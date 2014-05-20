package com.android.launcher3;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.android.launcher3.R;
import java.util.List;

public class LockPatternActivity extends Activity {
    public static final String ACTION_CREATE_PATTERN = "create_pattern";
    public static final String ACTION_COMPARE_PATTERN = "compare_pattern";
    public static final String EXTRA_PATTERN = "extra_pattern";

    private static final int MIN_PATTERN_SIZE = 4;
    private static final int MAX_PATTERN_RETRY = 5;
    private static final int PATTERN_CLEAR_TIMEOUT_MS = 2000;

    LockPatternView mLockPatternView;

    TextView mPatternLockHeader;
    Button mCancel;
    Button mContinue;
    String mPattern;

    int mRetry = 0;

    boolean mCreate;

    Runnable mCancelPatternRunnable = new Runnable() {
        public void run() {
            mLockPatternView.clearPattern();

            //Update UI Components
            if (mCreate) {
                if (mContinue.getText().equals(getResources().getString(R.string.cmd_confirm))){
                    mPatternLockHeader.setText(getResources().getString(
                            R.string.msg_redraw_pattern_to_confirm));
                } else {
                    mPatternLockHeader.setText(getResources().getString(
                            R.string.draw_an_unlock_pattern));
                }
                mContinue.setEnabled(false);
            } else {
                mPatternLockHeader.setText(getResources().getString(
                        R.string.draw_pattern_to_unlock));
            }
        }
    };

    View.OnClickListener mCancelOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            setResult(RESULT_CANCELED);
            finish();
        }
    };

    View.OnClickListener mContinueOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Button btn = (Button) v;
            if (btn.getText().equals(getResources().getString(R.string.cmd_confirm))) {
                Intent returnIntent = new Intent();
                returnIntent.putExtra(EXTRA_PATTERN, mPattern);
                setResult(RESULT_OK, returnIntent);
                finish();
            } else {
                mLockPatternView.clearPattern();

                mPatternLockHeader.setText(getResources().getString(
                        R.string.msg_redraw_pattern_to_confirm));
                btn.setText(getResources().getString(R.string.cmd_confirm));
                btn.setEnabled(false);
            }
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.patternlock);

        Intent intent = getIntent();
        mCreate = intent.getAction().equals(ACTION_CREATE_PATTERN) ? true : false;
        mPattern = "";

        if (!mCreate) {
            mPattern = intent.getStringExtra(EXTRA_PATTERN);
            mPattern = mPattern == null ? "" : mPattern;
        }

        mPatternLockHeader = (TextView) findViewById(R.id.pattern_lock_header);
        mCancel = (Button) findViewById(R.id.pattern_lock_btn_cancel);
        mCancel.setOnClickListener(mCancelOnClickListener);
        mContinue = (Button) findViewById(R.id.pattern_lock_btn_continue);
        mContinue.setOnClickListener(mContinueOnClickListener);
        if (mCreate) {
            mContinue.setEnabled(false);
            mPatternLockHeader.setText(getResources().getString(R.string.draw_an_unlock_pattern));
        } else {
            mCancel.setVisibility(View.GONE);
            mContinue.setVisibility(View.GONE);
            mPatternLockHeader.setText(getResources().getString(R.string.draw_pattern_to_unlock));
        }

        mLockPatternView = (LockPatternView) findViewById(R.id.lock_pattern_view);

        //Setup Pattern Lock View
        mLockPatternView.setSaveEnabled(false);
        mLockPatternView.setFocusable(false);
        mLockPatternView.setOnPatternListener(new UnlockPatternListener());

    }

    private class UnlockPatternListener implements LockPatternView.OnPatternListener {

        public void onPatternStart() {
            mLockPatternView.removeCallbacks(mCancelPatternRunnable);

            mPatternLockHeader.setText(getResources().getText(
                    R.string.msg_release_finger_when_done));
            mContinue.setEnabled(false);
        }

        public void onPatternCleared() {
        }

        public void onPatternDetected(List<LockPatternView.Cell> pattern) {
            //Check inserted Pattern
            if (mCreate) {
                if (pattern.size() < MIN_PATTERN_SIZE) {
                    mPatternLockHeader.setText(getResources().getString(
                            R.string.msg_connect_4dots));

                    mLockPatternView.setDisplayMode(LockPatternView.DisplayMode.Wrong);
                    mLockPatternView.postDelayed(mCancelPatternRunnable, PATTERN_CLEAR_TIMEOUT_MS);
                    return;
                }

                if (mContinue.getText().equals(getResources().getString(R.string.cmd_confirm))) {
                    if (mPattern.equals(patternToString(pattern))) {
                        mContinue.setText(getResources().getString(R.string.cmd_confirm));
                        mContinue.setEnabled(true);
                        mPatternLockHeader.setText(getResources().getString(
                                R.string.msg_your_new_unlock_pattern));
                    } else {
                        mContinue.setEnabled(false);

                        mLockPatternView.setDisplayMode(LockPatternView.DisplayMode.Wrong);
                        mLockPatternView.postDelayed(mCancelPatternRunnable,
                                PATTERN_CLEAR_TIMEOUT_MS);
                    }
                } else {
                    //Save pattern, user needs to redraw to confirm
                    mPattern = patternToString(pattern);

                    mPatternLockHeader.setText(getResources().getString(
                            R.string.msg_pattern_recorded));
                    mContinue.setEnabled(true);
                }
            } else {
                //Check against existing pattern
                if (mPattern.equals(patternToString(pattern))) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    mRetry++;
                    mPatternLockHeader.setText(getResources().getString(
                            R.string.msg_try_again));

                    mLockPatternView.setDisplayMode(LockPatternView.DisplayMode.Wrong);
                    mLockPatternView.postDelayed(mCancelPatternRunnable, PATTERN_CLEAR_TIMEOUT_MS);

                    if (mRetry >= MAX_PATTERN_RETRY) {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                }
            }
        }


        public void onPatternCellAdded(List<LockPatternView.Cell> pattern) {
        }
    }

    public static String patternToString(List<LockPatternView.Cell> pattern) {
        if (pattern == null) {
            return "";
        }
        final int patternSize = pattern.size();
        byte[] res = new byte[patternSize];
        for (int i = 0; i < patternSize; i++) {
            LockPatternView.Cell cell = pattern.get(i);
            res[i] = (byte) (cell.getRow() * 3 + cell.getColumn());
        }
        return new String(res);
    }
}
