/*
 * Copyright (C) 2022 Project Kaleidoscope
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

package com.android.quickstep.views;

import static com.android.launcher3.util.NavigationMode.THREE_BUTTONS;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout.LayoutParams;
import android.widget.TextView;

import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

import java.lang.Runnable;
import java.math.BigDecimal;

public class MemInfoView extends TextView {

    // When to show GB instead of MB
    private static final int UNIT_CONVERT_THRESHOLD = 1024; /* MiB */

    private static final BigDecimal GB2MB = new BigDecimal(1024);

    private static final int ALPHA_STATE_CTRL = 0;
    public static final int ALPHA_FS_PROGRESS = 1;

    public static final FloatProperty<MemInfoView> STATE_CTRL_ALPHA =
            new FloatProperty<MemInfoView>("state control alpha") {
                @Override
                public Float get(MemInfoView view) {
                    return view.getAlpha(ALPHA_STATE_CTRL);
                }

                @Override
                public void setValue(MemInfoView view, float v) {
                    view.setAlpha(ALPHA_STATE_CTRL, v);
                }
            };

    private DeviceProfile mDp;
    private MultiValueAlpha mAlpha;
    private ActivityManager mActivityManager;

    private Handler mHandler;
    private MemInfoWorker mWorker;

    private String mMemInfoText;

    public MemInfoView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mAlpha = new MultiValueAlpha(this, 2);
        mAlpha.setUpdateVisibility(true);
        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        mWorker = new MemInfoWorker();

        mMemInfoText = context.getResources().getString(R.string.meminfo_text);
    }

    /* Hijack this method to detect visibility rather than
     * onVisibilityChanged() because the the latter one can be
     * influenced by more factors, leading to unstable behavior. */
    @Override
    public void setVisibility(int visibility) {
        if (visibility == VISIBLE) {
            boolean showMeminfo = Utilities.isShowMeminfo(getContext());
            if (!showMeminfo) visibility = GONE;
        }

        super.setVisibility(visibility);

        if (visibility == VISIBLE)
            mHandler.post(mWorker);
        else
            mHandler.removeCallbacks(mWorker);
    }

    public void setDp(DeviceProfile dp) {
        mDp = dp;
    }

    public void setAlpha(int alphaType, float alpha) {
        mAlpha.get(alphaType).setValue(alpha);
    }

    public float getAlpha(int alphaType) {
        return mAlpha.get(alphaType).getValue();
    }

    public void updateVerticalMargin(NavigationMode mode) {
        LayoutParams lp = (LayoutParams)getLayoutParams();
        int bottomMargin;

        if (mode == THREE_BUTTONS)
            bottomMargin = mDp.memInfoMarginThreeButtonPx;
        else
            bottomMargin = mDp.memInfoMarginGesturePx;

        lp.setMargins(lp.leftMargin, lp.topMargin, lp.rightMargin, bottomMargin);
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
    }

    private String unitConvert(long valueMiB, boolean alignToGB) {
        BigDecimal rawVal = new BigDecimal(valueMiB);

        if (alignToGB)
            return rawVal.divide(GB2MB, 0, BigDecimal.ROUND_UP) + " GB";

        if (valueMiB > UNIT_CONVERT_THRESHOLD)
            return rawVal.divide(GB2MB, 1, BigDecimal.ROUND_HALF_UP) + " GB";
        else
            return rawVal + " MB";
    }

    private void updateMemInfoText(long availMemMiB, long totalMemMiB) {
        String text = String.format(mMemInfoText,
            unitConvert(availMemMiB, false), unitConvert(totalMemMiB, true));
        setText(text);
    }

    private class MemInfoWorker implements Runnable {
        @Override
        public void run() {
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            mActivityManager.getMemoryInfo(memInfo);
            long availMemMiB = memInfo.availMem / (1024 * 1024);
            long totalMemMiB = memInfo.totalMem / (1024 * 1024);
            updateMemInfoText(availMemMiB, totalMemMiB);

            mHandler.postDelayed(this, 1000);
        }
    }
}
