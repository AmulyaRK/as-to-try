package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.ImageProxy.PlaneProxy;
import androidx.camera.view.PreviewView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

public class ForegroundExtractor {
    private static final String TAG = "ForegroundExtractor";
    private final Context context;
    private final PreviewView previewView;
    private final OverlayView overlayView;

    public ForegroundExtractor(Context context, PreviewView previewView, OverlayView overlayView) {
        this.context = context;
        this.previewView = previewView;
        this.overlayView = overlayView;
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public void processImageProxy(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        Bitmap bitmap = yuvToRgb(imageProxy);
        if (bitmap == null) {
            Log.e(TAG, "Failed to convert ImageProxy to Bitmap");
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(
                Objects.requireNonNull(imageProxy.getImage()),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        segmentImage(inputImage, bitmap, imageProxy);
    }

    private void segmentImage(InputImage image, Bitmap bitmap, ImageProxy imageProxy) {
        SelfieSegmenterOptions options = new SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
                .enableRawSizeMask()
                .build();

        Segmentation.getClient(options).process(image)
                .addOnSuccessListener(mask -> {
                    extractForeground(mask, bitmap);
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Segmentation failed: " + e.getMessage(), e);
                    imageProxy.close();
                });
    }

    private void extractForeground(SegmentationMask mask, Bitmap cameraBitmap) {
        if (cameraBitmap == null) {
            Log.e(TAG, "Bitmap is null. Cannot process segmentation.");
            return;
        }

        int currentRotation = previewView.getDisplay().getRotation();
        Bitmap rotatedCameraBitmap = rotateBitmap(cameraBitmap, currentRotation);
        Bitmap resizedCameraBitmap = Bitmap.createScaledBitmap(rotatedCameraBitmap, mask.getWidth(), mask.getHeight(), true);

        Bitmap resultBitmap = Bitmap.createBitmap(mask.getWidth(), mask.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(resultBitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ByteBuffer buffer = mask.getBuffer();
        buffer.rewind();
        int width = mask.getWidth();
        int height = mask.getHeight();
        int[] pixels = new int[width * height];
        resizedCameraBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < width * height; i++) {
            float confidence = buffer.getFloat();
            int x = i % width;
            int y = i / width;
            if (confidence >= 0.5f) { // Foreground pixel
                resultBitmap.setPixel(i % width, i / width, pixels[i]);
            } else {
                resultBitmap.setPixel(x, y, Color.TRANSPARENT); // Transparent background
            }
        }

        overlayView.setOverlayBitmap(resultBitmap);
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }


    private Bitmap yuvToRgb(ImageProxy imageProxy) {
        // Get the YUV planes from the ImageProxy
        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();

// Extract Y, U, V buffer planes
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

// Allocate an NV21 byte array
        byte[] nv21 = new byte[ySize + uSize + vSize];

// Copy Y, U, V planes to the NV21 array
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

// Create a YuvImage object from the NV21 byte array
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, imageProxy.getWidth(), imageProxy.getHeight(), null);

// Set up the ByteArrayOutputStream to convert the YuvImage to JPEG
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()), 100, out);

        byte[] imageBytes = out.toByteArray();

// Decode the JPEG byte array to create a Bitmap
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

// Check if the bitmap was generated correctly
        if (bitmap == null) {
            Log.e(TAG, "Failed to decode YUV image to RGB bitmap");
        }

        return bitmap;
    }
}
