package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.View;

public class OverlayView extends View {
    private Bitmap overlayBitmap;
    private Bitmap transformedBitmap;
    private float rotationAngle = 0;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOverlayBitmap(Bitmap bitmap) {
        if (this.overlayBitmap != null) {
            this.overlayBitmap.recycle();
        }
        this.overlayBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        transformBitmap();
        invalidate();
    }

    public void setRotationAngle(float angle) {
        this.rotationAngle = angle;
        transformBitmap();
        invalidate();
    }

    private void transformBitmap() {
        if (overlayBitmap != null && getWidth() > 0 && getHeight() > 0) {
            float scaleWidth = ((float) getWidth()) / overlayBitmap.getWidth();
            float scaleHeight = ((float) getHeight()) / overlayBitmap.getHeight();
            float scale = Math.min(scaleWidth, scaleHeight);

            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            matrix.postRotate(rotationAngle, overlayBitmap.getWidth() / 2f, overlayBitmap.getHeight() / 2f);
            matrix.postTranslate((getWidth() - overlayBitmap.getWidth() * scale) / 2, (getHeight() - overlayBitmap.getHeight() * scale) / 2);

            transformedBitmap = Bitmap.createBitmap(overlayBitmap, 0, 0, overlayBitmap.getWidth(), overlayBitmap.getHeight(), matrix, false);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (transformedBitmap != null) {
            canvas.drawBitmap(transformedBitmap, 0, 0, null);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        transformBitmap();
    }
}
