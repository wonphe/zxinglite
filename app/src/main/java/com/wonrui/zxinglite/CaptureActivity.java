package com.wonrui.zxinglite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.wonrui.zxinglite.camera.CameraManager;
import com.wonrui.zxinglite.decode.CaptureActivityHandler;
import com.wonrui.zxinglite.decode.DecodeThread;
import com.wonrui.zxinglite.utils.AmbientLightManager;
import com.wonrui.zxinglite.utils.BeepManager;
import com.wonrui.zxinglite.utils.FinishListener;
import com.wonrui.zxinglite.utils.InactivityTimer;
import com.wonrui.zxinglite.view.ViewfinderView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;

import java.io.IOException;

public class CaptureActivity extends Activity implements
        SurfaceHolder.Callback, View.OnClickListener {
    private static final String TAG = CaptureActivity.class.getSimpleName();

    private CameraManager cameraManager;
    private ViewfinderView viewfinderView;
    private BeepManager beepManager;
    private AmbientLightManager ambientLightManager;
    private InactivityTimer inactivityTimer;
    private boolean hasSurface;
    private CaptureActivityHandler handler;
    private boolean isFlashlightOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //保持屏幕常量
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_capture);

        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);
        // 提示音&震动管理
        beepManager = new BeepManager(this);
        ambientLightManager = new AmbientLightManager(this);

        // 返回键事件
        findViewById(R.id.capture_back).setOnClickListener(this);
        // 闪光灯按钮
        findViewById(R.id.capture_flashlight).setOnClickListener(this);
    }

    /**
     * 由于MainActivity中使用startActivityForResult(intent, 0)来启动当前activity
     * 因此退出时需返回一个值供MainActivity进行处理
     * 重写返回键事件，返回一个空值给前一个Activity
     */
    @Override
    public void onBackPressed() {
        setResult(0, new Intent().putExtra("ScanResult", ""));
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 相机初始化的动作需要开启相机并测量屏幕大小，这些操作
        // 不建议放到onCreate中，因为如果在onCreate中加上首次启动展示帮助信息的代码的话，
        // 会导致扫描窗口的尺寸计算有误的bug
        cameraManager = new CameraManager(getApplication());
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        viewfinderView.setCameraManager(cameraManager);

        handler = null;

        // 加载声音配置，其实在BeemManager的构造器中也会调用该方法，即在onCreate的时候会调用一次
        beepManager.updatePrefs();
        // 启动闪光灯调节器
        ambientLightManager.start(cameraManager);
        // 恢复活动监控器
        inactivityTimer.onResume();

        // 摄像头预览功能必须借助SurfaceView，因此也需要在一开始对其进行初始化
        // 如果需要了解SurfaceView的原理
        // 参考:http://blog.csdn.net/luoshengyang/article/details/8661317
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(surfaceHolder);
        } else {
            // Install the callback and wait for surfaceCreated() to init the camera.
            surfaceHolder.addCallback(this);
        }
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        ambientLightManager.stop();
        beepManager.close();
        cameraManager.closeDriver();
        //historyManager = null; // Keep for onActivityResult
        if (!hasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a RuntimeException.
            if (handler == null) {
                handler = new CaptureActivityHandler(this, cameraManager, DecodeThread.ALL_MODE);
            }
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    private void displayFrameworkBugMessageAndExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage(getString(R.string.msg_camera_framework_bug));
        builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
        builder.setOnCancelListener(new FinishListener(this));
        builder.show();
    }

    //region 实现SurfaceHolder.Callback接口
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        hasSurface = false;
    }
    //endregion

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    /**
     * A valid barcode has been found, so give an indication of success and show the results.
     *
     * @param rawResult   The contents of the barcode.
     * @param scaleFactor amount by which thumbnail was scaled
     * @param barcode     A greyscale bitmap of the camera data which was decoded.
     */
    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        inactivityTimer.onActivity();

        boolean fromLiveScan = barcode != null;
        if (fromLiveScan) {
            // Then not from history, so beep/vibrate and we have an image to draw on
            beepManager.playBeepSoundAndVibrate();
            drawResultPoints(barcode, scaleFactor, rawResult);
        }
        // 回传结果给调用改Activity的页面
        setResult(0, new Intent().putExtra("ScanResult", rawResult.getText()));
        finish();
    }

    /**
     * Superimpose a line for 1D or dots for 2D to highlight the key features of the barcode.
     *
     * @param barcode     A bitmap of the captured image.
     * @param scaleFactor amount by which thumbnail was scaled
     * @param rawResult   The decoded results which contains the points to draw.
     */
    private void drawResultPoints(Bitmap barcode, float scaleFactor, Result rawResult) {
        ResultPoint[] points = rawResult.getResultPoints();
        if (points != null && points.length > 0) {
            Canvas canvas = new Canvas(barcode);
            Paint paint = new Paint();
            paint.setColor(getResources().getColor(R.color.result_points));
            if (points.length == 2) {
                paint.setStrokeWidth(4.0f);
                drawLine(canvas, paint, points[0], points[1], scaleFactor);
            } else if (points.length == 4 &&
                    (rawResult.getBarcodeFormat() == BarcodeFormat.UPC_A ||
                            rawResult.getBarcodeFormat() == BarcodeFormat.EAN_13)) {
                // Hacky special case -- draw two lines, for the barcode and metadata
                drawLine(canvas, paint, points[0], points[1], scaleFactor);
                drawLine(canvas, paint, points[2], points[3], scaleFactor);
            } else {
                paint.setStrokeWidth(10.0f);
                for (ResultPoint point : points) {
                    if (point != null) {
                        canvas.drawPoint(scaleFactor * point.getX(), scaleFactor * point.getY(), paint);
                    }
                }
            }
        }
    }


    private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b, float scaleFactor) {
        if (a != null && b != null) {
            canvas.drawLine(scaleFactor * a.getX(),
                    scaleFactor * a.getY(),
                    scaleFactor * b.getX(),
                    scaleFactor * b.getY(),
                    paint);
        }
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    public Handler getHandler() {
        return handler;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.capture_back: // 返回按钮
                // 由于MainActivity中使用startActivityForResult(intent, 0)来启动当前activity
                // 因此退出时需返回一个值供MainActivity进行处理
                setResult(0, new Intent().putExtra("ScanResult", ""));
                finish();
                break;
            case R.id.capture_flashlight:
                if (isFlashlightOpen) {
                    cameraManager.setTorch(false); // 关闭闪光灯
                    findViewById(R.id.capture_flashlight).
                            setBackgroundResource(R.drawable.ic_action_flash_off);
                    isFlashlightOpen = false;
                } else {
                    cameraManager.setTorch(true); // 打开闪光灯
                    findViewById(R.id.capture_flashlight).
                            setBackgroundResource(R.drawable.ic_action_flash_on);
                    isFlashlightOpen = true;
                }
                break;
            default:
                break;
        }
    }
}
