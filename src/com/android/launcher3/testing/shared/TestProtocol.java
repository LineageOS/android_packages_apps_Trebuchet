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

package com.android.launcher3.testing.shared;

/**
 * Protocol for custom accessibility events for communication with UI Automation tests.
 */
public final class TestProtocol {
    public static final String STATE_FIELD = "state";
    public static final String SWITCHED_TO_STATE_MESSAGE = "TAPL_SWITCHED_TO_STATE";
    public static final String SCROLL_FINISHED_MESSAGE = "TAPL_SCROLL_FINISHED";
    public static final String PAUSE_DETECTED_MESSAGE = "TAPL_PAUSE_DETECTED";
    public static final String DISMISS_ANIMATION_ENDS_MESSAGE = "TAPL_DISMISS_ANIMATION_ENDS";
    public static final String FOLDER_OPENED_MESSAGE = "TAPL_FOLDER_OPENED";
    public static final int NORMAL_STATE_ORDINAL = 0;
    public static final int SPRING_LOADED_STATE_ORDINAL = 1;
    public static final int OVERVIEW_STATE_ORDINAL = 2;
    public static final int OVERVIEW_MODAL_TASK_STATE_ORDINAL = 3;
    public static final int QUICK_SWITCH_STATE_ORDINAL = 4;
    public static final int ALL_APPS_STATE_ORDINAL = 5;
    public static final int BACKGROUND_APP_STATE_ORDINAL = 6;
    public static final int HINT_STATE_ORDINAL = 7;
    public static final int HINT_STATE_TWO_BUTTON_ORDINAL = 8;
    public static final int OVERVIEW_SPLIT_SELECT_ORDINAL = 9;
    public static final String TAPL_EVENTS_TAG = "TaplEvents";
    public static final String SEQUENCE_MAIN = "Main";
    public static final String SEQUENCE_TIS = "TIS";
    public static final String SEQUENCE_PILFER = "Pilfer";

    public static String stateOrdinalToString(int ordinal) {
        switch (ordinal) {
            case NORMAL_STATE_ORDINAL:
                return "Normal";
            case SPRING_LOADED_STATE_ORDINAL:
                return "SpringLoaded";
            case OVERVIEW_STATE_ORDINAL:
                return "Overview";
            case OVERVIEW_MODAL_TASK_STATE_ORDINAL:
                return "OverviewModal";
            case QUICK_SWITCH_STATE_ORDINAL:
                return "QuickSwitch";
            case ALL_APPS_STATE_ORDINAL:
                return "AllApps";
            case BACKGROUND_APP_STATE_ORDINAL:
                return "Background";
            case HINT_STATE_ORDINAL:
                return "Hint";
            case HINT_STATE_TWO_BUTTON_ORDINAL:
                return "Hint2Button";
            case OVERVIEW_SPLIT_SELECT_ORDINAL:
                return "OverviewSplitSelect";
            default:
                return "Unknown";
        }
    }

    public static final String TEST_INFO_REQUEST_FIELD = "request";
    public static final String TEST_INFO_RESPONSE_FIELD = "response";

    public static final String REQUEST_HOME_TO_OVERVIEW_SWIPE_HEIGHT =
            "home-to-overview-swipe-height";
    public static final String REQUEST_BACKGROUND_TO_OVERVIEW_SWIPE_HEIGHT =
            "background-to-overview-swipe-height";
    public static final String REQUEST_HOME_TO_ALL_APPS_SWIPE_HEIGHT =
            "home-to-all-apps-swipe-height";
    public static final String REQUEST_ICON_HEIGHT =
            "icon-height";
    public static final String REQUEST_IS_LAUNCHER_INITIALIZED = "is-launcher-initialized";
    public static final String REQUEST_FREEZE_APP_LIST = "freeze-app-list";
    public static final String REQUEST_UNFREEZE_APP_LIST = "unfreeze-app-list";
    public static final String REQUEST_ENABLE_MANUAL_TASKBAR_STASHING = "enable-taskbar-stashing";
    public static final String REQUEST_DISABLE_MANUAL_TASKBAR_STASHING = "disable-taskbar-stashing";
    public static final String REQUEST_UNSTASH_TASKBAR_IF_STASHED = "unstash-taskbar-if-stashed";
    public static final String REQUEST_STASHED_TASKBAR_HEIGHT = "stashed-taskbar-height";
    public static final String REQUEST_RECREATE_TASKBAR = "recreate-taskbar";
    public static final String REQUEST_APP_LIST_FREEZE_FLAGS = "app-list-freeze-flags";
    public static final String REQUEST_APPS_LIST_SCROLL_Y = "apps-list-scroll-y";
    public static final String REQUEST_WIDGETS_SCROLL_Y = "widgets-scroll-y";
    public static final String REQUEST_TARGET_INSETS = "target-insets";
    public static final String REQUEST_WINDOW_INSETS = "window-insets";
    public static final String REQUEST_PID = "pid";
    public static final String REQUEST_FORCE_GC = "gc";
    public static final String REQUEST_VIEW_LEAK = "view-leak";
    public static final String REQUEST_RECENT_TASKS_LIST = "recent-tasks-list";
    public static final String REQUEST_START_EVENT_LOGGING = "start-event-logging";
    public static final String REQUEST_GET_TEST_EVENTS = "get-test-events";
    public static final String REQUEST_GET_HAD_NONTEST_EVENTS = "get-had-nontest-events";
    public static final String REQUEST_STOP_EVENT_LOGGING = "stop-event-logging";
    public static final String REQUEST_CLEAR_DATA = "clear-data";
    public static final String REQUEST_USE_TEST_WORKSPACE_LAYOUT = "use-test-workspace-layout";
    public static final String REQUEST_USE_DEFAULT_WORKSPACE_LAYOUT =
            "use-default-workspace-layout";
    public static final String REQUEST_HOTSEAT_ICON_NAMES = "get-hotseat-icon-names";
    public static final String REQUEST_IS_TABLET = "is-tablet";
    public static final String REQUEST_IS_TWO_PANELS = "is-two-panel";
    public static final String REQUEST_START_DRAG_THRESHOLD = "start-drag-threshold";
    public static final String REQUEST_GET_ACTIVITIES_CREATED_COUNT =
            "get-activities-created-count";
    public static final String REQUEST_GET_ACTIVITIES = "get-activities";
    public static final String REQUEST_HAS_TIS = "has-touch-interaction-service";

    public static final String REQUEST_WORKSPACE_CELL_LAYOUT_SIZE = "workspace-cell-layout-size";
    public static final String REQUEST_WORKSPACE_CELL_CENTER = "workspace-cell-center";

    public static final String REQUEST_HOTSEAT_CELL_CENTER = "hotseat-cell-center";

    public static final String REQUEST_GET_FOCUSED_TASK_HEIGHT_FOR_TABLET =
            "get-focused-task-height-for-tablet";
    public static final String REQUEST_GET_GRID_TASK_SIZE_RECT_FOR_TABLET =
            "get-grid-task-size-rect-for-tablet";
    public static final String REQUEST_GET_OVERVIEW_PAGE_SPACING = "get-overview-page-spacing";
    public static final String REQUEST_ENABLE_ROTATION = "enable_rotation";

    public static boolean sDebugTracing = false;
    public static final String REQUEST_ENABLE_DEBUG_TRACING = "enable-debug-tracing";
    public static final String REQUEST_DISABLE_DEBUG_TRACING = "disable-debug-tracing";


    public static boolean sDisableSensorRotation;
    public static final String REQUEST_MOCK_SENSOR_ROTATION = "mock-sensor-rotation";

    public static final String PERMANENT_DIAG_TAG = "TaplTarget";
    public static final String NO_DROP_TARGET = "b/195031154";
    public static final String NULL_INT_SET = "b/200572078";
    public static final String MISSING_PROMISE_ICON = "b/202985412";
    public static final String TASKBAR_IN_APP_STATE = "b/227657604";
    public static final String INCORRECT_INFO_UPDATED = "b/239465630";

    public static final String REQUEST_EMULATE_DISPLAY = "emulate-display";
    public static final String REQUEST_STOP_EMULATE_DISPLAY = "stop-emulate-display";
    public static final String REQUEST_IS_EMULATE_DISPLAY_RUNNING = "is-emulate-display-running";
    public static final String REQUEST_EMULATE_PRINT_DEVICE = "emulate-print-device";
}
