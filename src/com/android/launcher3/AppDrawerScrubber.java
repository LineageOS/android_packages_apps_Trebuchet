package com.android.launcher3;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.SeekBar;

public class AppDrawerScrubber extends LinearLayout {

    private AppDrawerListAdapter mAdapter;
    private RecyclerView mListView;
    private SeekBar mSeekBar;
    private String[] mSections;
    private LinearLayoutManager mLayoutManager;

    public AppDrawerScrubber(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AppDrawerScrubber(Context context) {
        super(context);
        init(context);
    }

    public void updateSections() {
        mSections = (String[]) mAdapter.getSections();
        mSeekBar.setMax(mSections.length - 1);
    }

    public void setSource(RecyclerView listView) {
        mListView = listView;
        mAdapter = (AppDrawerListAdapter) listView.getAdapter();
        mLayoutManager = (LinearLayoutManager) listView.getLayoutManager();
    }

    private boolean isReady() {
        return mListView != null &&
                mAdapter != null &&
                mSections != null;
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.scrub_layout, this);
        mSeekBar = (SeekBar) findViewById(R.id.scrubber);

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, final int progress, boolean fromUser) {
                if (!isReady()) {
                    return;
                }
                mLayoutManager.scrollToPositionWithOffset(mAdapter.getPositionForSection(progress), 0);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (!isReady()) {
                    return;
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (!isReady()) {
                    return;
                }
            }
        });
    }
}