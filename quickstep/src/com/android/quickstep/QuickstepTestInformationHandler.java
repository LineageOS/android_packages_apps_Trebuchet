package com.android.quickstep;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.testing.TestInformationHandler;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.TISBindHelper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class QuickstepTestInformationHandler extends TestInformationHandler {

    protected final Context mContext;

    public QuickstepTestInformationHandler(Context context) {
        mContext = context;
    }

    @Override
    public Bundle call(String method, String arg, @Nullable Bundle extras) {
        final Bundle response = new Bundle();
        switch (method) {
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

            case TestProtocol.REQUEST_GET_OVERVIEW_PAGE_SPACING: {
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        mDeviceProfile.overviewPageSpacing);
                return response;
            }

            case TestProtocol.REQUEST_HAS_TIS: {
                response.putBoolean(
                        TestProtocol.REQUEST_HAS_TIS, true);
                return response;
            }

            case TestProtocol.REQUEST_ENABLE_MANUAL_TASKBAR_STASHING:
                runOnTISBinder(tisBinder -> {
                    enableManualTaskbarStashing(tisBinder, true);
                });
                return response;

            case TestProtocol.REQUEST_DISABLE_MANUAL_TASKBAR_STASHING:
                runOnTISBinder(tisBinder -> {
                    enableManualTaskbarStashing(tisBinder, false);
                });
                return response;

            case TestProtocol.REQUEST_UNSTASH_TASKBAR_IF_STASHED:
                runOnTISBinder(tisBinder -> {
                    enableManualTaskbarStashing(tisBinder, true);

                    // Allow null-pointer to catch illegal states.
                    tisBinder.getTaskbarManager().getCurrentActivityContext()
                            .unstashTaskbarIfStashed();

                    enableManualTaskbarStashing(tisBinder, false);
                });
                return response;

            case TestProtocol.REQUEST_STASHED_TASKBAR_HEIGHT: {
                final Resources resources = mContext.getResources();
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        resources.getDimensionPixelSize(R.dimen.taskbar_stashed_size));
                return response;
            }
        }

        return super.call(method, arg, extras);
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

    private void enableManualTaskbarStashing(
            TouchInteractionService.TISBinder tisBinder, boolean enable) {
        // Allow null-pointer to catch illegal states.
        tisBinder.getTaskbarManager().getCurrentActivityContext().enableManualStashingDuringTests(
                enable);
    }

    /**
     * Runs the given command on the UI thread, after ensuring we are connected to
     * TouchInteractionService.
     */
    protected void runOnTISBinder(Consumer<TouchInteractionService.TISBinder> connectionCallback) {
        try {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            TISBindHelper helper = MAIN_EXECUTOR.submit(() ->
                    new TISBindHelper(mContext, tisBinder -> {
                        connectionCallback.accept(tisBinder);
                        countDownLatch.countDown();
                    })).get();
            countDownLatch.await();
            MAIN_EXECUTOR.execute(helper::onDestroy);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
