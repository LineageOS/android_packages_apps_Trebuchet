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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.system.Os;
import android.view.View;

import androidx.annotation.Keep;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Class to handle requests from tests, including debug ones.
 */
public class DebugTestInformationHandler extends TestInformationHandler {
    private static LinkedList sLeaks;
    private static Collection<String> sEvents;
    private static Application.ActivityLifecycleCallbacks sActivityLifecycleCallbacks;
    private static final Map<Activity, Boolean> sActivities =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static int sActivitiesCreatedCount = 0;

    public DebugTestInformationHandler(Context context) {
        init(context);
        if (sActivityLifecycleCallbacks == null) {
            sActivityLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle bundle) {
                    sActivities.put(activity, true);
                    ++sActivitiesCreatedCount;
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityResumed(Activity activity) {
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                }
            };
            ((Application) context.getApplicationContext())
                    .registerActivityLifecycleCallbacks(sActivityLifecycleCallbacks);
        }
    }

    private static void runGcAndFinalizersSync() {
        Runtime.getRuntime().gc();
        Runtime.getRuntime().runFinalization();

        final CountDownLatch fence = new CountDownLatch(1);
        createFinalizationObserver(fence);
        try {
            do {
                Runtime.getRuntime().gc();
                Runtime.getRuntime().runFinalization();
            } while (!fence.await(100, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    // Create the observer in the scope of a method to minimize the chance that
    // it remains live in a DEX/machine register at the point of the fence guard.
    // This must be kept to avoid R8 inlining it.
    @Keep
    private static void createFinalizationObserver(CountDownLatch fence) {
        new Object() {
            @Override
            protected void finalize() throws Throwable {
                try {
                    fence.countDown();
                } finally {
                    super.finalize();
                }
            }
        };
    }

    @Override
    public Bundle call(String method, String arg) {
        final Bundle response = new Bundle();
        switch (method) {
            case TestProtocol.REQUEST_APP_LIST_FREEZE_FLAGS: {
                return getLauncherUIProperty(Bundle::putInt,
                        l -> l.getAppsView().getAppsStore().getDeferUpdatesFlags());
            }

            case TestProtocol.REQUEST_ENABLE_DEBUG_TRACING:
                TestProtocol.sDebugTracing = true;
                return response;

            case TestProtocol.REQUEST_DISABLE_DEBUG_TRACING:
                TestProtocol.sDebugTracing = false;
                return response;

            case TestProtocol.REQUEST_PID: {
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, Os.getpid());
                return response;
            }

            case TestProtocol.REQUEST_FORCE_GC: {
                runGcAndFinalizersSync();
                return response;
            }

            case TestProtocol.REQUEST_VIEW_LEAK: {
                if (sLeaks == null) sLeaks = new LinkedList();
                sLeaks.add(new View(mContext));
                sLeaks.add(new View(mContext));
                return response;
            }

            case TestProtocol.REQUEST_START_EVENT_LOGGING: {
                sEvents = new ArrayList<>();
                TestLogging.setEventConsumer(
                        (sequence, event) -> {
                            final Collection<String> events = sEvents;
                            if (events != null) {
                                synchronized (events) {
                                    events.add(sequence + '/' + event);
                                }
                            }
                        });
                return response;
            }

            case TestProtocol.REQUEST_STOP_EVENT_LOGGING: {
                TestLogging.setEventConsumer(null);
                sEvents = null;
                return response;
            }

            case TestProtocol.REQUEST_GET_TEST_EVENTS: {
                if (sEvents == null) {
                    // sEvents can be null if Launcher died and restarted after
                    // REQUEST_START_EVENT_LOGGING.
                    return response;
                }

                synchronized (sEvents) {
                    response.putStringArrayList(
                            TestProtocol.TEST_INFO_RESPONSE_FIELD, new ArrayList<>(sEvents));
                }
                return response;
            }

            case TestProtocol.REQUEST_CLEAR_DATA: {
                final long identity = Binder.clearCallingIdentity();
                try {
                    LauncherSettings.Settings.call(mContext.getContentResolver(),
                            LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
                    MAIN_EXECUTOR.submit(() ->
                            LauncherAppState.getInstance(mContext).getModel().forceReload());
                    return response;
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
            }

            case TestProtocol.REQUEST_GET_ACTIVITIES_CREATED_COUNT: {
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, sActivitiesCreatedCount);
                return response;
            }

            case TestProtocol.REQUEST_GET_ACTIVITIES: {
                response.putStringArray(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        sActivities.keySet().stream().map(
                                a -> a.getClass().getSimpleName() + " ("
                                        + (a.isDestroyed() ? "destroyed" : "current") + ")")
                                .toArray(String[]::new));
                return response;
            }

            default:
                return super.call(method, arg);
        }
    }
}
