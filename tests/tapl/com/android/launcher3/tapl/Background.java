/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.tapl;

import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

import static com.android.launcher3.tapl.OverviewTask.TASK_START_EVENT;
import static com.android.launcher3.testing.TestProtocol.OVERVIEW_STATE_ORDINAL;

import android.graphics.Point;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.TestProtocol;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Indicates the base state with a UI other than Overview running as foreground. It can also
 * indicate Launcher as long as Launcher is not in Overview state.
 */
public class Background extends LauncherInstrumentation.VisibleContainer {
    private static final int ZERO_BUTTON_SWIPE_UP_GESTURE_DURATION = 500;
    private static final Pattern SQUARE_BUTTON_EVENT = Pattern.compile("onOverviewToggle");

    Background(LauncherInstrumentation launcher) {
        super(launcher);
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.BACKGROUND;
    }

    /**
     * Swipes up or presses the square button to switch to Overview.
     * Returns the base overview, which can be either in Launcher or the fallback recents.
     *
     * @return the Overview panel object.
     */
    @NonNull
    public BaseOverview switchToOverview() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to switch from background to overview")) {
            verifyActiveContainer();
            goToOverviewUnchecked();
            return mLauncher.isFallbackOverview()
                    ? new BaseOverview(mLauncher) : new Overview(mLauncher);
        }
    }


    protected boolean zeroButtonToOverviewGestureStartsInLauncher() {
        return mLauncher.isTablet();
    }

    protected boolean zeroButtonToOverviewGestureStateTransitionWhileHolding() {
        return false;
    }

    protected void goToOverviewUnchecked() {
        switch (mLauncher.getNavigationModel()) {
            case ZERO_BUTTON: {
                sendDownPointerToEnterOverviewToLauncher();
                String swipeAndHoldToEnterOverviewActionName =
                        "swiping and holding to enter overview";
                // If swiping from an app (e.g. Overview is in Background), we pause and hold on
                // swipe up to make overview appear, or else swiping without holding would take
                // us to the Home state. If swiping up from Home (e.g. Overview in Home or
                // Workspace state where the below condition is true), there is no need to pause,
                // and we will not test for an intermediate carousel as one will not exist.
                if (zeroButtonToOverviewGestureStateTransitionWhileHolding()) {
                    mLauncher.runToState(this::sendSwipeUpAndHoldToEnterOverviewGestureToLauncher,
                            OVERVIEW_STATE_ORDINAL, swipeAndHoldToEnterOverviewActionName);
                    sendUpPointerToEnterOverviewToLauncher();
                } else {
                    // If swiping up from an app to overview, pause on intermediate carousel
                    // until snapshots are visible. No intermediate carousel when swiping from
                    // Home. The task swiped up is not a snapshot but the TaskViewSimulator. If
                    // only a single task exists, no snapshots will be available during swipe up.
                    mLauncher.executeAndWaitForLauncherEvent(
                            this::sendSwipeUpAndHoldToEnterOverviewGestureToLauncher,
                            event -> TestProtocol.PAUSE_DETECTED_MESSAGE.equals(
                                    event.getClassName().toString()),
                            () -> "Pause wasn't detected",
                            swipeAndHoldToEnterOverviewActionName);
                    try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                            "paused on swipe up to overview")) {
                        if (mLauncher.getRecentTasks().size() > 1) {
                            // When swiping up to grid-overview for tablets, the swiped tab will be
                            // in the middle of the screen (TaskViewSimulator, not a snapshot), and
                            // all remaining snapshots will be to the left of that task. In
                            // non-tablet overview, snapshots can be on either side of the swiped
                            // task, but we still check that they become visible after swiping and
                            // pausing.
                            mLauncher.waitForOverviewObject("snapshot");
                            if (mLauncher.isTablet()) {
                                List<UiObject2> tasks = mLauncher.getDevice().findObjects(
                                        mLauncher.getOverviewObjectSelector("snapshot"));
                                final int centerX = mLauncher.getDevice().getDisplayWidth() / 2;
                                mLauncher.assertTrue(
                                        "All tasks not to the left of the swiped task",
                                        tasks.stream()
                                                .allMatch(
                                                        t -> t.getVisibleBounds().right < centerX));
                            }

                        }
                        String upPointerToEnterOverviewActionName =
                                "sending UP pointer to enter overview";
                        mLauncher.runToState(this::sendUpPointerToEnterOverviewToLauncher,
                                OVERVIEW_STATE_ORDINAL, upPointerToEnterOverviewActionName);
                    }
                }
                break;
            }

            case TWO_BUTTON: {
                final int startX;
                final int startY;
                final int endX;
                final int endY;
                final int swipeLength = mLauncher.getTestInfo(getSwipeHeightRequestName()).
                        getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD) + mLauncher.getTouchSlop();

                if (mLauncher.getDevice().isNaturalOrientation()) {
                    startX = endX = mLauncher.getDevice().getDisplayWidth() / 2;
                    startY = getSwipeStartY();
                    endY = startY - swipeLength;
                } else {
                    startX = getSwipeStartX();
                    // TODO(b/184059820) make horizontal swipe use swipe width not height, for the
                    // moment just double the swipe length.
                    endX = startX - swipeLength * 2;
                    startY = endY = mLauncher.getDevice().getDisplayHeight() / 2;
                }

                mLauncher.swipeToState(startX, startY, endX, endY, 10, OVERVIEW_STATE_ORDINAL,
                        LauncherInstrumentation.GestureScope.OUTSIDE_WITH_PILFER);
                break;
            }

            case THREE_BUTTON:
                if (mLauncher.isTablet()) {
                    mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN,
                            LauncherInstrumentation.EVENT_TOUCH_DOWN);
                    mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN,
                            LauncherInstrumentation.EVENT_TOUCH_UP);
                }
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, SQUARE_BUTTON_EVENT);
                mLauncher.runToState(
                        () -> mLauncher.waitForNavigationUiObject("recent_apps").click(),
                        OVERVIEW_STATE_ORDINAL, "clicking Recents button");
                break;
        }
        expectSwitchToOverviewEvents();
    }

    private void expectSwitchToOverviewEvents() {
    }

    private void sendDownPointerToEnterOverviewToLauncher() {
        final int centerX = mLauncher.getDevice().getDisplayWidth() / 2;
        final int startY = getSwipeStartY();
        final Point start = new Point(centerX, startY);
        final long downTime = SystemClock.uptimeMillis();
        final LauncherInstrumentation.GestureScope gestureScope =
                zeroButtonToOverviewGestureStartsInLauncher()
                        ? LauncherInstrumentation.GestureScope.INSIDE_TO_OUTSIDE
                        : LauncherInstrumentation.GestureScope.OUTSIDE_WITH_PILFER;

        mLauncher.sendPointer(
                downTime, downTime, MotionEvent.ACTION_DOWN, start, gestureScope);
    }

    private void sendSwipeUpAndHoldToEnterOverviewGestureToLauncher() {
        final int centerX = mLauncher.getDevice().getDisplayWidth() / 2;
        final int startY = getSwipeStartY();
        final int swipeHeight = mLauncher.getTestInfo(getSwipeHeightRequestName()).getInt(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
        final Point start = new Point(centerX, startY);
        final Point end =
                new Point(centerX, startY - swipeHeight - mLauncher.getTouchSlop());
        final long downTime = SystemClock.uptimeMillis();
        final LauncherInstrumentation.GestureScope gestureScope =
                zeroButtonToOverviewGestureStartsInLauncher()
                        ? LauncherInstrumentation.GestureScope.INSIDE_TO_OUTSIDE
                        : LauncherInstrumentation.GestureScope.OUTSIDE_WITH_PILFER;

        mLauncher.movePointer(
                downTime,
                downTime,
                ZERO_BUTTON_SWIPE_UP_GESTURE_DURATION,
                start,
                end,
                gestureScope);
    }

    private void sendUpPointerToEnterOverviewToLauncher() {
        final int centerX = mLauncher.getDevice().getDisplayWidth() / 2;
        final int startY = getSwipeStartY();
        final int swipeHeight = mLauncher.getTestInfo(getSwipeHeightRequestName()).getInt(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
        final Point end =
                new Point(centerX, startY - swipeHeight - mLauncher.getTouchSlop());
        final long downTime = SystemClock.uptimeMillis();
        final LauncherInstrumentation.GestureScope gestureScope =
                zeroButtonToOverviewGestureStartsInLauncher()
                        ? LauncherInstrumentation.GestureScope.INSIDE_TO_OUTSIDE
                        : LauncherInstrumentation.GestureScope.OUTSIDE_WITH_PILFER;

        mLauncher.sendPointer(downTime, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, end, gestureScope);
    }

    @NonNull
    public Background quickSwitchToPreviousApp() {
        boolean toRight = true;
        quickSwitch(toRight);
        return new Background(mLauncher);
    }

    @NonNull
    public Background quickSwitchToPreviousAppSwipeLeft() {
        boolean toRight = false;
        quickSwitch(toRight);
        return new Background(mLauncher);
    }

    @NonNull
    private void quickSwitch(boolean toRight) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to quick switch to the previous app")) {
            verifyActiveContainer();
            final boolean launcherWasVisible = mLauncher.isLauncherVisible();
            boolean transposeInLandscape = false;
            switch (mLauncher.getNavigationModel()) {
                case TWO_BUTTON:
                    transposeInLandscape = true;
                    // Fall through, zero button and two button modes behave the same.
                case ZERO_BUTTON: {
                    final int startX;
                    final int startY;
                    final int endX;
                    final int endY;
                    final int cornerRadius = (int) Math.ceil(mLauncher.getWindowCornerRadius());
                    if (toRight) {
                        if (mLauncher.getDevice().isNaturalOrientation() || !transposeInLandscape) {
                            // Swipe from the bottom left to the bottom right of the screen.
                            startX = cornerRadius;
                            startY = getSwipeStartY();
                            endX = mLauncher.getDevice().getDisplayWidth() - cornerRadius;
                            endY = startY;
                        } else {
                            // Swipe from the bottom right to the top right of the screen.
                            startX = getSwipeStartX();
                            startY = mLauncher.getRealDisplaySize().y - 1 - cornerRadius;
                            endX = startX;
                            endY = cornerRadius;
                        }
                    } else {
                        if (mLauncher.getDevice().isNaturalOrientation() || !transposeInLandscape) {
                            // Swipe from the bottom right to the bottom left of the screen.
                            startX = mLauncher.getDevice().getDisplayWidth() - cornerRadius;
                            startY = getSwipeStartY();
                            endX = cornerRadius;
                            endY = startY;
                        } else {
                            // Swipe from the bottom left to the top left of the screen.
                            startX = getSwipeStartX();
                            startY = cornerRadius;
                            endX = startX;
                            endY = mLauncher.getRealDisplaySize().y - 1 - cornerRadius;
                        }
                    }

                    final boolean isZeroButton = mLauncher.getNavigationModel()
                            == LauncherInstrumentation.NavigationModel.ZERO_BUTTON;
                    LauncherInstrumentation.GestureScope gestureScope =
                            launcherWasVisible && isZeroButton
                                    ? LauncherInstrumentation.GestureScope.INSIDE_TO_OUTSIDE
                                    : LauncherInstrumentation.GestureScope.OUTSIDE_WITH_PILFER;
                    mLauncher.executeAndWaitForEvent(
                            () -> mLauncher.linearGesture(
                                    startX, startY, endX, endY, 20, false, gestureScope),
                            event -> event.getEventType() == TYPE_WINDOW_STATE_CHANGED,
                            () -> "Quick switch gesture didn't change window state", "swiping");
                    break;
                }

                case THREE_BUTTON:
                    // Double press the recents button.
                    UiObject2 recentsButton = mLauncher.waitForNavigationUiObject("recent_apps");
                    if (mLauncher.isTablet()) {
                        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN,
                                LauncherInstrumentation.EVENT_TOUCH_DOWN);
                        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN,
                                LauncherInstrumentation.EVENT_TOUCH_UP);
                    }
                    mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, SQUARE_BUTTON_EVENT);
                    mLauncher.runToState(() -> recentsButton.click(), OVERVIEW_STATE_ORDINAL,
                            "clicking Recents button for the first time");
                    mLauncher.getOverview();
                    if (mLauncher.isTablet()) {
                        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN,
                                LauncherInstrumentation.EVENT_TOUCH_DOWN);
                        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN,
                                LauncherInstrumentation.EVENT_TOUCH_UP);
                    }
                    mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, SQUARE_BUTTON_EVENT);
                    mLauncher.executeAndWaitForEvent(
                            () -> recentsButton.click(),
                            event -> event.getEventType() == TYPE_WINDOW_STATE_CHANGED,
                            () -> "Pressing recents button didn't change window state",
                            "clicking Recents button for the second time");
                    break;
            }
            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, TASK_START_EVENT);
            return;
        }
    }

    protected String getSwipeHeightRequestName() {
        return TestProtocol.REQUEST_BACKGROUND_TO_OVERVIEW_SWIPE_HEIGHT;
    }

    protected int getSwipeStartX() {
        return mLauncher.getRealDisplaySize().x - 1;
    }

    protected int getSwipeStartY() {
        return mLauncher.getRealDisplaySize().y - 1;
    }
}
