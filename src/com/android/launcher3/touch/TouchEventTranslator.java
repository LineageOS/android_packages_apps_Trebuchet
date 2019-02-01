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
    private static final boolean DEBUG = false;
    private static final String TAG = "TouchEventTranslator";
    private final DownState ZERO = new DownState(0, 0.0f, 0.0f);
    private final SparseArray<Pair<PointerProperties[], PointerCoords[]>> mCache = new SparseArray<>();
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
        int i = 0;
        while (i < ev.getPointerCount() && i < mDownEvents.size()) {
            put(ev.getPointerId(i), i, ev.getX(i), 0.0f, mDownEvents.get(i).timeStamp, ev);
            i++;
        }
    }

    public void processMotionEvent(MotionEvent ev) {
        int index = ev.getActionIndex();
        float x = ev.getX(index);
        float y = ev.getY(index) - mDownEvents.get(index, ZERO).downY;
        int actionMasked = ev.getActionMasked();
        if (actionMasked != 1) {
            if (actionMasked == 2) {
                for (actionMasked = 0; actionMasked < ev.getPointerCount(); actionMasked++) {
                    position(ev.getPointerId(actionMasked), x, y);
                }
                generateEvent(ev.getAction(), ev);
                return;
            } else if (actionMasked == 3) {
                cancel(ev);
                return;
            } else if (actionMasked == 5) {
                int pid = ev.getPointerId(index);
                if (mFingers.get(pid, null) != null) {
                    for (actionMasked = 0; actionMasked < ev.getPointerCount(); actionMasked++) {
                        position(ev.getPointerId(actionMasked), x, y);
                    }
                    generateEvent(ev.getAction(), ev);
                    return;
                }
                put(pid, index, x, y, ev);
                return;
            } else if (actionMasked != 6) {
                String str = TAG;
                Log.v(str, "Didn't process ");
                printSamples(str, ev);
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
        boolean isInitialDown = false;
        checkFingerExistence(id, false);
        if (mFingers.size() == 0) {
            isInitialDown = true;
        }
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
        if (isInitialDown) {
            action = 0;
        } else {
            action = 5 | (index << 8);
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
        boolean isFinalUp = true;
        checkFingerExistence(id, true);
        if (mFingers.size() != 1) {
            isFinalUp = false;
        }
        if (isFinalUp) {
            action = 1;
        } else {
            action = 6 | (index << 8);
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
        generateEvent(3, ev);
        mFingers.clear();
        return this;
    }

    private void checkFingerExistence(int id, boolean shouldExist) {
        if (shouldExist == (mFingers.get(id, null) == null)) {
            throw new IllegalArgumentException(shouldExist ? "Finger does not exist" : "Finger already exists");
        }
    }

    public void printSamples(String msg, MotionEvent ev) {
        System.out.printf("%s %s", msg, MotionEvent.actionToString(ev.getActionMasked()));
        int pointerCount = ev.getPointerCount();
        System.out.printf("#%d/%d", ev.getActionIndex(), pointerCount);
        System.out.printf(" t=%d:", ev.getEventTime());
        for (int p = 0; p < pointerCount; p++) {
            System.out.printf("  id=%d: (%f,%f)", ev.getPointerId(p), ev.getX(p), ev.getY(p));
        }
        System.out.println();
    }

    private void generateEvent(int action, MotionEvent ev) {
        generateEvent(action, ev.getEventTime(), ev);
    }

    private void generateEvent(int action, long ms, MotionEvent ev) {
        Pair<PointerProperties[], PointerCoords[]> state = getFingerState();
        MotionEvent event = MotionEvent.obtain(mDownEvents.get(0).timeStamp, ms, action, state.first.length, state.first, state.second, ev.getMetaState(), ev.getButtonState(), ev.getXPrecision(), ev.getYPrecision(), ev.getDeviceId(), ev.getEdgeFlags(), ev.getSource(), ev.getFlags());
        if (event.getPointerId(event.getActionIndex()) >= 0) {
            mListener.accept(event);
            event.recycle();
            return;
        }
        printSamples("TouchEventTranslatorgenerateEvent", event);
        String stringBuilder = String.valueOf(event.getActionIndex()) +
                " not found in MotionEvent";
        throw new IllegalStateException(stringBuilder);
    }

    private Pair<PointerProperties[], PointerCoords[]> getFingerState() {
        int nFingers = mFingers.size();
        Pair<PointerProperties[], PointerCoords[]> result = mCache.get(nFingers);
        PointerProperties[] properties = result.first;
        PointerCoords[] coordinates = result.second;
        int index = 0;
        for (int i = 0; i < mFingers.size(); i++) {
            int id = mFingers.keyAt(i);
            PointF location = (PointF) mFingers.get(id);
            PointerProperties property = properties[i];
            property.id = id;
            property.toolType = 1;
            properties[index] = property;
            PointerCoords coordinate = coordinates[i];
            coordinate.x = location.x;
            coordinate.y = location.y;
            coordinate.pressure = 1.0f;
            coordinates[index] = coordinate;
            index++;
        }
        return mCache.get(nFingers);
    }
}
