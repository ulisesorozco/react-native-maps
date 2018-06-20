package com.airbnb.android.react.maps;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;


import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;



public class AirMapOverlay extends AirMapFeature implements ImageReadable {

  private GroundOverlayOptions groundOverlayOptions;
  private GroundOverlay groundOverlay;
  private LatLngBounds bounds;
  private BitmapDescriptor iconBitmapDescriptor;
  private Bitmap iconBitmap;
  private float zIndex;
  private float transparency;

  private Bitmap boundaryBitmap;
  private Context mContext;
  private ReadableArray points;
  private ReadableMap maxIdx;

  private final ImageReader mImageReader;
  private GoogleMap map;

  private static final String POINTS = "points";
  private static final String POINTS_ARRAY = "pointsArray";
  private static final String POINT_X = "x";
  private static final String POINT_Y = "y";
  private static final String MAX_IDX = "maxIdx";

  public AirMapOverlay(Context context) {
    super(context);
    this.mImageReader = new ImageReader(context, getResources(), this);
    this.mContext = context;
  }

  public void setBounds(ReadableArray bounds) {
    LatLng sw = new LatLng(bounds.getArray(1).getDouble(0), bounds.getArray(0).getDouble(1));
    LatLng ne = new LatLng(bounds.getArray(0).getDouble(0), bounds.getArray(1).getDouble(1));
    this.bounds = new LatLngBounds(sw, ne);
    if (this.groundOverlay != null) {
      this.groundOverlay.setPositionFromBounds(this.bounds);
    }
  }

  public void setZIndex(float zIndex) {
    this.zIndex = zIndex;
    if (this.groundOverlay != null) {
      this.groundOverlay.setZIndex(zIndex);
    }
  }

  // public void setTransparency(float transparency) {
  //     this.transparency = transparency;
  //     if (groundOverlay != null) {
  //         groundOverlay.setTransparency(transparency);
  //     }
  // }

  public void setImage(String uri) {
    this.mImageReader.setImage(uri);
  }

  public void setPoints(ReadableArray points) {
    this.points = points;
  }

  public void setMaxIdx(ReadableMap maxIdx) {
    this.maxIdx = maxIdx;
    this.boundaryBitmap = makeBoundary(this.mContext);
  }


  public GroundOverlayOptions getGroundOverlayOptions() {
    if (this.groundOverlayOptions == null) {
      this.groundOverlayOptions = createGroundOverlayOptions();
    }
    return this.groundOverlayOptions;
  }

  private GroundOverlayOptions createGroundOverlayOptions() {
    if (this.groundOverlayOptions != null) {
      return this.groundOverlayOptions;
    }
    if (this.iconBitmapDescriptor != null) {
      GroundOverlayOptions options = new GroundOverlayOptions();
      options.image(iconBitmapDescriptor);
      options.positionFromBounds(bounds);
      options.zIndex(zIndex);
      return options;
    }
    if (this.boundaryBitmap != null) {
      GroundOverlayOptions options = new GroundOverlayOptions();
      BitmapDescriptor boundaryBitmapDescriptor = BitmapDescriptorFactory.fromBitmap(boundaryBitmap);
      options.image(boundaryBitmapDescriptor);
      options.positionFromBounds(bounds);
      options.zIndex(zIndex);
      return options;
    }
    return null;
  }

  @Nullable
  public Bitmap makeBoundary(Context context) {

    try {
      List<Point> points = new ArrayList<>();

      for (int i = 0; i < this.points.size(); i++) {
        ReadableMap point = this.points.getMap(i);
        points.add(new Point(point.getInt("x"), point.getInt("y")));
      }

      final int width = this.maxIdx.getInt("x") + 1;
      final int height = this.maxIdx.getInt("y") + 1;

      //===== this part fix OOME in case of big area ===
      //TODO improve for really big area
      float scale = 16;//getNumber(SCALE).floatValue();
      int mSize = width > height ? width : height;
      if (mSize > 120) {
        scale = scale / (mSize / 120);
      }


      //zoneScale *= IMAGE_DENSITY; // adjust for predefined density. the higher this number, the sharper the image.
      int shift = 27;

      Bitmap image = Bitmap.createBitmap((int)(width * scale + shift * 2), (int)(height * scale + shift * 2), Bitmap.Config.ARGB_8888);
      image.eraseColor(Color.argb(0, 0, 0, 0));   // pre-fill transparent
      Canvas canvas = new Canvas(image);

      List<Point> reflectedPoints = reflectZone(points, this.maxIdx.getInt("y"));

      //paint Yellow boarder
      Paint paint = new Paint();
      paint.setColor(Color.parseColor("#FFDC00"));
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(60);
      Path path = new Path();
      path.moveTo(reflectedPoints.get(0).x * scale + shift, reflectedPoints.get(0).y * scale + shift);
      for (int i = 1; i < points.size(); i++) {
        path.lineTo(reflectedPoints.get(i).x * scale + shift, reflectedPoints.get(i).y * scale + shift);
      }
      path.close();
      canvas.drawPath(path, paint);

      //paint Green zone
      paint.reset();
      paint.setColor(Color.parseColor("#802ECC40"));
      paint.setStyle(Paint.Style.FILL);
      paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
      paint.setStrokeWidth(1);
      path.reset(); // only needed when reusing this path for a new build
      path.moveTo(reflectedPoints.get(0).x * scale + shift, reflectedPoints.get(0).y * scale + shift);
      for (int i = 1; i < points.size(); i++) {
        path.lineTo(reflectedPoints.get(i).x * scale + shift, reflectedPoints.get(i).y * scale + shift);
      }
      path.close();
      canvas.drawPath(path, paint);

      //pain Blue boarder
      paint.reset();
      paint.setColor(Color.parseColor("#0074D9"));
      paint.setStyle(Paint.Style.STROKE);
      paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
      paint.setStrokeWidth(20);
      path.reset(); // only needed when reusing this path for a new build
      path.moveTo(reflectedPoints.get(0).x * scale + shift, reflectedPoints.get(0).y * scale + shift);
      for (int i = 1; i < points.size(); i++) {
        path.lineTo(reflectedPoints.get(i).x * scale + shift, reflectedPoints.get(i).y * scale + shift);
      }
      path.close();
      canvas.drawPath(path, paint);

      int bc = image.getByteCount();

      return image;

    } catch (NullPointerException e) {
      return null;
    }
  }

  @Override
  public Object getFeature() {
    return groundOverlay;
  }

  @Override
  public void addToMap(GoogleMap map) {
    GroundOverlayOptions groundOverlayOptions = getGroundOverlayOptions();
    if (groundOverlayOptions != null) {
      this.groundOverlay = map.addGroundOverlay(groundOverlayOptions);
      this.groundOverlay.setClickable(true);
    } else {
      this.map = map;
    }
  }

  @Override
  public void removeFromMap(GoogleMap map) {
    this.map = null;
    if (this.groundOverlay != null) {
      this.groundOverlay.remove();
      this.groundOverlay = null;
      this.groundOverlayOptions = null;
    }
  }

  @Override
  public void setIconBitmap(Bitmap bitmap) {
    this.iconBitmap = bitmap;
  }

  @Override
  public void setIconBitmapDescriptor(
      BitmapDescriptor iconBitmapDescriptor) {
    this.iconBitmapDescriptor = iconBitmapDescriptor;
  }

  @Override
  public void update() {
    this.groundOverlay = getGroundOverlay();
    if (this.groundOverlay != null) {
      this.groundOverlay.setImage(this.iconBitmapDescriptor);
      this.groundOverlay.setClickable(true);
    }
  }

  private GroundOverlay getGroundOverlay() {
    if (this.groundOverlay != null) {
      return this.groundOverlay;
    }
    if (this.map == null) {
      return null;
    }
    GroundOverlayOptions groundOverlayOptions = getGroundOverlayOptions();
    if (groundOverlayOptions != null) {
      return this.map.addGroundOverlay(groundOverlayOptions);
    }
    return null;
  }

  private List<Point> reflectZone(List<Point> points, int maxIdxY) {
    List<Point> reflectedPoints = new ArrayList<>();
    for (Point p : points) {
      reflectedPoints.add(new Point(p.x, -(p.y - maxIdxY)));
    }
    return reflectedPoints;
  }

  private int prepareInt(Number n) {
    if (n instanceof Double) {
      return Double.valueOf(n.doubleValue() + 0.00001).intValue();
    }
    return n.intValue();
  }
}
