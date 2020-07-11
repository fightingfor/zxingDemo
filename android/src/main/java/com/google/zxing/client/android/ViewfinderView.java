/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android;

import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.QrCameraManager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

  private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
  private static final long ANIMATION_DELAY = 80L;
  private static final int CURRENT_POINT_OPACITY = 0xA0;
  private static final int MAX_RESULT_POINTS = 20;
  private static final int POINT_SIZE = 6;

  private QrCameraManager cameraManager;
  private final Paint paint;
  private Bitmap resultBitmap;
  private final int maskColor;
  private final int resultColor;
  private final int laserColor;
  private final int resultPointColor;
  private int scannerAlpha;
  private List<ResultPoint> possibleResultPoints;
  private List<ResultPoint> lastPossibleResultPoints;
  /**
   * 扫码框四角颜色
   */
  private int cornerColor;
  /**
   * 扫描区边角的宽
   */
  private int cornerRectWidth;
  /**
   * 扫描区边角的高
   */
  private int cornerRectHeight;

  // This constructor is used when the class is built from an XML resource.
  public ViewfinderView(Context context, AttributeSet attrs) {
    super(context, attrs);

    // Initialize these once for performance rather than calling them every time in onDraw().
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Resources resources = getResources();
    maskColor = resources.getColor(R.color.viewfinder_mask);
    resultColor = resources.getColor(R.color.result_view);
    laserColor = resources.getColor(R.color.viewfinder_laser);
    resultPointColor = resources.getColor(R.color.possible_result_points);
    scannerAlpha = 0;
    possibleResultPoints = new ArrayList<>(5);
    lastPossibleResultPoints = null;

    cornerRectWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,16,getResources().getDisplayMetrics());
    cornerRectHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,16,getResources().getDisplayMetrics());
    cornerColor =  resources.getColor(R.color.possible_result_points);
  }

  public void setQrCameraManager(QrCameraManager cameraManager) {
    this.cameraManager = cameraManager;
  }

  @SuppressLint("DrawAllocation")
  @Override
  public void onDraw(Canvas canvas) {
    if (cameraManager == null) {
      return; // not ready yet, early draw before done configuring
    }
    Rect frame = cameraManager.getFramingRect();
    Rect previewFrame = cameraManager.getFramingRectInPreview();    
    if (frame == null || previewFrame == null) {
      return;
    }
    //绘制覆盖颜色
    drawCover(canvas,frame);
    // 绘制边角
    drawCorner(canvas, frame);

    if (resultBitmap != null) {
      // Draw the opaque result bitmap over the scanning rectangle
      paint.setAlpha(CURRENT_POINT_OPACITY);
      canvas.drawBitmap(resultBitmap, null, frame, paint);
    } else {

      // Draw a red "laser scanner" line through the middle to show decoding is active
      paint.setColor(laserColor);
      paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
      scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;
      int middle = frame.height() / 2 + frame.top;
      canvas.drawRect(frame.left + 2, middle - 1, frame.right - 1, middle + 2, paint);

      // Request another update at the animation interval, but only repaint the laser line,
      // not the entire viewfinder mask.
      postInvalidateDelayed(ANIMATION_DELAY,
                            frame.left - POINT_SIZE,
                            frame.top - POINT_SIZE,
                            frame.right + POINT_SIZE,
                            frame.bottom + POINT_SIZE);
    }
  }

  /**
   * 绘制四个角
   * @param canvas
   * @param frame
   */
  private void drawCorner(Canvas canvas, Rect frame) {
    paint.setColor(cornerColor);
    int diffWidth = cornerRectWidth / 2;
    //左上
    canvas.drawRect(frame.left - diffWidth, frame.top -diffWidth, frame.left + diffWidth, frame.top + cornerRectHeight -diffWidth, paint);
    canvas.drawRect(frame.left - diffWidth, frame.top -diffWidth, frame.left + cornerRectHeight -diffWidth, frame.top + diffWidth, paint);
    //右上
    canvas.drawRect(frame.right - diffWidth, frame.top -diffWidth , frame.right + diffWidth, frame.top + cornerRectHeight -diffWidth, paint);
    canvas.drawRect(frame.right - cornerRectHeight - diffWidth, frame.top -diffWidth  , frame.right + diffWidth , frame.top  +diffWidth, paint);
    //左下
    canvas.drawRect(frame.left -diffWidth, frame.bottom - diffWidth, frame.left + cornerRectHeight -diffWidth, frame.bottom +diffWidth, paint);
    canvas.drawRect(frame.left -diffWidth, frame.bottom - cornerRectHeight +diffWidth, frame.left + diffWidth, frame.bottom +diffWidth, paint);
    //右下
    canvas.drawRect(frame.right - diffWidth, frame.bottom - cornerRectHeight +diffWidth, frame.right +diffWidth, frame.bottom +diffWidth, paint);
    canvas.drawRect(frame.right - cornerRectHeight +diffWidth, frame.bottom - diffWidth, frame.right +diffWidth, frame.bottom + diffWidth, paint);
  }

  /**
   * 绘制遮掩层
   */
  private void drawCover(Canvas canvas, Rect frame) {

    // 获取屏幕的宽和高
    int width = canvas.getWidth();
    int height = canvas.getHeight();

    // Draw the exterior (i.e. outside the framing rect) darkened
    paint.setColor(resultBitmap != null ? resultColor : maskColor);

    // 画出扫描框外面的阴影部分，共四个部分，扫描框的上面到屏幕上面，扫描框的下面到屏幕下面
    // 扫描框的左边面到屏幕左边，扫描框的右边到屏幕右边
    canvas.drawRect(0, 0, width, frame.top, paint);
    canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
    canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1,
            paint);
    canvas.drawRect(0, frame.bottom + 1, width, height, paint);
  }

  public void drawViewfinder() {
    Bitmap resultBitmap = this.resultBitmap;
    this.resultBitmap = null;
    if (resultBitmap != null) {
      resultBitmap.recycle();
    }
    invalidate();
  }

  public void addPossibleResultPoint(ResultPoint point) {
    List<ResultPoint> points = possibleResultPoints;
    synchronized (points) {
      points.add(point);
      int size = points.size();
      if (size > MAX_RESULT_POINTS) {
        // trim it
        points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
      }
    }
  }

}
