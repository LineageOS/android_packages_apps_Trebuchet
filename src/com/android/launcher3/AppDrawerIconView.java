package com.android.launcher3;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AppDrawerIconView extends LinearLayout {

    TextView mLabel;
    ImageView mIcon;

    public AppDrawerIconView(Context context) {
        super(context);
    }

    public AppDrawerIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AppDrawerIconView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLabel = (TextView) findViewById(R.id.label);
        mIcon = (ImageView) findViewById(R.id.image);
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        mLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.iconTextSizePx);
        mLabel.setShadowLayer(BubbleTextView.SHADOW_LARGE_RADIUS, 0.0f,
                BubbleTextView.SHADOW_Y_OFFSET, BubbleTextView.SHADOW_LARGE_COLOUR);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            setAlpha(PagedViewIcon.PRESS_ALPHA);
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            setAlpha(1f);
        }
        return super.onTouchEvent(event);
    }
}