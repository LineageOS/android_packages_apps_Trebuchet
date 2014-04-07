package com.android.launcher3;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;
import com.android.launcher3.settings.SettingsProvider;


public class NumberPreference extends DialogPreference {

    private int m_iMin = 0;
    private int m_iMax = 0;
    private boolean m_bDynamicMax = false;
    private String m_sDynamicMaxKey = "";

    private NumberPicker mNumberPicker = null;

    public NumberPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NumberPreference, 0, 0);
        m_iMin = a.getInt(R.styleable.NumberPreference_min, 1);
        m_iMax = a.getInt(R.styleable.NumberPreference_max, 3);
        if(m_iMin < 0 || m_iMin > m_iMax) {
            throw new IllegalArgumentException();
        }

        m_bDynamicMax = a.getBoolean(R.styleable.NumberPreference_dynamicMax, false);
        if (m_bDynamicMax) {
            m_sDynamicMaxKey = a.getString(R.styleable.NumberPreference_dynamicMaxKey);
            if ((m_sDynamicMaxKey != null) && !m_sDynamicMaxKey.isEmpty()) {
                int iDynamicMax = SettingsProvider.getIntCustomDefault(context, m_sDynamicMaxKey, 0);
                if (iDynamicMax > 0) {
                    m_iMax = iDynamicMax;
                }
            }
        }

        a.recycle();
    }

    public void setMax(int iNewMax) {
        if (this.m_bDynamicMax) {
            if (iNewMax > 0) {
                this.m_iMax = iNewMax;
                if (this.mNumberPicker != null) {
                    this.mNumberPicker.invalidate();
                }
            }
            else {
                throw new IllegalArgumentException();
            }
        }
    }

    @Override
    protected View onCreateDialogView() {
        mNumberPicker = new android.widget.NumberPicker(getContext());
        return mNumberPicker;
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        builder.setTitle(getTitle()).setCancelable(true);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        if (m_bDynamicMax) {
            if ((m_sDynamicMaxKey != null) && !m_sDynamicMaxKey.isEmpty()) {
                int iDynamicMax = SettingsProvider.getIntCustomDefault(getContext(), m_sDynamicMaxKey, 0);
                if (iDynamicMax > 0) {
                    m_iMax = iDynamicMax;
                }
            }
        }

        NumberPicker picker = (NumberPicker)view;
        picker.setMinValue(m_iMin);
        picker.setMaxValue(m_iMax);
        picker.setValue(getPersistedInt(m_iMin));
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if(positiveResult) {
            persistInt(mNumberPicker.getValue());
        }
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        int value = m_iMin;
        if (restorePersistedValue) {
            value = getPersistedInt(1);
        } else {
            Integer defVal = (Integer)defaultValue;
            if (defVal != null) {
                value = defVal;
            }
        }
        if(value < m_iMin) {
            value = m_iMin;
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInteger(index, 1);
    }

}

