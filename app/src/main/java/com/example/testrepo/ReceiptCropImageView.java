package com.example.testrepo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class ReceiptCropImageView extends View {
    private static final float DEFAULT_CROP_INSET_RATIO = 0.08f;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint overlayPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageBounds = new RectF();
    private final RectF cropRect = new RectF();

    private final float minCropSizePx;
    private final float handleRadiusPx;
    private final float touchTargetPx;

    @Nullable
    private Bitmap bitmap;
    private DragMode dragMode = DragMode.NONE;
    private float lastTouchX;
    private float lastTouchY;

    public ReceiptCropImageView(Context context) {
        this(context, null);
    }

    public ReceiptCropImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ReceiptCropImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        overlayPaint.setColor(0x99000000);

        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(2f));

        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);

        minCropSizePx = dpToPx(96f);
        handleRadiusPx = dpToPx(10f);
        touchTargetPx = dpToPx(28f);
    }

    public void setImageBitmap(@Nullable Bitmap bitmap) {
        this.bitmap = bitmap;
        updateImageBounds();
        invalidate();
    }

    @Nullable
    public Bitmap getCroppedBitmap() {
        if (bitmap == null || imageBounds.isEmpty() || cropRect.isEmpty()) {
            return null;
        }

        int left = clampToBitmap(Math.round((cropRect.left - imageBounds.left)
                * bitmap.getWidth() / imageBounds.width()), bitmap.getWidth() - 1);
        int top = clampToBitmap(Math.round((cropRect.top - imageBounds.top)
                * bitmap.getHeight() / imageBounds.height()), bitmap.getHeight() - 1);
        int right = clampToBitmap(Math.round((cropRect.right - imageBounds.left)
                * bitmap.getWidth() / imageBounds.width()), bitmap.getWidth());
        int bottom = clampToBitmap(Math.round((cropRect.bottom - imageBounds.top)
                * bitmap.getHeight() / imageBounds.height()), bitmap.getHeight());

        int width = Math.max(1, right - left);
        int height = Math.max(1, bottom - top);

        try {
            return Bitmap.createBitmap(bitmap, left, top, width, height);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateImageBounds();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(Color.BLACK);
        if (bitmap == null || imageBounds.isEmpty()) {
            return;
        }

        canvas.drawBitmap(bitmap, null, imageBounds, bitmapPaint);
        drawOverlay(canvas);
        drawHandles(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null || imageBounds.isEmpty() || cropRect.isEmpty()) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragMode = resolveDragMode(x, y);
                if (dragMode == DragMode.NONE) {
                    return false;
                }
                lastTouchX = x;
                lastTouchY = y;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragMode == DragMode.NONE) {
                    return false;
                }
                updateCropRect(x - lastTouchX, y - lastTouchY);
                lastTouchX = x;
                lastTouchY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragMode = DragMode.NONE;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateImageBounds() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
            imageBounds.setEmpty();
            cropRect.setEmpty();
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();
        float scale = Math.min(viewWidth / bitmapWidth, viewHeight / bitmapHeight);

        float drawnWidth = bitmapWidth * scale;
        float drawnHeight = bitmapHeight * scale;
        float left = (viewWidth - drawnWidth) / 2f;
        float top = (viewHeight - drawnHeight) / 2f;

        imageBounds.set(left, top, left + drawnWidth, top + drawnHeight);
        resetCropRect();
    }

    private void resetCropRect() {
        if (imageBounds.isEmpty()) {
            cropRect.setEmpty();
            return;
        }

        float horizontalInset = Math.max(handleRadiusPx, imageBounds.width() * DEFAULT_CROP_INSET_RATIO);
        float verticalInset = Math.max(handleRadiusPx, imageBounds.height() * DEFAULT_CROP_INSET_RATIO);

        cropRect.set(
                imageBounds.left + horizontalInset,
                imageBounds.top + verticalInset,
                imageBounds.right - horizontalInset,
                imageBounds.bottom - verticalInset
        );
    }

    private void drawOverlay(Canvas canvas) {
        canvas.drawRect(imageBounds.left, imageBounds.top, imageBounds.right, cropRect.top, overlayPaint);
        canvas.drawRect(imageBounds.left, cropRect.bottom, imageBounds.right, imageBounds.bottom, overlayPaint);
        canvas.drawRect(imageBounds.left, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
        canvas.drawRect(cropRect.right, cropRect.top, imageBounds.right, cropRect.bottom, overlayPaint);
        canvas.drawRect(cropRect, borderPaint);
    }

    private void drawHandles(Canvas canvas) {
        canvas.drawCircle(cropRect.left, cropRect.top, handleRadiusPx, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.top, handleRadiusPx, handlePaint);
        canvas.drawCircle(cropRect.left, cropRect.bottom, handleRadiusPx, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.bottom, handleRadiusPx, handlePaint);
    }

    private DragMode resolveDragMode(float x, float y) {
        boolean nearLeft = Math.abs(x - cropRect.left) <= touchTargetPx;
        boolean nearRight = Math.abs(x - cropRect.right) <= touchTargetPx;
        boolean nearTop = Math.abs(y - cropRect.top) <= touchTargetPx;
        boolean nearBottom = Math.abs(y - cropRect.bottom) <= touchTargetPx;
        boolean withinVertical = y >= cropRect.top - touchTargetPx && y <= cropRect.bottom + touchTargetPx;
        boolean withinHorizontal = x >= cropRect.left - touchTargetPx && x <= cropRect.right + touchTargetPx;

        if (nearLeft && nearTop) {
            return DragMode.TOP_LEFT;
        }
        if (nearRight && nearTop) {
            return DragMode.TOP_RIGHT;
        }
        if (nearLeft && nearBottom) {
            return DragMode.BOTTOM_LEFT;
        }
        if (nearRight && nearBottom) {
            return DragMode.BOTTOM_RIGHT;
        }
        if (nearLeft && withinVertical) {
            return DragMode.LEFT;
        }
        if (nearRight && withinVertical) {
            return DragMode.RIGHT;
        }
        if (nearTop && withinHorizontal) {
            return DragMode.TOP;
        }
        if (nearBottom && withinHorizontal) {
            return DragMode.BOTTOM;
        }
        if (cropRect.contains(x, y)) {
            return DragMode.MOVE;
        }
        return DragMode.NONE;
    }

    private void updateCropRect(float dx, float dy) {
        switch (dragMode) {
            case MOVE:
                moveCropRect(dx, dy);
                break;
            case LEFT:
                cropRect.left = clamp(cropRect.left + dx, imageBounds.left, cropRect.right - minCropSizePx);
                break;
            case RIGHT:
                cropRect.right = clamp(cropRect.right + dx, cropRect.left + minCropSizePx, imageBounds.right);
                break;
            case TOP:
                cropRect.top = clamp(cropRect.top + dy, imageBounds.top, cropRect.bottom - minCropSizePx);
                break;
            case BOTTOM:
                cropRect.bottom = clamp(cropRect.bottom + dy, cropRect.top + minCropSizePx, imageBounds.bottom);
                break;
            case TOP_LEFT:
                cropRect.left = clamp(cropRect.left + dx, imageBounds.left, cropRect.right - minCropSizePx);
                cropRect.top = clamp(cropRect.top + dy, imageBounds.top, cropRect.bottom - minCropSizePx);
                break;
            case TOP_RIGHT:
                cropRect.right = clamp(cropRect.right + dx, cropRect.left + minCropSizePx, imageBounds.right);
                cropRect.top = clamp(cropRect.top + dy, imageBounds.top, cropRect.bottom - minCropSizePx);
                break;
            case BOTTOM_LEFT:
                cropRect.left = clamp(cropRect.left + dx, imageBounds.left, cropRect.right - minCropSizePx);
                cropRect.bottom = clamp(cropRect.bottom + dy, cropRect.top + minCropSizePx, imageBounds.bottom);
                break;
            case BOTTOM_RIGHT:
                cropRect.right = clamp(cropRect.right + dx, cropRect.left + minCropSizePx, imageBounds.right);
                cropRect.bottom = clamp(cropRect.bottom + dy, cropRect.top + minCropSizePx, imageBounds.bottom);
                break;
            case NONE:
                break;
        }
    }

    private void moveCropRect(float dx, float dy) {
        float adjustedDx = dx;
        float adjustedDy = dy;

        if (cropRect.left + adjustedDx < imageBounds.left) {
            adjustedDx = imageBounds.left - cropRect.left;
        }
        if (cropRect.right + adjustedDx > imageBounds.right) {
            adjustedDx = imageBounds.right - cropRect.right;
        }
        if (cropRect.top + adjustedDy < imageBounds.top) {
            adjustedDy = imageBounds.top - cropRect.top;
        }
        if (cropRect.bottom + adjustedDy > imageBounds.bottom) {
            adjustedDy = imageBounds.bottom - cropRect.bottom;
        }

        cropRect.offset(adjustedDx, adjustedDy);
    }

    private int clampToBitmap(int value, int maxValue) {
        return Math.max(0, Math.min(value, maxValue));
    }

    private float clamp(float value, float minValue, float maxValue) {
        return Math.max(minValue, Math.min(value, maxValue));
    }

    private float dpToPx(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private enum DragMode {
        NONE,
        MOVE,
        LEFT,
        TOP,
        RIGHT,
        BOTTOM,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }
}
