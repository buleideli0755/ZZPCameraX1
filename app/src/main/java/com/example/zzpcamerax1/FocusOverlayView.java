package com.example.zzpcamerax1;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class FocusOverlayView extends View {
    /*const values objects*/
    private final static String TAG = "FocusOverlayView";
    public final static int FOCUS_STATE_IDLE = 0;
    public final static int FOCUS_STATE_FOCUSING = 1;
    public final static int FOCUS_STATE_SUCCESS = 2;
    public final static int FOCUS_STATE_FAILED = 3;
    public final static int FOCUS_STATE_LOCKED = 4;
    /*ui objects*/
    private Paint mPaint;
    /*others objects*/
    private float mFocusCircleX, mFocusCircleY;
    private int mFocusState; //refer to FOCUS_STATE_XXX

    public FocusOverlayView(Context context) {
        super(context);
        init();
    }

    public FocusOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FocusOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        LogZZP.d(TAG,"init");
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(5f);
        mPaint.setAntiAlias(true);
        mFocusState = FOCUS_STATE_IDLE;
    }

    public void updateFocusUI(int state){
        mFocusState = state;
        invalidate(); // Redraw the circle
    }

    public void setFocusCircle(float x, float y, int state) {
        mFocusCircleX = x;
        mFocusCircleY = y;
        updateFocusUI(state);
        LogZZP.d(TAG,"setFocusCircle mFocusState:"+mFocusState);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        switch (mFocusState) {
            case FOCUS_STATE_FOCUSING:
                mPaint.setColor(Color.WHITE);
                break;
            case FOCUS_STATE_SUCCESS:
                mPaint.setColor(Color.GREEN);
                break;
            case FOCUS_STATE_FAILED:
                mPaint.setColor(Color.RED);
                break;
            case FOCUS_STATE_LOCKED:
                mPaint.setColor(Color.YELLOW);
                break;
            default:
                return;
        }
        canvas.drawCircle(mFocusCircleX, mFocusCircleY, 50, mPaint);
        //LogZZP.d(TAG,"onDraw");
    }
}