package com.android.launcher3.taskbar;

import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_A11Y;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_BACK;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_HOME;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_IME_SWITCH;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_RECENTS;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.SCREEN_PIN_LONG_PRESS_THRESHOLD;
import static com.android.quickstep.OverviewCommandHelper.TYPE_HOME;
import static com.android.quickstep.OverviewCommandHelper.TYPE_TOGGLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SCREEN_PINNING;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;

import androidx.test.runner.AndroidJUnit4;

import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TouchInteractionService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class TaskbarNavButtonControllerTest {

    private final static int DISPLAY_ID = 2;

    @Mock
    SystemUiProxy mockSystemUiProxy;
    @Mock
    TouchInteractionService mockService;
    @Mock
    OverviewCommandHelper mockCommandHelper;
    @Mock
    Handler mockHandler;

    private TaskbarNavButtonController mNavButtonController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockService.getDisplayId()).thenReturn(DISPLAY_ID);
        when(mockService.getOverviewCommandHelper()).thenReturn(mockCommandHelper);
        mNavButtonController = new TaskbarNavButtonController(mockService,
                mockSystemUiProxy, mockHandler);
    }

    @Test
    public void testPressBack() {
        mNavButtonController.onButtonClick(BUTTON_BACK);
        verify(mockSystemUiProxy, times(1)).onBackPressed();
    }

    @Test
    public void testPressImeSwitcher() {
        mNavButtonController.onButtonClick(BUTTON_IME_SWITCH);
        verify(mockSystemUiProxy, times(1)).onImeSwitcherPressed();
    }

    @Test
    public void testPressA11yShortClick() {
        mNavButtonController.onButtonClick(BUTTON_A11Y);
        verify(mockSystemUiProxy, times(1))
                .notifyAccessibilityButtonClicked(DISPLAY_ID);
    }

    @Test
    public void testPressA11yLongClick() {
        mNavButtonController.onButtonLongClick(BUTTON_A11Y);
        verify(mockSystemUiProxy, times(1)).notifyAccessibilityButtonLongClicked();
    }

    @Test
    public void testLongPressHome() {
        mNavButtonController.onButtonLongClick(BUTTON_HOME);
        verify(mockSystemUiProxy, times(1)).startAssistant(any());
    }

    @Test
    public void testPressHome() {
        mNavButtonController.onButtonClick(BUTTON_HOME);
        verify(mockCommandHelper, times(1)).addCommand(TYPE_HOME);
    }

    @Test
    public void testPressRecents() {
        mNavButtonController.onButtonClick(BUTTON_RECENTS);
        verify(mockCommandHelper, times(1)).addCommand(TYPE_TOGGLE);
    }

    @Test
    public void testPressRecentsWithScreenPinned() {
        mNavButtonController.updateSysuiFlags(SYSUI_STATE_SCREEN_PINNING);
        mNavButtonController.onButtonClick(BUTTON_RECENTS);
        verify(mockCommandHelper, times(0)).addCommand(TYPE_TOGGLE);
    }

    @Test
    public void testLongPressBackRecentsNotPinned() {
        mNavButtonController.onButtonLongClick(BUTTON_RECENTS);
        mNavButtonController.onButtonLongClick(BUTTON_BACK);
        verify(mockSystemUiProxy, times(0)).stopScreenPinning();
    }

    @Test
    public void testLongPressBackRecentsPinned() {
        mNavButtonController.updateSysuiFlags(SYSUI_STATE_SCREEN_PINNING);
        mNavButtonController.onButtonLongClick(BUTTON_RECENTS);
        mNavButtonController.onButtonLongClick(BUTTON_BACK);
        verify(mockSystemUiProxy, times(1)).stopScreenPinning();
    }

    @Test
    public void testLongPressBackRecentsTooLongPinned() {
        mNavButtonController.updateSysuiFlags(SYSUI_STATE_SCREEN_PINNING);
        mNavButtonController.onButtonLongClick(BUTTON_RECENTS);
        try {
            Thread.sleep(SCREEN_PIN_LONG_PRESS_THRESHOLD + 5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mNavButtonController.onButtonLongClick(BUTTON_BACK);
        verify(mockSystemUiProxy, times(0)).stopScreenPinning();
    }

    @Test
    public void testLongPressBackRecentsMultipleAttemptPinned() {
        mNavButtonController.updateSysuiFlags(SYSUI_STATE_SCREEN_PINNING);
        mNavButtonController.onButtonLongClick(BUTTON_RECENTS);
        try {
            Thread.sleep(SCREEN_PIN_LONG_PRESS_THRESHOLD + 5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mNavButtonController.onButtonLongClick(BUTTON_BACK);
        verify(mockSystemUiProxy, times(0)).stopScreenPinning();

        // Try again w/in threshold
        mNavButtonController.onButtonLongClick(BUTTON_RECENTS);
        mNavButtonController.onButtonLongClick(BUTTON_BACK);
        verify(mockSystemUiProxy, times(1)).stopScreenPinning();
    }

    @Test
    public void testLongPressHomeScreenPinned() {
        mNavButtonController.updateSysuiFlags(SYSUI_STATE_SCREEN_PINNING);
        mNavButtonController.onButtonLongClick(BUTTON_HOME);
        verify(mockSystemUiProxy, times(0)).startAssistant(any());
    }
}
