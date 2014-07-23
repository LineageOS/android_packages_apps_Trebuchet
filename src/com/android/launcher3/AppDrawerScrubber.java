package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.AttributeSet;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.SeekBar;
import android.widget.TextView;

public class AppDrawerScrubber extends LinearLayout implements OnClickListener {

    private AppDrawerListAdapter mAdapter;
    private RecyclerView mListView;
    private TextView mFirstIndicator, mLastIndicator;
    private TextView mScrubberIndicator;
    private SeekBar mSeekBar;
    private String[] mSections;
    private LinearLayoutManager mLayoutManager;

    public AppDrawerScrubber(Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.scrub_layout, this);
        mFirstIndicator = ((TextView) findViewById(R.id.firstSection));
        mFirstIndicator.setOnClickListener(this);
        mLastIndicator = ((TextView) findViewById(R.id.lastSection));
        mLastIndicator.setOnClickListener(this);
        mScrubberIndicator = (TextView) findViewById(R.id.scrubberIndicator);
        mSeekBar = (SeekBar) findViewById(R.id.scrubber);
        init();
    }

    public void updateSections() {
        mSections = (String[]) mAdapter.getSections();
        mSeekBar.setMax(mSections.length - 1);
        mFirstIndicator.setText(mSections[0]);
        mLastIndicator.setText(mSections[mSections.length - 1]);
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

    private void init() {
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, final int progress, boolean fromUser) {
                if (!isReady()) {
                    return;
                }
                resetScrubber();
                mScrubberIndicator.setTranslationX((progress * seekBar.getWidth()) /
                        mSections.length);
                String section = String.valueOf(mSections[progress]);
                mLayoutManager.scrollToPositionWithOffset(
                        mAdapter.getPositionForSection(progress), 0);
                mScrubberIndicator.setText(section);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (!isReady()) {
                    return;
                }
                resetScrubber();
                mScrubberIndicator.setAlpha(1f);
                mScrubberIndicator.setVisibility(View.VISIBLE);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (!isReady()) {
                    return;
                }
                resetScrubber();
                mScrubberIndicator.animate().alpha(0f).translationYBy(20f)
                    .setDuration(200).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mScrubberIndicator.setVisibility(View.INVISIBLE);
                    }
                });
            }

            private void resetScrubber() {
                mScrubberIndicator.animate().cancel();
                mScrubberIndicator.setTranslationY(0f);
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v == mFirstIndicator) {
            int positionForFirstSection = mAdapter.getPositionForSection(0);
            mLayoutManager.scrollToPositionWithOffset(positionForFirstSection, 0);
        } else if (v == mLastIndicator) {
            int positionForLastSection = mAdapter.getPositionForSection(mSections.length - 1);
            mLayoutManager.scrollToPositionWithOffset(positionForLastSection, 0);
        }
    }
}