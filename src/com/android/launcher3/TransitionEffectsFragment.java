package com.android.launcher3;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.android.launcher3.settings.SettingsProvider;

public class TransitionEffectsFragment extends Fragment {
    public static final String PAGE_OR_DRAWER_SCROLL_SELECT = "pageOrDrawer";
    public static final String SELECTED_TRANSITION_EFFECT = "selectedTransitionEffect";
    public static final String TRANSITION_EFFECTS_FRAGMENT = "transitionEffectsFragment";
    ImageView mTransitionIcon;
    ListView mListView;
    View mCurrentSelection;

    String[] mTransitionStates;
    TypedArray mTransitionDrawables;
    String mCurrentState;
    int mCurrentPosition;
    boolean mIsDrawer;
    String mSettingsProviderValue;
    int mPreferenceValue;

    OnClickListener mSettingsItemListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (mCurrentPosition == (Integer) v.getTag()) {
                return;
            }
            mCurrentPosition = (Integer) v.getTag();
            mCurrentState = mTransitionStates[mCurrentPosition];

            setCleared(mCurrentSelection);
            setSelected(v);
            mCurrentSelection = v;

            new Thread(new Runnable() {
                public void run() {
                    mTransitionIcon.post(new Runnable() {
                        public void run() {
                            setImageViewToEffect();
                        }
                    });
                }
            }).start();

            ((TransitionsArrayAdapter) mListView.getAdapter()).notifyDataSetChanged();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.settings_transitions_screen, container, false);
        mListView = (ListView) v.findViewById(R.id.settings_transitions_list);

        final Launcher launcher = (Launcher) getActivity();
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)
                mListView.getLayoutParams();
        lp.bottomMargin = ((FrameLayout.LayoutParams) launcher.getOverviewPanel()
                .findViewById(R.id.settings_container).getLayoutParams()).bottomMargin;
        mListView.setLayoutParams(lp);

        mIsDrawer = getArguments().getBoolean(PAGE_OR_DRAWER_SCROLL_SELECT);

        mSettingsProviderValue = mIsDrawer ?
                SettingsProvider.SETTINGS_UI_DRAWER_SCROLLING_TRANSITION_EFFECT
                : SettingsProvider.SETTINGS_UI_HOMESCREEN_SCROLLING_TRANSITION_EFFECT;
        mPreferenceValue = mIsDrawer ? R.string.preferences_interface_drawer_scrolling_transition_effect
                : R.string.preferences_interface_homescreen_scrolling_transition_effect;

        mTransitionIcon = (ImageView) v.findViewById(R.id.settings_transition_image);
        TextView title = (TextView) v.findViewById(R.id.transition_effect_title);
        title.setText(getResources().getString(R.string.scroll_effect_text));
        LinearLayout titleLayout = (LinearLayout) v.findViewById(R.id.transition_title);
        titleLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setEffect();
            }
        });
        View options = v.findViewById(R.id.transition_options_menu);
        options.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                launcher.onClickTransitionEffectOverflowMenuButton(view, mIsDrawer);
            }
        });

        String[] titles = getResources().getStringArray(
                R.array.transition_effect_entries);
        mListView.setAdapter(new TransitionsArrayAdapter(getActivity(),
                R.layout.settings_pane_list_item, titles));

        mTransitionStates = getResources().getStringArray(
                R.array.transition_effect_values);
        mTransitionDrawables = getResources().obtainTypedArray(
                R.array.transition_effect_drawables);

        mCurrentState = SettingsProvider.getString(getActivity(),
                mSettingsProviderValue, mPreferenceValue);
        mCurrentPosition = mapEffectToPosition(mCurrentState);

        mListView.setSelection(mCurrentPosition);

        // RTL
        ImageView navPrev = (ImageView) v.findViewById(R.id.nav_prev);
        Configuration config = getResources().getConfiguration();
        if (config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            navPrev.setImageResource(R.drawable.ic_navigation_next);
        }
        return v;
    }

    public void setEffect() {
        ((Launcher) getActivity()).setTransitionEffect(mIsDrawer, mCurrentState);
    }

    private int mapEffectToPosition(String effect) {
        int length = mTransitionStates.length;
        for (int i = 0; i < length; i++) {
            if (effect.equals(mTransitionStates[i])) {
                return i;
            }
        }
        return -1;
    }

    private void setImageViewToEffect() {
        mTransitionIcon.setBackgroundResource(mTransitionDrawables
                .getResourceId(mCurrentPosition, R.drawable.transition_none));

        AnimationDrawable frameAnimation = (AnimationDrawable) mTransitionIcon.getBackground();
        frameAnimation.start();
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
            ObjectAnimator anim2 = ObjectAnimator.ofFloat(
                   darkPanel , "alpha", 0.0f, 0.3f);
            anim2.start();

            anim.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator arg0) {}
                @Override
                public void onAnimationRepeat(Animator arg0) {}
                @Override
                public void onAnimationEnd(Animator arg0) {
                    darkPanel.setVisibility(View.GONE);
                    setImageViewToEffect();
                }
                @Override
                public void onAnimationCancel(Animator arg0) {}
            });

            return anim;
        } else {
            return super.onCreateAnimator(transit, enter, nextAnim);
        }
    }

    private class TransitionsArrayAdapter extends ArrayAdapter<String> {
        Context mContext;
        String[] titles;

        public TransitionsArrayAdapter(Context context, int textViewResourceId,
                String[] objects) {
            super(context, textViewResourceId, objects);

            mContext = context;
            titles = objects;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.settings_pane_list_item,
                    parent, false);
            TextView textView = (TextView) convertView
                    .findViewById(R.id.item_name);

            // RTL
            Configuration config = getResources().getConfiguration();
            if (config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                textView.setGravity(Gravity.RIGHT);
            }

            textView.setText(titles[position]);
            // Set Selected State
            if (position == mCurrentPosition) {
                mCurrentSelection = convertView;
                setSelected(mCurrentSelection);
            }

            convertView.setOnClickListener(mSettingsItemListener);
            convertView.setTag(position);
            return convertView;
        }
    }
}
