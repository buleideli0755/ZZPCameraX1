package com.example.zzpcamerax1;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import android.content.Context;
import android.hardware.camera2.CaptureRequest;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.core.CameraControl;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.Preview;
import androidx.camera.core.impl.utils.futures.FutureCallback;
import androidx.camera.core.impl.utils.futures.Futures;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.example.zzpcamerax1.FocusOverlayView;
import com.google.common.util.concurrent.ListenableFuture;

public class CameraView extends ConstraintLayout {
    /*const values objects*/
    private final static String TAG = "CameraView";
    private final static int FOCUS_TIMEOUT = 2000;
    private final static int FOCUS_UI_TIMEOUT = 2000;
    private final static int FOCUS_UI_DISAPPEAR_TIMEOUT = 2000;
    /*ui objects*/
    private PreviewView mPreviewView;
    private FocusOverlayView mFocusOverlayView;

    private GestureDetectorCompat mGestureDetector;
    /*logic processing objects*/
    private CameraControl mCameraControl;
    private Camera2CameraControl mCamera2Control;
    /*others objects*/
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;
    private ScheduledExecutorService mScheduledExecutorService;
    private Context mContext;
    private boolean mPause;
    public CameraView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public PreviewView getPreviewView() {
        return mPreviewView;
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        // Called when a long press is detected
        @Override
        public void onLongPress(MotionEvent e) {
            super.onLongPress(e);
            LogZZP.d(TAG, "onLongPress");
            // Lock AE/AF when long press occurs
            //startLockAF(e.getX(), e.getY());
            startLockAEAF(e.getX(), e.getY());
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            LogZZP.d(TAG, "onSingleTapConfirmed");
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                LogZZP.d(TAG,"tap focus");
                //startTAF(e.getX(), e.getY());
                startTAFAE(e.getX(), e.getY());
                return true;
            }
            return false;
        }
    }

    private void startLockAF(float x, float y) {
        LogZZP.d(TAG,"startLockAF x:"+x+",y:"+y);
        //resetAF();//cancel take about 300ms to complete,so do not call it before the af trigger.
        resetExecutor();
        mFocusOverlayView.setFocusCircle(x, y, FocusOverlayView.FOCUS_STATE_FOCUSING); // Set to focusing state,1

        if (mCameraControl != null) {
            //For lock af,use disableAutoCancel,to let the auto cancel focus not to process,so the af can indirectly locked.
            FocusMeteringAction focusAction = new FocusMeteringAction.Builder(
                    mPreviewView.getMeteringPointFactory().createPoint(x, y)).disableAutoCancel().build();
            LogZZP.d(TAG,"startLockAF focusAction CancelDuration:"+focusAction.getAutoCancelDurationInMillis());
            ListenableFuture<FocusMeteringResult> focusResult = mCameraControl.startFocusAndMetering(focusAction);
            focusResult.addListener(() -> {
                post(() -> {
                    LogZZP.d(TAG,"startLockAF mPause:"+mPause);
                    if(mPause)
                        return;
                    Boolean success = false;
                    try {
                        LogZZP.d(TAG,"startLockAF 1");
                        success = focusResult.get().isFocusSuccessful();
                        LogZZP.d(TAG,"startLockAF 2");
                    } catch (CancellationException | ExecutionException | InterruptedException e) {
                        /*
                        the following issue is normal.
                        1,when the before af processing has not been completed and the cancelfocus processing called by timeout,
                        and at that time is the current af is still processing,then the listener would be handled by the
                        exception(ExecutionException: Cancelled by cancelFocusAndMetering)
                        2,when the current af processing is still not complete,the next af trigger,then the current af processing would stop
                        by handling the listener,due to the exception catching(Cancelled by another startFocusAndMetering)
                        */
                        LogZZP.e(TAG,"startLockAF 3,"+e);
                    }
                    LogZZP.d(TAG,"startLockAF focus x:"+x+",y:"+y+",is success:"+success);
                    mFocusOverlayView.setFocusCircle(x, y, success ? FocusOverlayView.FOCUS_STATE_LOCKED :
                            FocusOverlayView.FOCUS_STATE_FAILED);
                });
            }, Executors.newSingleThreadExecutor());
        }
    }

    private void startLockAEAF(float x, float y) {
        LogZZP.d(TAG,"startLockAEAF x:"+x+",y:"+y);
        //resetAF();//cancel take about 300ms to complete,so do not call it before the af trigger.
        lockAE(false);
        resetExecutor();
        mFocusOverlayView.setFocusCircle(x, y, FocusOverlayView.FOCUS_STATE_FOCUSING); // Set to focusing state,1

        if (mCameraControl != null) {
            //For lock af,use disableAutoCancel,to let the auto cancel focus not to process,so the af can indirectly locked.
            FocusMeteringAction focusAction = new FocusMeteringAction.Builder(
                    mPreviewView.getMeteringPointFactory().createPoint(x, y)).disableAutoCancel().build();
            LogZZP.d(TAG,"startLockAEAF focusAction CancelDuration:"+focusAction.getAutoCancelDurationInMillis());
            ListenableFuture<FocusMeteringResult> focusResult = mCameraControl.startFocusAndMetering(focusAction);
            focusResult.addListener(() -> {
                post(() -> {
                    LogZZP.d(TAG,"startLockAEAF mPause:"+mPause);
                    if(mPause)
                        return;
                    Boolean success = false;
                    try {
                        LogZZP.d(TAG,"startLockAEAF 1");
                        success = focusResult.get().isFocusSuccessful();
                        LogZZP.d(TAG,"startLockAEAF 2");
                    } catch (CancellationException | ExecutionException | InterruptedException e) {
                        /*
                        the following issue is normal.
                        1,when the before af processing has not been completed and the cancelfocus processing called by timeout,
                        and at that time is the current af is still processing,then the listener would be handled by the
                        exception(ExecutionException: Cancelled by cancelFocusAndMetering)
                        2,when the current af processing is still not complete,the next af trigger,then the current af processing would stop
                        by handling the listener,due to the exception catching(Cancelled by another startFocusAndMetering)
                        */
                        LogZZP.e(TAG,"startLockAEAF 3,"+e);
                    }
                    LogZZP.d(TAG,"startLockAEAF focus x:"+x+",y:"+y+",is success:"+success);
                    mFocusOverlayView.setFocusCircle(x, y, success ? FocusOverlayView.FOCUS_STATE_LOCKED :
                            FocusOverlayView.FOCUS_STATE_FAILED);

                    // Focus and metering completed successfully,3A now converged,so the AE can be locked.
                    lockAE(true);
                });
            }, Executors.newSingleThreadExecutor());
        }
    }

    private void lockAE(boolean isLock){
        LogZZP.d(TAG,"lockAE isLock:"+isLock+",mCamera2Control:"+mCamera2Control);
        if(null == mCamera2Control)
            mCamera2Control = Camera2CameraControl.from(mCameraControl);
        LogZZP.d(TAG,"lockAE mCamera2Control:"+mCamera2Control);
        if(isLock){
            LogZZP.d(TAG,"lockAE lock");
            CaptureRequestOptions options = new CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                    .build();
            ListenableFuture<Void> future = mCamera2Control.setCaptureRequestOptions(options);
            /*Futures.addCallback(future, new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable Void result) {
                    LogZZP.d(TAG,"lockAE onSuccess,isLock:"+isLock);
                }
                @Override
                public void onFailure(@NonNull Throwable t) {
                    LogZZP.d(TAG,"lockAE onFailure,isLock:"+isLock);
                }
            }, ContextCompat.getMainExecutor(mContext));*/
        }else{
            if(null != mCamera2Control.getCaptureRequestOptions()){
                Object o = mCamera2Control.getCaptureRequestOptions().getCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK);
/*                LogZZP.d(TAG,"lockAE mCamera2Control.getCaptureRequestOptions():"+mCamera2Control.getCaptureRequestOptions());
                LogZZP.d(TAG,"lockAE mCamera2Control.getCaptureRequestOptions().getCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK):"
                        +o);*/
                if(null != o){
                    boolean hasLocked = (boolean)o;
                    LogZZP.d(TAG,"lockAE unlock hasLocked:"+hasLocked);
                    if(hasLocked){
                        CaptureRequestOptions options = new CaptureRequestOptions.Builder()
                                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
                                .build();
                        ListenableFuture<Void> future = mCamera2Control.setCaptureRequestOptions(options);
                        /*Futures.addCallback(future, new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(@Nullable Void result) {
                                LogZZP.d(TAG,"lockAE onSuccess,isLock:"+isLock);
                            }
                            @Override
                            public void onFailure(@NonNull Throwable t) {
                                LogZZP.d(TAG,"lockAE onFailure,isLock:"+isLock);
                            }
                        }, ContextCompat.getMainExecutor(mContext));*/
                    }
                }
            }
        }
    }

    private void init(@NonNull Context context) {
        LogZZP.d(TAG,"init");
        mContext = context;

        mGestureDetector = new GestureDetectorCompat(mContext, new GestureListener());
        mScheduledExecutorService = Executors.newScheduledThreadPool(1);

        LayoutInflater inflater = LayoutInflater.from(mContext);
        inflater.inflate(R.layout.camera_view, this, true);
        mPreviewView = findViewById(R.id.previewView);
        mFocusOverlayView = findViewById(R.id.focusOverlayView);
        mPreviewView.setOnTouchListener((v, event) -> {
            mGestureDetector.onTouchEvent(event); // Pass touch events to GestureDetector
            return true;
        });

        DisplayMetrics dm = getPreviewView().getResources().getDisplayMetrics();
        mScreenWidth = dm.widthPixels;
        mScreenHeight = dm.heightPixels;

        mCamera2Control= null;
        LogZZP.d(TAG,"init mScreenWidth:"+mScreenWidth+",mScreenHeight:"+mScreenHeight);
    }

    public void configPreview(Preview preview) {
        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());
    }
    public void setCameraControl(CameraControl cameraControl) {
        this.mCameraControl = cameraControl;
    }

    private void startTAF(float x, float y) {
        LogZZP.d(TAG,"startTAF x:"+x+",y:"+y);
        //resetAF();//cancel take about 300ms to complete,so do not call it before the af trigger.
        resetExecutor();
        mFocusOverlayView.setFocusCircle(x, y, FocusOverlayView.FOCUS_STATE_FOCUSING); // Set to focusing state,1

        if (mCameraControl != null) {
            //For TAF,should setAutoCancelDuration,to cancel af automatically.
            FocusMeteringAction focusAction = new FocusMeteringAction.Builder(
                    mPreviewView.getMeteringPointFactory().createPoint(x, y)).setAutoCancelDuration(FOCUS_TIMEOUT,TimeUnit.MILLISECONDS).build();
            LogZZP.d(TAG,"startTAF focusAction CancelDuration:"+focusAction.getAutoCancelDurationInMillis());
            ListenableFuture<FocusMeteringResult> focusResult = mCameraControl.startFocusAndMetering(focusAction);
            focusResult.addListener(() -> {//after auto cancel called,the callback would also be invoked.
                post(() -> {
                    LogZZP.d(TAG,"startTAF mPause:"+mPause);
                    if(mPause)
                        return;
                    Boolean success = false;
                    try {
                        LogZZP.d(TAG,"startTAF 1");
                        success = focusResult.get().isFocusSuccessful();
                        LogZZP.d(TAG,"startTAF 2");
                    } catch (CancellationException | ExecutionException | InterruptedException e) {
                        /*
                        the following issue is normal.
                        1,when the before af processing has not been completed and the cancelfocus processing called by timeout,
                        and at that time is the current af is still processing,then the listener would be handled by the
                        exception(ExecutionException: Cancelled by cancelFocusAndMetering)
                        2,when the current af processing is still not complete,the next af trigger,then the current af processing would stop
                        by handling the listener,due to the exception catching(Cancelled by another startFocusAndMetering)
                        */
                        LogZZP.e(TAG,"startTAF 3,"+e);
                    }
                    LogZZP.d(TAG,"startTAF focus x:"+x+",y:"+y+",is success:"+success);
                    mFocusOverlayView.setFocusCircle(x, y, success ? FocusOverlayView.FOCUS_STATE_SUCCESS :
                            FocusOverlayView.FOCUS_STATE_FAILED);
                    hideFocusCircle(x, y);
                });
            }, Executors.newSingleThreadExecutor());
            // Timeout after 2 seconds to fail focus
            //The below process is no need,for the setAutoCancelDuration above do the same thing.
/*            mScheduledExecutorService.schedule(() -> {
                if (mFocusOverlayView.getFocusState() == FocusOverlayView.FOCUS_STATE_FOCUSING) {
                    post(() -> {
                        LogZZP.d(TAG,"focus ui timeout");
                        mFocusOverlayView.setFocusCircle(x, y, FocusOverlayView.FOCUS_STATE_FAILED); // Set to failed state,FOCUS_STATE_FAILED
                        scheduleHideCircle(x, y);
                        //cancel the focus processing
                        LogZZP.d(TAG,"call focus cancel from app");
                        mCameraControl.cancelFocusAndMetering();
                    });
                }
            }, FOCUS_UI_TIMEOUT, TimeUnit.MILLISECONDS);*/
        }
    }

    private void startTAFAE(float x, float y) {
        LogZZP.d(TAG,"startTAFAE x:"+x+",y:"+y);
        //resetAF();//cancel take about 300ms to complete,so do not call it before the af trigger.
        lockAE(false);
        resetExecutor();
        mFocusOverlayView.setFocusCircle(x, y, FocusOverlayView.FOCUS_STATE_FOCUSING); // Set to focusing state,1

        if (mCameraControl != null) {
            //For TAF,should setAutoCancelDuration,to cancel af automatically.
            FocusMeteringAction focusAction = new FocusMeteringAction.Builder(
                    mPreviewView.getMeteringPointFactory().createPoint(x, y)).setAutoCancelDuration(FOCUS_TIMEOUT,TimeUnit.MILLISECONDS).build();
            LogZZP.d(TAG,"startTAFAE focusAction CancelDuration:"+focusAction.getAutoCancelDurationInMillis());
            ListenableFuture<FocusMeteringResult> focusResult = mCameraControl.startFocusAndMetering(focusAction);
            focusResult.addListener(() -> {//after auto cancel called,the callback would also be invoked.
                post(() -> {
                    LogZZP.d(TAG,"startTAFAE mPause:"+mPause);
                    if(mPause)
                        return;
                    Boolean success = false;
                    try {
                        LogZZP.d(TAG,"startTAFAE 1");
                        success = focusResult.get().isFocusSuccessful();
                        LogZZP.d(TAG,"startTAFAE 2");
                    } catch (CancellationException | ExecutionException | InterruptedException e) {
                        /*
                        the following issue is normal.
                        1,when the before af processing has not been completed and the cancelfocus processing called by timeout,
                        and at that time is the current af is still processing,then the listener would be handled by the
                        exception(ExecutionException: Cancelled by cancelFocusAndMetering)
                        2,when the current af processing is still not complete,the next af trigger,then the current af processing would stop
                        by handling the listener,due to the exception catching(Cancelled by another startFocusAndMetering)
                        */
                        LogZZP.e(TAG,"startTAFAE 3,"+e);
                    }
                    LogZZP.d(TAG,"startTAFAE focus x:"+x+",y:"+y+",is success:"+success);
                    mFocusOverlayView.setFocusCircle(x, y, success ? FocusOverlayView.FOCUS_STATE_SUCCESS :
                            FocusOverlayView.FOCUS_STATE_FAILED);
                    hideFocusCircle(x, y);
                });
            }, Executors.newSingleThreadExecutor());
        }
    }

    private void resetAF(){
        LogZZP.d(TAG,"resetAF cancelFocusAndMetering1");
        ListenableFuture<Void> future = mCameraControl.cancelFocusAndMetering();
        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LogZZP.d(TAG,"cancel af onSuccess");
            }
            @Override
            public void onFailure(@NonNull Throwable t) {
                LogZZP.d(TAG,"cancel af  onFailure");
            }
        }, ContextCompat.getMainExecutor(mContext));
        LogZZP.d(TAG,"resetAF cancelFocusAndMetering2");
        List<Runnable> notDoRunnable = mScheduledExecutorService.shutdownNow();
        LogZZP.d(TAG,"resetAF notDoRunnable.size:"+notDoRunnable.size());
        mScheduledExecutorService = Executors.newScheduledThreadPool(1);
    }

    private void resetExecutor(){
        List<Runnable> notDoRunnable = mScheduledExecutorService.shutdownNow();
        LogZZP.d(TAG,"resetExecutor notDoRunnable.size:"+notDoRunnable.size());
        mScheduledExecutorService = Executors.newScheduledThreadPool(1);
    }

    private void hideFocusCircle(float x, float y) {
        LogZZP.d(TAG,"hideFocusCircle post task");
        mScheduledExecutorService.schedule(() -> {
            LogZZP.d(TAG,"hideFocusCircle hide focus ui");
            post(() -> mFocusOverlayView.setFocusCircle(x, y, FocusOverlayView.FOCUS_STATE_IDLE)); // Hide circle,0
        }, FOCUS_UI_DISAPPEAR_TIMEOUT, TimeUnit.MILLISECONDS);
    }
    public void setPreviewAspect(String ratio) {
        boolean isPortrait = mContext.getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT;

        int bottomPanelHeight = Utils.dip2px(mContext, 90);
        int bottomPanelOffset = mScreenHeight - bottomPanelHeight;
        LogZZP.d(TAG,"setPreviewAspect isPortrait:"+isPortrait+",mScreenWidth:"+mScreenWidth+
                ",mScreenHeight:"+mScreenHeight+",bottomPanelHeight:"+bottomPanelHeight+
                ",bottomPanelOffset:"+bottomPanelOffset);
        int width = mScreenWidth;
        int height = mScreenHeight;
        int topMargin = 0;
        int leftMargin = 0;

        if (isPortrait) {
            switch (ratio) {
                case "16:9":
                    height = (int) (width * 16 / 9.0F);
                    break;
                case "4:3":
                    height = (int) (width * 4 / 3.0F);
                    break;
                case "1:1":
                    height = width = mScreenWidth;
                    break;
                case "Full":
                    //height = width = mScreenWidth;
                    break;
                default:
                    height = (int) (width * 4 / 3.0F);
                    break;
            }

            topMargin = (mScreenHeight - height) / 2;
            if (topMargin + height > bottomPanelOffset) {
                topMargin = bottomPanelOffset - height;
            }
        } else {
            switch (ratio) {
                case "16:9":
                    width = (int) (height * 16 / 9.0F);
                    break;
                case "4:3":
                    width = (int) (height * 4 / 3.0F);
                    break;
                case "1:1":
                    height = width = mScreenHeight;
                    break;
                case "Full":
                    //height = width = mScreenWidth;
                    break;
                default:
                    width = (int) (height * 4 / 3.0F);
                    break;
            }

            leftMargin = (mScreenWidth - width) / 2;
        }
        LogZZP.d(TAG,"setPreviewAspect width:"+width+",height:"+height+",leftMargin:"+leftMargin+",topMargin:"+topMargin);
        setLayoutParams(width, height, leftMargin, topMargin);
    }
    private void setLayoutParams(int width, int height, int leftMargin, int topMargin) {
        ViewGroup.LayoutParams params =  mPreviewView.getLayoutParams();
        if (params == null) {
            return;
        }

        params.width = width;
        params.height = height;

        LogZZP.d(TAG,"setLayoutParams params.width:"+params.width+",params.height:"+params.height);
        mPreviewView.setLayoutParams(params);
    }

    public void onPause(){
        LogZZP.d(TAG,"onPause e");
        mPause = true;
        resetAF();
        mFocusOverlayView.updateFocusUI(FocusOverlayView.FOCUS_STATE_IDLE);
        LogZZP.d(TAG,"onPause x");
    }

    public void onResume(){
        LogZZP.d(TAG,"onResume");
        mPause = false;
    }
}