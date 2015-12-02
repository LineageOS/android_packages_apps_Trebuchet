/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.TextView;
import com.android.launcher3.settings.SettingsProvider;

public class DynamicGridSizeFragment extends Fragment
        implements NumberPicker.OnValueChangeListener, Dialog.OnDismissListener {
    public static final String DYNAMIC_GRID_SIZE_FRAGMENT = "DynamicGridSizeFragment";

    public static final int MIN_DYNAMIC_GRID_ROWS = 2;
    public static final int MIN_DYNAMIC_GRID_COLUMNS = 3;

    GridSizeView mDynamicGrid;

    ListView mListView;
    View mCurrentSelection;
    GridSizeAdapter mAdapter;
    InvariantDeviceProfile.GridSize mCurrentSize;

    Dialog mDialog;

    int mCustomGridRows = 0;
    int mCustomGridColumns = 0;

    View.OnClickListener mSettingsItemListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mCurrentSize = InvariantDeviceProfile.GridSize.getModeForValue((Integer) v.getTag());

            setCleared(mCurrentSelection);
            setSelected(v);
            mCurrentSelection = v;

            if (mCurrentSize == InvariantDeviceProfile.GridSize.Custom) {
                showNumberPicker();
            }

            ((GridSizeAdapter) mListView.getAdapter()).notifyDataSetChanged();

            mAdapter.notifyDataSetInvalidated();
            updateGridMetrics();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dynamic_grid_size_screen, container, false);
        mDynamicGrid = (GridSizeView) v.findViewById(R.id.dynamic_grid_size_image);
        mListView = (ListView) v.findViewById(R.id.dynamic_grid_list);

        Launcher launcher = (Launcher) getActivity();
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)
                mListView.getLayoutParams();
        lp.bottomMargin = ((FrameLayout.LayoutParams) launcher.getOverviewPanel()
                .findViewById(R.id.settings_container).getLayoutParams()).bottomMargin;
        mListView.setLayoutParams(lp);

        LinearLayout titleLayout = (LinearLayout) v.findViewById(R.id.dynamic_grid_title);
        titleLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSize();
            }
        });

        mCurrentSize = InvariantDeviceProfile.GridSize.getModeForValue(
                SettingsProvider.getIntCustomDefault(getActivity(),
                SettingsProvider.SETTINGS_UI_DYNAMIC_GRID_SIZE, 0));

        InvariantDeviceProfile grid = getInvariantDeviceProfile();
        mCustomGridRows = grid.numRows;
        mCustomGridColumns = grid.numColumns;

        updateGridMetrics();

        Resources res = getResources();
        int[] valueResIds = {
            R.string.grid_size_comfortable,
            R.string.grid_size_cozy,
            R.string.grid_size_condensed,
            R.string.grid_size_custom
        };
        mAdapter = new GridSizeAdapter(getActivity(), valueResIds);
        mListView.setAdapter(mAdapter);

        // RTL
        ImageView navPrev = (ImageView) v.findViewById(R.id.nav_prev);
        Configuration config = getResources().getConfiguration();
        if (config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            navPrev.setImageResource(R.drawable.ic_navigation_next);
        }

        return v;
    }

    private void updateGridMetrics() {
        if (mCurrentSize == InvariantDeviceProfile.GridSize.Custom) {
            mDynamicGrid.setMetrics(mCustomGridRows, mCustomGridColumns);
        } else {
            InvariantDeviceProfile grid = getInvariantDeviceProfile();
            mDynamicGrid.setMetrics(grid.numRowsBase + mCurrentSize.getValue(),
                    grid.numColumnsBase + mCurrentSize.getValue());
        }
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        if (enter) {
            DisplayMetrics displaymetrics = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
            int width = displaymetrics.widthPixels;
            Configuration config = getResources().getConfiguration();
            final ObjectAnimator anim;
            if (config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                anim = ObjectAnimator.ofFloat(this, "translationX", -width, 0);
            } else {
                anim = ObjectAnimator.ofFloat(this, "translationX", width, 0);
            }

            final View darkPanel = ((Launcher) getActivity()).getDarkPanel();
            darkPanel.setVisibility(View.VISIBLE);
            ObjectAnimator anim2 = ObjectAnimator.ofFloat(darkPanel, "alpha", 0.0f, 0.3f);
            anim2.start();

            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd (Animator animation) {
                    darkPanel.setVisibility(View.GONE);
                }
            });

            return anim;
        } else {
            return super.onCreateAnimator(transit, enter, nextAnim);
        }
    }

    public void setSize() {
        ((Launcher) getActivity()).setDynamicGridSize(mCurrentSize);
    }

    private void setSelected(View v) {
        v.setBackgroundColor(Color.WHITE);
        TextView t = (TextView) v.findViewById(R.id.item_name);
        t.setTextColor(getResources().getColor(R.color.settings_bg_color));
    }

    private void setCleared(View v) {
        v.setBackgroundColor(getResources().getColor(R.color.settings_bg_color));
        TextView t = (TextView) v.findViewById(R.id.item_name);
        t.setTextColor(Color.WHITE);
    }

    private void showNumberPicker() {
        mDialog = new Dialog(getActivity());
        mDialog.setTitle(getResources().getString(
                R.string.preferences_interface_homescreen_custom));
        mDialog.setContentView(R.layout.custom_grid_size_dialog);

        NumberPicker nPRows = (NumberPicker) mDialog.findViewById(R.id.custom_rows);
        NumberPicker nPColumns = (NumberPicker) mDialog.findViewById(R.id.custom_columns);

        InvariantDeviceProfile grid = getInvariantDeviceProfile();
        int rows = grid.numRowsBase;
        int columns = grid.numColumnsBase;

        nPRows.setMinValue(Math.max(MIN_DYNAMIC_GRID_ROWS, rows - InvariantDeviceProfile.GRID_SIZE_MIN));
        nPRows.setMaxValue(rows + InvariantDeviceProfile.GRID_SIZE_MAX);
        nPRows.setValue(mCustomGridRows);
        nPRows.setWrapSelectorWheel(false);
        nPRows.setOnValueChangedListener(this);
        nPRows.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        nPColumns.setMinValue(Math.max(MIN_DYNAMIC_GRID_COLUMNS,
                columns - InvariantDeviceProfile.GRID_SIZE_MIN));
        nPColumns.setMaxValue(columns + InvariantDeviceProfile.GRID_SIZE_MAX);
        nPColumns.setValue(mCustomGridColumns);
        nPColumns.setWrapSelectorWheel(false);
        nPColumns.setOnValueChangedListener(this);
        nPColumns.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        Button button = (Button) mDialog.findViewById(R.id.dialog_confirm_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDialog != null) {
                    mDialog.dismiss();
                }
            }
        });

        mDialog.setOnDismissListener(this);
        mDialog.show();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    @Override
    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        if (picker.getId() == R.id.custom_rows) {
            mCustomGridRows = newVal;
        } else if (picker.getId() == R.id.custom_columns) {
            mCustomGridColumns = newVal;
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        SettingsProvider.putInt(getActivity(),
                SettingsProvider.SETTINGS_UI_HOMESCREEN_ROWS, mCustomGridRows);
        SettingsProvider.putInt(getActivity(),
                SettingsProvider.SETTINGS_UI_HOMESCREEN_COLUMNS, mCustomGridColumns);

        mAdapter.notifyDataSetInvalidated();
        mDynamicGrid.setMetrics(mCustomGridRows, mCustomGridColumns);
    }

    private class GridSizeAdapter extends BaseAdapter {
        Context mContext;
        int[] mTitleResIds;

        public GridSizeAdapter(Context context, int[] resIds) {
            mContext = context;
            mTitleResIds = resIds;
        }

        @Override
        public int getCount() {
            return mTitleResIds.length;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public Object getItem(int position) {
            return mContext.getString(mTitleResIds[position]);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater)
                    mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.settings_pane_list_item, parent, false);
            }

            TextView textView = (TextView) convertView.findViewById(R.id.item_name);

            // RTL
            Configuration config = getResources().getConfiguration();
            if (config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                textView.setGravity(Gravity.RIGHT);
            }

            // Set selected state
            if (position == mCurrentSize.getValue()) {
                if (mCurrentSelection != null) {
                    setCleared(mCurrentSelection);
                }
                mCurrentSelection = convertView;
                setSelected(mCurrentSelection);
            }

            if (position == InvariantDeviceProfile.GridSize.Custom.getValue()) {
                InvariantDeviceProfile grid = getInvariantDeviceProfile();

                int rows = SettingsProvider.getIntCustomDefault(getActivity(),
                        SettingsProvider.SETTINGS_UI_HOMESCREEN_ROWS, grid.numRowsBase);
                int columns = SettingsProvider.getIntCustomDefault(getActivity(),
                        SettingsProvider.SETTINGS_UI_HOMESCREEN_COLUMNS, grid.numColumnsBase);
                textView.setText(mContext.getString(mTitleResIds[position], rows, columns));
            } else {
                textView.setText(mTitleResIds[position]);
            }

            convertView.setOnClickListener(mSettingsItemListener);
            convertView.setTag(position);
            return convertView;
        }
    }

    private InvariantDeviceProfile getInvariantDeviceProfile() {
        LauncherAppState app = LauncherAppState.getInstance();
        return app.getInvariantDeviceProfile();
    }

    private static class GridSizeView extends View {
        private int mRows = 0, mColumns = 0;
        private Paint mForegroundPaint;
        private int mBackgroundColor;

        public GridSizeView(Context context, AttributeSet attrs) {
            super(context, attrs);
            Resources res = context.getResources();

            mForegroundPaint = new Paint();
            mForegroundPaint.setColor(res.getColor(R.color.dynamic_grid_preview_foreground));
            mBackgroundColor = res.getColor(R.color.dynamic_grid_preview_background);
        }

        public void setMetrics(int rows, int columns) {
            mRows = rows;
            mColumns = columns;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float width = getWidth() - getPaddingLeft() - getPaddingRight();
            float height = getHeight() - getPaddingTop() - getPaddingBottom();
            float xOffset = getPaddingLeft();
            float yOffset = getPaddingTop();

            canvas.drawColor(mBackgroundColor);

            // Draw rows
            for (int i = 1; i < mRows; i++) {
                float yPos = yOffset + height / mRows * i;
                canvas.drawLine(xOffset, yPos, xOffset + width, yPos, mForegroundPaint);
            }

            // Draw columns
            for (int j = 1; j < mColumns; j++) {
                float xPos = xOffset + width / mColumns * j;
                canvas.drawLine(xPos, yOffset, xPos, yOffset + height, mForegroundPaint);
            }
        }
    }
}
