package com.wonrui.zxinglite.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.View;

import com.wonrui.zxinglite.R;
import com.wonrui.zxinglite.camera.CameraManager;
import com.google.zxing.ResultPoint;

import java.util.ArrayList;
import java.util.List;


/**
 * 该视图是覆盖在相机的预览视图之上的一层视图。扫描区构成原理，其实是在预览视图上画四块遮罩层，
 * 中间留下的部分保持透明，并画上一条激光线，实际上该线条就是展示而已，与扫描功能没有任何关系。
 */
public class ViewfinderView extends View {
    private static final int MAX_RESULT_POINTS = 20;
    private static final int CURRENT_POINT_OPACITY = 0xA0;
    /**
     * 刷新界面的时间
     */
    private static final long ANIMATION_DELAY = 10L;
    private static final int OPAQUE = 0xFF;
    private static final int POINT_SIZE = 6;
    private CameraManager cameraManager;
    /**
     * 画笔对象的引用
     */
    private final Paint paint;
    /**
     * 第一次绘制控件
     */
    boolean isFirst = true;
    /**
     * 中间那条线每次刷新移动的距离
     */
    private static final int SPEEN_DISTANCE = 10;
    /**
     * 中间滑动线的最顶端位置
     */
    private int slideTop;

    /**
     * 中间滑动线的最底端位置
     */
    private int slideBottom;
    /**
     * 扫描框中的中间线的宽度
     */
    private static int MIDDLE_LINE_WIDTH;
    /**
     * 遮掩层的颜色
     */
    private final int maskColor;
    private final int resultColor;
    private final int resultPointColor;
    private Bitmap resultBitmap;
    private List<ResultPoint> possibleResultPoints;
    private List<ResultPoint> lastPossibleResultPoints;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG); // 开启反锯齿
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask); // 遮掩层颜色
        resultColor = resources.getColor(R.color.result_view);
        resultPointColor = resources.getColor(R.color.possible_result_points);
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = null;
        MIDDLE_LINE_WIDTH = dip2px(context, 3.0F);
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        // super.onDraw(canvas);
        if (cameraManager == null) {
            return; // not ready yet, early draw before done configuring
        }
        Rect frame = cameraManager.getFramingRect();
        Rect previewFrame = cameraManager.getFramingRectInPreview();
        if (frame == null || previewFrame == null) {
            return;
        }

        // 绘制遮掩层
        drawCover(canvas, frame);


        if (resultBitmap != null) { // 绘制扫描结果的图
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, frame, paint);
        } else {
            // 画扫描框边上的角
            drawRectEdges(canvas, frame);

            // 绘制扫描线
            drawScanningLine(canvas, frame);

            float scaleX = frame.width() / (float) previewFrame.width();
            float scaleY = frame.height() / (float) previewFrame.height();

            List<ResultPoint> currentPossible = possibleResultPoints;
            List<ResultPoint> currentLast = lastPossibleResultPoints;
            int frameLeft = frame.left;
            int frameTop = frame.top;
            if (currentPossible.isEmpty()) {
                lastPossibleResultPoints = null;
            } else {
                possibleResultPoints = new ArrayList<>(5);
                lastPossibleResultPoints = currentPossible;
                paint.setAlpha(CURRENT_POINT_OPACITY);
                paint.setColor(resultPointColor);
                synchronized (currentPossible) {
                    for (ResultPoint point : currentPossible) {
                        canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                                frameTop + (int) (point.getY() * scaleY),
                                POINT_SIZE, paint);
                    }
                }
            }
            if (currentLast != null) {
                paint.setAlpha(CURRENT_POINT_OPACITY / 2);
                paint.setColor(resultPointColor);
                synchronized (currentLast) {
                    float radius = POINT_SIZE / 2.0f;
                    for (ResultPoint point : currentLast) {
                        canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                                frameTop + (int) (point.getY() * scaleY),
                                radius, paint);
                    }
                }
            }

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
     * 绘制遮掩层
     *
     * @param canvas
     * @param frame
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
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);
    }

    /**
     * 描绘方形的四个角
     *
     * @param canvas
     * @param frame
     */
    private void drawRectEdges(Canvas canvas, Rect frame) {

        paint.setColor(Color.WHITE);
        paint.setAlpha(OPAQUE);

        Resources resources = getResources();
        /**
         * 这些资源可以用缓存进行管理，不需要每次刷新都新建
         */
        Bitmap bitmapCornerTopleft = BitmapFactory.decodeResource(resources,
                R.drawable.scan_corner_top_left);
        Bitmap bitmapCornerTopright = BitmapFactory.decodeResource(resources,
                R.drawable.scan_corner_top_right);
        Bitmap bitmapCornerBottomLeft = BitmapFactory.decodeResource(resources,
                R.drawable.scan_corner_bottom_left);
        Bitmap bitmapCornerBottomRight = BitmapFactory.decodeResource(
                resources, R.drawable.scan_corner_bottom_right);

        canvas.drawBitmap(bitmapCornerTopleft, frame.left, frame.top, paint);
        canvas.drawBitmap(bitmapCornerTopright, frame.right - bitmapCornerTopright.getWidth(),
                frame.top, paint);
        canvas.drawBitmap(bitmapCornerBottomLeft, frame.left,
                2 + (frame.bottom - bitmapCornerBottomLeft.getHeight()), paint);
        canvas.drawBitmap(bitmapCornerBottomRight, frame.right - bitmapCornerBottomRight.getWidth(),
                2 + (frame.bottom - bitmapCornerBottomRight.getHeight()), paint);

        bitmapCornerTopleft.recycle();
        bitmapCornerTopright.recycle();
        bitmapCornerBottomLeft.recycle();
        bitmapCornerBottomRight.recycle();
    }

    /**
     * 绘制扫描线
     *
     * @param canvas
     * @param frame  扫描框
     */
    private void drawScanningLine(Canvas canvas, Rect frame) {
        // 初始化中间线滑动的最上边和最下边
        if (isFirst) {
            isFirst = false;
            slideTop = frame.top;
            slideBottom = frame.bottom;
        }

        // 绘制中间的线,每次刷新界面，中间的线往下移动SPEEN_DISTANCE
        slideTop += SPEEN_DISTANCE;
        if (slideTop >= slideBottom) {
            slideTop = frame.top;
        }

        // 从图片资源画扫描线
        Rect lineRect = new Rect();
        lineRect.left = frame.left;
        lineRect.right = frame.right;
        lineRect.top = slideTop;
        lineRect.bottom = (slideTop + MIDDLE_LINE_WIDTH);
        canvas.drawBitmap(((BitmapDrawable) getResources().getDrawable(
                R.drawable.scan_laser)).getBitmap(), null, lineRect, paint);
    }

    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
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

    public void drawViewfinder() {
        Bitmap resultBitmap = this.resultBitmap;
        this.resultBitmap = null;
        if (resultBitmap != null) {
            resultBitmap.recycle();
        }
        invalidate();
    }

    /**
     * dp转px
     */
    public int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }
}
