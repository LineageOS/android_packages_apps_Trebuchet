/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.touch;

import android.graphics.PointF;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import com.android.launcher3.Utilities.Consumer;

public class TouchEventTranslator {
    private static final String TAG = TouchEventTranslator.class.getSimpleName();

    private final DownState ZERO = new DownState(0, 0.0f, 0.0f);
    private final SparseArray<Pair<PointerProperties[], PointerCoords[]>> mCache =
            new SparseArray<>();
    private final SparseArray<DownState> mDownEvents = new SparseArray<>();
    private final SparseArray<PointF> mFingers = new SparseArray<>();
    private final Consumer<MotionEvent> mListener;

    private class DownState {
        float downX;
        float downY;
        long timeStamp;

        public DownState(long timeStamp, float downX, float downY) {
            this.timeStamp = timeStamp;
            this.downX = downX;
            this.downY = downY;
        }
    }

    public TouchEventTranslator(Consumer<MotionEvent> listener) {
        mListener = listener;
    }

    public void reset() {
        mDownEvents.clear();
        mFingers.clear();
    }

    public float getDownX() {
        return mDownEvents.get(0).downX;
    }

    public float getDownY() {
        return mDownEvents.get(0).downY;
    }

    public void setDownParameters(int idx, MotionEvent e) {
        mDownEvents.append(idx, new DownState(e.getEventTime(), e.getX(idx), e.getY(idx)));
    }

    public void dispatchDownEvents(MotionEvent ev) {
        for (int i = 0; i < ev.getPointerCount() && i < mDownEvents.size(); i++) {
            put(ev.getPointerId(i), i, ev.getX(i), 0.0f, mDownEvents.get(i).timeStamp, ev);
        }
    }

    public void processMotionEvent(MotionEvent ev) {
        int index = ev.getActionIndex();
        float x = ev.getX(index);
        float y = ev.getY(index) - mDownEvents.get(index, ZERO).downY;
        int actionMasked = ev.getActionMasked();
        if (actionMasked != MotionEvent.ACTION_UP) {
            if (actionMasked == MotionEvent.ACTION_MOVE) {
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    position(ev.getPointerId(i), x, y);
                }
                generateEvent(ev.getAction(), ev);
                return;
            } else if (actionMasked == MotionEvent.ACTION_CANCEL) {
                cancel(ev);
                return;
            } else if (actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                int pid = ev.getPointerId(index);
                if (mFingers.get(pid, null) != null) {
                    for (int i = 0; i < ev.getPointerCount(); i++) {
                        position(ev.getPointerId(i), x, y);
                    }
                    generateEvent(ev.getAction(), ev);
                    return;
                }
                put(pid, index, x, y, ev);
                return;
            } else if (actionMasked != MotionEvent.ACTION_POINTER_UP) {
                Log.v(TAG, "Didn't process ");
                return;
            }
        }
        lift(ev.getPointerId(index), index, x, y, ev);
    }

    private TouchEventTranslator put(int id, int index, float x, float y, MotionEvent ev) {
        return put(id, index, x, y, ev.getEventTime(), ev);
    }

    private TouchEventTranslator put(int id, int index, float x, float y, long ms, MotionEvent ev) {
        int action;
        checkFingerExistence(id, false);
        mFingers.put(id, new PointF(x, y));
        int n = mFingers.size();
        if (mCache.get(n) == null) {
            PointerProperties[] properties = new PointerProperties[n];
            PointerCoords[] coords = new PointerCoords[n];
            for (int i = 0; i < n; i++) {
                properties[i] = new PointerProperties();
                coords[i] = new PointerCoords();
            }
            mCache.put(n, new Pair<>(properties, coords));
        }

        boolean isInitialDown = mFingers.size() == 0;
        if (isInitialDown) {
            action = MotionEvent.ACTION_DOWN;
        } else {
            action = MotionEvent.ACTION_POINTER_DOWN | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        }
        generateEvent(action, ms, ev);
        return this;
    }

    public TouchEventTranslator position(int id, float x, float y) {
        checkFingerExistence(id, true);
        mFingers.get(id).set(x, y);
        return this;
    }

    private TouchEventTranslator lift(int id, int index, MotionEvent ev) {
        int action;
        checkFingerExistence(id, true);

        boolean isFinalUp = mFingers.size() == 1;
        if (isFinalUp) {
            action = MotionEvent.ACTION_UP;
        } else {
            action = MotionEvent.ACTION_POINTER_UP | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        }
        generateEvent(action, ev);
        mFingers.remove(id);
        return this;
    }

    private TouchEventTranslator lift(int id, int index, float x, float y, MotionEvent ev) {
        checkFingerExistence(id, true);
        mFingers.get(id).set(x, y);
        return lift(id, index, ev);
    }

    public TouchEventTranslator cancel(MotionEvent ev) {
        generateEvent(MotionEvent.ACTION_CANCEL, ev);
        mFingers.clear();
        return this;
    }

    private void checkFingerExistence(int id, boolean shouldExist) {
        if (shouldExist == (mFingers.get(id, null) == null)) {
            throw new IllegalArgumentException(
                    shouldExist ? "Finger does not exist" : "Finger already exists");
        }
    }

    private void generateEvent(int action, MotionEvent ev) {
        generateEvent(action, ev.getEventTime(), ev);
    }

    private void generateEvent(int action, long ms, MotionEvent ev) {
        Pair<PointerProperties[], PointerCoords[]> state = getFingerState();
        MotionEvent event = MotionEvent.obtain(
                mDownEvents.get(0).timeStamp, ms, action,
                state.first.length, state.first, state.second,
                ev.getMetaState(), ev.getButtonState(), ev.getXPrecision(),
                ev.getYPrecision(), ev.getDeviceId(), ev.getEdgeFlags(),
                ev.getSource(), ev.getFlags());
        if (event.getPointerId(event.getActionIndex()) >= 0) {
            mListener.accept(event);
            event.recycle();
            return;
        }
        throw new IllegalStateException(String.valueOf(event.getActionIndex()) +
                " not found in MotionEvent");
    }

    private Pair<PointerProperties[], PointerCoords[]> getFingerState() {
        int nFingers = mFingers.size();
        Pair<PointerProperties[], PointerCoords[]> result = mCache.get(nFingers);
        PointerProperties[] properties = result.first;
        PointerCoords[] coordinates = result.second;
        for (int i = 0; i < mFingers.size(); i++) {
            int id = mFingers.keyAt(i);
            PointF location = mFingers.get(id);
            PointerProperties property = properties[i];
            property.id = id;
            property.toolType = 1;
            properties[i] = property;
            PointerCoords coordinate = coordinates[i];
            coordinate.x = location.x;
            coordinate.y = location.y;
            coordinate.pressure = 1.0f;
            coordinates[i] = coordinate;
        }
        return mCache.get(nFingers);
    }
}
