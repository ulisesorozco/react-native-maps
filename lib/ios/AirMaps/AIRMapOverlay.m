#import "AIRMapOverlay.h"

#import <React/RCTBridge.h>
#import <React/RCTEventDispatcher.h>
#import <React/RCTImageLoader.h>
#import <React/RCTUtils.h>
#import <React/UIView+React.h>

#define SSGREEN @"#2ECC40"
#define SSBLUE @"#0074D9"
#define SSYELLOW @"#FFDC00"

@interface AIRMapOverlay()
@property (nonatomic, strong, readwrite) UIImage *overlayImage;
@end

@implementation AIRMapOverlay {
    RCTImageLoaderCancellationBlock _reloadImageCancellationBlock;
    CLLocationCoordinate2D _southWest;
    CLLocationCoordinate2D _northEast;
    MKMapRect _mapRect;
}

- (void)setImageSrc:(NSString *)imageSrc
{
    NSLog(@">>> SET IMAGESRC: %@", imageSrc);
    _imageSrc = imageSrc;

    if (_reloadImageCancellationBlock) {
        _reloadImageCancellationBlock();
        _reloadImageCancellationBlock = nil;
    }
    __weak typeof(self) weakSelf = self;
    _reloadImageCancellationBlock = [_bridge.imageLoader loadImageWithURLRequest:[RCTConvert NSURLRequest:_imageSrc]
                                                                            size:weakSelf.bounds.size
                                                                           scale:RCTScreenScale()
                                                                         clipped:YES
                                                                      resizeMode:RCTResizeModeCenter
                                                                   progressBlock:nil
                                                                partialLoadBlock:nil
                                                                 completionBlock:^(NSError *error, UIImage *image) {
                                                                     if (error) {
                                                                         NSLog(@"%@", error);
                                                                     }
                                                                     dispatch_async(dispatch_get_main_queue(), ^{
                                                                         NSLog(@">>> IMAGE: %@", image);
                                                                         weakSelf.overlayImage = image;
                                                                         [weakSelf createOverlayRendererIfPossible];
                                                                         [weakSelf update];
                                                                     });
                                                                 }];
}

- (void)setBoundsRect:(NSArray *)boundsRect {
    _boundsRect = boundsRect;
    NSLog(@">>> $$$ IMAGE: %@", boundsRect);
    _southWest = CLLocationCoordinate2DMake([boundsRect[1][0] doubleValue], [boundsRect[0][1] doubleValue]);
    _northEast = CLLocationCoordinate2DMake([boundsRect[0][0] doubleValue], [boundsRect[1][1] doubleValue]);

    MKMapPoint southWest = MKMapPointForCoordinate(_southWest);
    MKMapPoint northEast = MKMapPointForCoordinate(_northEast);

    _mapRect = MKMapRectMake(southWest.x, northEast.y, northEast.x - southWest.x, northEast.y - southWest.y);

    [self update];
}

- (void)setPointsSrc:(NSArray *)pointsSrc {
    _pointsSrc = pointsSrc;
    [self drawBoundary:pointsSrc transparent:false];
}

- (void)drawBoundary:(NSArray *)area transparent:(BOOL)transparent
{
    float maxSavedImageHeight = 300.0;
    if (area.count < 3) return;
    
    int pMinY = 100000;
    int pMaxY = 0;
    
    int pMinX = 100000;
    int pMaxX = 0;
    
    for (NSDictionary* point in area) {
        
        int coordX = [[point valueForKey:@"x"] intValue];
        int coordY = [[point valueForKey:@"y"] intValue];
        
        
        pMinY = MIN(pMinY, coordY);
        pMaxY = MAX(pMaxY, coordY);
        
        pMinX = MIN(pMinX, coordX);
        pMaxX = MAX(pMaxX, coordX);
    }
    
    double pointsHeight = ABS(pMaxY - pMinY);
    double pointsWidth  = ABS(pMaxX - pMinX);
    
    double bmpScale = maxSavedImageHeight/(double)pointsHeight;
    double cHeight = maxSavedImageHeight;
    double cWidth = (double)pointsWidth * bmpScale;
    
    // pointsHeight - count of pixel cells in height
    float borderWidth = maxSavedImageHeight/pointsHeight;
    
    NSMutableArray* points = [[NSMutableArray alloc] init];
    double marginOffset = (double)borderWidth * 2;
    
    for (NSInteger i = 0; i < area.count; i++) {
        NSDictionary* point = area[i];
        int x = [[point valueForKey:@"x"] doubleValue];
        int y = [[point valueForKey:@"y"] doubleValue];
        
        CGPoint newPoint = CGPointMake((x * bmpScale) + marginOffset, cHeight - (y * bmpScale) + marginOffset);
        [points addObject:[NSValue valueWithCGPoint:newPoint]];
    }
    
    CGSize size = CGSizeMake((CGFloat)cWidth + borderWidth * 4, (CGFloat)cHeight + borderWidth * 4);
    Boolean opaque = false;
    CGFloat scale = 1;
    
    UIGraphicsBeginImageContextWithOptions(size, opaque, scale);
    
    CGContextRef context =  UIGraphicsGetCurrentContext();
    CGContextSetBlendMode(context, kCGBlendModeCopy);
    
    UIColor* fillColor;
    UIColor* outColor;
    UIColor* innerColor;
    if (transparent) {
        fillColor = [UIColor clearColor];
        outColor = [UIColor clearColor];
        innerColor = [UIColor clearColor];
    } else {
        fillColor = [self getColorFromHex:SSGREEN withAlpha:0.5f];
        outColor = [self getColorFromHex:SSYELLOW withAlpha:1.0f];
        innerColor = [self getColorFromHex:SSBLUE withAlpha:1.0f];
    }
    
    CGContextSetFillColorWithColor(context, fillColor.CGColor);
    CGContextSetShouldAntialias(context, true);
    CGContextSetAllowsAntialiasing(context, true);
    
    CGContextSetStrokeColorWithColor(context, outColor.CGColor);
    
    CGContextSetLineWidth(context, borderWidth * 3);
    
    CGMutablePathRef path = CGPathCreateMutable();
    
    CGContextBeginPath(context);
    
    CGPathMoveToPoint(path, nil, [points.firstObject CGPointValue].x, [points.firstObject CGPointValue].y);
    
    for (NSInteger i = 1; i < points.count; i++) {
        CGPoint newPoint = [[points objectAtIndex:i] CGPointValue];
        CGPathAddLineToPoint(path, nil, newPoint.x, newPoint.y);
    }
    
    CGPathCloseSubpath(path);
    CGContextAddPath(context, path);
    CGContextStrokePath(context);
    
    CGContextAddPath(context, path);
    CGContextFillPath(context);
    
    CGContextSetStrokeColorWithColor(context, innerColor.CGColor);
    CGContextSetLineWidth(context, borderWidth);
    CGContextAddPath(context, path);
    CGContextStrokePath(context);
    
    UIImage* image = UIGraphicsGetImageFromCurrentImageContext();
    CGContextFlush(context);
    UIGraphicsEndImageContext();
    
    __weak typeof(self) weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
        NSLog(@">>> IMAGE Loaded");
        weakSelf.overlayImage = image;
        [weakSelf createOverlayRendererIfPossible];
        [weakSelf update];
    });
}

-(UIColor*) getColorFromHex:(NSString*)hexString withAlpha:(float)alpha {
    NSString *cString = [hexString uppercaseString];
    
    if ([cString hasPrefix:@"#"]) {
        cString = [cString substringFromIndex:1];
    }
    
    if (cString.length != 6) {
        return [UIColor clearColor];
    }
    
    float red = [self convertHexToInt:[cString substringWithRange:NSMakeRange(0, 2)]] / 255.0f;
    float green = [self convertHexToInt:[cString substringWithRange:NSMakeRange(2, 2)]] / 255.0f;
    float blue = [self convertHexToInt:[cString substringWithRange:NSMakeRange(4, 2)]] / 255.0f;
    
    return [UIColor colorWithRed:red green:green blue:blue alpha:alpha];
}

-(int) convertHexToInt:(NSString*)string {
    NSScanner *scanner = [[NSScanner alloc] initWithString:string];
    
    unsigned int retval;
    if (![scanner scanHexInt:&retval]) {
        NSLog(@"Invalid hex string");
    }
    return retval;
}

- (void)createOverlayRendererIfPossible
{
    if (MKMapRectIsEmpty(_mapRect) || !self.overlayImage) return;
    __weak typeof(self) weakSelf = self;
    self.renderer = [[AIRMapOverlayRenderer alloc] initWithOverlay:weakSelf];
}

- (void)update
{
    if (!_renderer) return;

    if (_map == nil) return;
    [_map removeOverlay:self];
    [_map addOverlay:self];
}

#pragma mark MKOverlay implementation

- (CLLocationCoordinate2D)coordinate
{
    return MKCoordinateForMapPoint(MKMapPointMake(MKMapRectGetMidX(_mapRect), MKMapRectGetMidY(_mapRect)));
}

- (MKMapRect)boundingMapRect
{
    return _mapRect;
}

- (BOOL)intersectsMapRect:(MKMapRect)mapRect
{
    return MKMapRectIntersectsRect(_mapRect, mapRect);
}

- (BOOL)canReplaceMapContent
{
    return NO;
}

@end
