/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3;

import static com.android.launcher3.logging.KeyboardStateManager.KeyboardState.SHOW;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.android.launcher3.views.ActivityContext;


/**
 * The edit text that reports back when the back key has been pressed.
 * Note: AppCompatEditText doesn't fully support #displayCompletions and #onCommitCompletion
 */
public class ExtendedEditText extends EditText {
    private boolean mForceDisableSuggestions = false;

    /**
     * Implemented by listeners of the back key.
     */
    public interface OnBackKeyListener {
        boolean onBackKey();
    }

    private OnBackKeyListener mBackKeyListener;

    public ExtendedEditText(Context context) {
        // ctor chaining breaks the touch handling
        super(context);
    }

    public ExtendedEditText(Context context, AttributeSet attrs) {
        // ctor chaining breaks the touch handling
        super(context, attrs);
    }

    public ExtendedEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnBackKeyListener(OnBackKeyListener listener) {
        mBackKeyListener = listener;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        // If this is a back key, propagate the key back to the listener
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            if (TextUtils.isEmpty(getText())) {
                hideKeyboard();
            }
            if (mBackKeyListener != null) {
                return mBackKeyListener.onBackKey();
            }
            return false;
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        // We don't want this view to interfere with Launcher own drag and drop.
        return false;
    }

    public void showKeyboard() {
        onKeyboardShown();
        showSoftInput();
    }

    public void hideKeyboard() {
        ActivityContext.lookupContext(getContext()).hideKeyboard();
        clearFocus();
    }

    protected void onKeyboardShown() {
        ActivityContext.lookupContext(getContext()).getStatsLogManager()
                .keyboardStateManager().setKeyboardState(SHOW);
    }

    private boolean showSoftInput() {
        return requestFocus() &&
                getContext().getSystemService(InputMethodManager.class)
                    .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
    }

    public void dispatchBackKey() {
        hideKeyboard();
        if (mBackKeyListener != null) {
            mBackKeyListener.onBackKey();
        }
    }

    /**
     * Set to true when you want isSuggestionsEnabled to return false.
     * Use this to disable the red underlines that appear under typos when suggestions is enabled.
     */
    public void forceDisableSuggestions(boolean forceDisableSuggestions) {
        mForceDisableSuggestions = forceDisableSuggestions;
    }

    @Override
    public boolean isSuggestionsEnabled() {
        return !mForceDisableSuggestions && super.isSuggestionsEnabled();
    }

    public void reset() {
        if (!TextUtils.isEmpty(getText())) {
            setText("");
        }
    }
}
