package com.wonrui.zxinglite.decode;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.wonrui.zxinglite.CaptureActivity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPointCallback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the heavy lifting of decoding the images.
 */
public class DecodeThread extends Thread {
    public static final String BARCODE_BITMAP = "barcode_bitmap";
    public static final String BARCODE_SCALED_FACTOR = "barcode_scaled_factor";

    public static final int ALL_MODE = 0X300;

    private final CaptureActivity activity;
    private final Map<DecodeHintType, Object> hints;
    private Handler handler;
    private final CountDownLatch handlerInitLatch;

    public DecodeThread(CaptureActivity activity,
                        int decodeMode,
                        ResultPointCallback resultPointCallback) {
        this.activity = activity;
        handlerInitLatch = new CountDownLatch(1);

        hints = new EnumMap<>(DecodeHintType.class);
        Collection<BarcodeFormat> decodeFormats = new ArrayList<BarcodeFormat>();
        switch (decodeMode) {
            case ALL_MODE:
                decodeFormats.addAll(DecodeFormatManager.PRODUCT_FORMATS);
                decodeFormats.addAll(DecodeFormatManager.INDUSTRIAL_FORMATS);
                decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
                decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS);
                decodeFormats.addAll(DecodeFormatManager.AZTEC_FORMATS);
                decodeFormats.addAll(DecodeFormatManager.PDF417_FORMATS);
                break;
            default:
        }
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
        hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK,
                resultPointCallback);
        Log.i("DecodeThread", "Hints: " + hints);
    }

    public Handler getHandler() {
        try {
            handlerInitLatch.await();
        } catch (InterruptedException ie) {
            // continue?
        }
        return handler;
    }

    @Override
    public void run() {
        Looper.prepare();
        handler = new DecodeHandler(activity, hints);
        handlerInitLatch.countDown();
        Looper.loop();
    }
}
