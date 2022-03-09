package com.android.quickstep;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;

import com.android.launcher3.LauncherState;
import com.android.launcher3.testing.TestInformationHandler;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.uioverrides.touchcontrollers.PortraitStatesTouchController;
import com.android.quickstep.util.LayoutUtils;

public class QuickstepTestInformationHandler extends TestInformationHandler {

    protected final Context mContext;

    public QuickstepTestInformationHandler(Context context) {
        mContext = context;
    }

    @Override
    public Bundle call(String method, String arg) {
        final Bundle response = new Bundle();
        switch (method) {
            case TestProtocol.REQUEST_ALL_APPS_TO_OVERVIEW_SWIPE_HEIGHT: {
                return getLauncherUIProperty(Bundle::putInt, l -> {
                    final float progress = LauncherState.OVERVIEW.getVerticalProgress(l)
                            - LauncherState.ALL_APPS.getVerticalProgress(l);
                    final float distance = l.getAllAppsController().getShiftRange() * progress;
                    return (int) distance;
                });
            }

            case TestProtocol.REQUEST_HOME_TO_OVERVIEW_SWIPE_HEIGHT: {
                final float swipeHeight =
                        LayoutUtils.getDefaultSwipeHeight(mContext, mDeviceProfile);
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) swipeHeight);
                return response;
            }

            case TestProtocol.REQUEST_BACKGROUND_TO_OVERVIEW_SWIPE_HEIGHT: {
                final float swipeHeight =
                        LayoutUtils.getShelfTrackingDistance(mContext, mDeviceProfile,
                                PagedOrientationHandler.PORTRAIT);
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) swipeHeight);
                return response;
            }

            case TestProtocol.REQUEST_HOTSEAT_TOP: {
                return getLauncherUIProperty(
                        Bundle::putInt, PortraitStatesTouchController::getHotseatTop);
            }

            case TestProtocol.REQUEST_GET_FOCUSED_TASK_HEIGHT_FOR_TABLET: {
                if (!mDeviceProfile.isTablet) {
                    return null;
                }
                Rect focusedTaskRect = new Rect();
                LauncherActivityInterface.INSTANCE.calculateTaskSize(mContext, mDeviceProfile,
                        focusedTaskRect);
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, focusedTaskRect.height());
                return response;
            }

            case TestProtocol.REQUEST_GET_GRID_TASK_SIZE_RECT_FOR_TABLET: {
                if (!mDeviceProfile.isTablet) {
                    return null;
                }
                Rect gridTaskRect = new Rect();
                LauncherActivityInterface.INSTANCE.calculateGridTaskSize(mContext, mDeviceProfile,
                        gridTaskRect, PagedOrientationHandler.PORTRAIT);
                response.putParcelable(TestProtocol.TEST_INFO_RESPONSE_FIELD, gridTaskRect);
                return response;
            }
        }

        return super.call(method, arg);
    }

    @Override
    protected Activity getCurrentActivity() {
        RecentsAnimationDeviceState rads = new RecentsAnimationDeviceState(mContext);
        OverviewComponentObserver observer = new OverviewComponentObserver(mContext, rads);
        try {
            return observer.getActivityInterface().getCreatedActivity();
        } finally {
            observer.onDestroy();
            rads.destroy();
        }
    }

    @Override
    protected boolean isLauncherInitialized() {
        return super.isLauncherInitialized() && TouchInteractionService.isInitialized();
    }
}
