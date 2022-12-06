/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.launcher3.testing;

import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.android.launcher3.Utilities;
import com.android.launcher3.testing.shared.TestProtocol;

import java.util.function.BiConsumer;

public final class TestLogging {
    private static BiConsumer<String, String> sEventConsumer;
    public static boolean sHadEventsNotFromTest;

    private static void recordEventSlow(String sequence, String event) {
        Log.d(TestProtocol.TAPL_EVENTS_TAG, sequence + " / " + event);
        final BiConsumer<String, String> eventConsumer = sEventConsumer;
        if (eventConsumer != null) {
            eventConsumer.accept(sequence, event);
        }
    }

    public static void recordEvent(String sequence, String event) {
        if (Utilities.IS_RUNNING_IN_TEST_HARNESS) {
            recordEventSlow(sequence, event);
        }
    }

    public static void recordEvent(String sequence, String message, Object parameter) {
        if (Utilities.IS_RUNNING_IN_TEST_HARNESS) {
            recordEventSlow(sequence, message + ": " + parameter);
        }
    }

    private static void registerEventNotFromTest(InputEvent event) {
        if (!sHadEventsNotFromTest && event.getDeviceId() != -1) {
            sHadEventsNotFromTest = true;
            Log.d(TestProtocol.PERMANENT_DIAG_TAG, "First event not from test: " + event);
        }
    }

    public static void recordKeyEvent(String sequence, String message, KeyEvent event) {
        if (Utilities.IS_RUNNING_IN_TEST_HARNESS) {
            recordEventSlow(sequence, message + ": " + event);
            registerEventNotFromTest(event);
        }
    }

    public static void recordMotionEvent(String sequence, String message, MotionEvent event) {
        if (Utilities.IS_RUNNING_IN_TEST_HARNESS && event.getAction() != MotionEvent.ACTION_MOVE) {
            recordEventSlow(sequence, message + ": " + event);
            registerEventNotFromTest(event);
        }
    }

    static void setEventConsumer(BiConsumer<String, String> consumer) {
        sEventConsumer = consumer;
    }
}
