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
package com.android.launcher3.allapps;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Process;
import android.support.animation.DynamicAnimation;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.AppInfo;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Insettable;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.keyboard.FocusedItemDecorator;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ComponentKeyMapper;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.BottomUserEducationView;
import com.android.launcher3.views.RecyclerViewFastScroller;
import com.android.launcher3.views.SpringRelativeLayout;

import java.util.List;

/**
 * The all apps view container.
 */
public class AllAppsContainerView extends SpringRelativeLayout implements DragSource,
        Insettable, OnDeviceProfileChangeListener {

    private static final float FLING_VELOCITY_MULTIPLIER = 135f;
    // Starts the springs after at least 55% of the animation has passed.
    private static final float FLING_ANIMATION_THRESHOLD = 0.55f;

    private final Launcher mLauncher;
    private final AdapterHolder[] mAH;
    private final ItemInfoMatcher mPersonalMatcher = ItemInfoMatcher.ofUser(Process.myUserHandle());
    private final ItemInfoMatcher mWorkMatcher = ItemInfoMatcher.not(mPersonalMatcher);
    private final AllAppsStore mAllAppsStore = new AllAppsStore();

    private final Paint mNavBarScrimPaint;
    private int mNavBarScrimHeight = 0;

    private SearchUiManager mSearchUiManager;
    private View mSearchContainer;
    private AllAppsPagedView mViewPager;
    private FloatingHeaderView mHeader;

    private SpannableStringBuilder mSearchQueryBuilder = null;

    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;

    private boolean mUsingTabs;
    private boolean mHasPredictions = false;
    private boolean mSearchModeWhileUsingTabs = false;

    private RecyclerViewFastScroller mTouchHandler;
    private final Point mFastScrollerOffset = new Point();

    public AllAppsContainerView(Context context) {
        this(context, null);
    }

    public AllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLauncher = Launcher.getLauncher(context);
        mLauncher.addOnDeviceProfileChangeListener(this);

        mSearchQueryBuilder = new SpannableStringBuilder();
        Selection.setSelection(mSearchQueryBuilder, 0);

        mAH = new AdapterHolder[2];
        mAH[AdapterHolder.MAIN] = new AdapterHolder(false /* isWork */);
        mAH[AdapterHolder.WORK] = new AdapterHolder(true /* isWork */);

        mNavBarScrimPaint = new Paint();
        mNavBarScrimPaint.setColor(Themes.getAttrColor(context, R.attr.allAppsNavBarScrimColor));

        mAllAppsStore.addUpdateListener(this::onAppsUpdated);

        addSpringView(R.id.all_apps_header);
        addSpringView(R.id.apps_list_view);
        addSpringView(R.id.all_apps_tabs_view_pager);
    }

    public AllAppsStore getAppsStore() {
        return mAllAppsStore;
    }

    @Override
    protected void setDampedScrollShift(float shift) {
        // Bound the shift amount to avoid content from drawing on top (Y-val) of the QSB.
        float maxShift = getSearchView().getHeight() / 2f;
        super.setDampedScrollShift(Utilities.boundToRange(shift, -maxShift, maxShift));
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        for (AdapterHolder holder : mAH) {
            if (holder.recyclerView != null) {
                // Remove all views and clear the pool, while keeping the data same. After this
                // call, all the viewHolders will be recreated.
                holder.recyclerView.swapAdapter(holder.recyclerView.getAdapter(), true);
                holder.recyclerView.getRecycledViewPool().clear();
            }
        }
    }

    private void onAppsUpdated() {
        if (FeatureFlags.ALL_APPS_TABS_ENABLED) {
            boolean hasWorkApps = false;
            for (AppInfo app : mAllAppsStore.getApps()) {
                if (mWorkMatcher.matches(app, null)) {
                    hasWorkApps = true;
                    break;
                }
            }
            rebindAdapters(hasWorkApps);
        }
    }

    /**
     * Returns whether the view itself will handle the touch event or not.
     */
    public boolean shouldContainerScroll(MotionEvent ev) {
        // IF the MotionEvent is inside the search box, and the container keeps on receiving
        // touch input, container should move down.
        if (mLauncher.getDragLayer().isEventOverView(mSearchContainer, ev)) {
            return true;
        }
        AllAppsRecyclerView rv = getActiveRecyclerView();
        if (rv == null) {
            return true;
        }
        if (rv.getScrollbar().getThumbOffsetY() >= 0 &&
                mLauncher.getDragLayer().isEventOverView(rv.getScrollbar(), ev)) {
            return false;
        }
        return rv.shouldContainerScroll(ev, mLauncher.getDragLayer());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null &&
                    rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(), mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            }
        }
        if (mTouchHandler != null) {
            return mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mTouchHandler != null) {
            mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
            return true;
        }
        return false;
    }

    public String getDescription() {
        @StringRes int descriptionRes;
        if (mUsingTabs) {
            descriptionRes =
                    mViewPager.getNextPage() == 0
                            ? R.string.all_apps_button_personal_label
                            : R.string.all_apps_button_work_label;
        } else {
            descriptionRes = R.string.all_apps_button_label;
        }
        return getContext().getString(descriptionRes);
    }

    public AllAppsRecyclerView getActiveRecyclerView() {
        if (!mUsingTabs || mViewPager.getNextPage() == 0) {
            return mAH[AdapterHolder.MAIN].recyclerView;
        } else {
            return mAH[AdapterHolder.WORK].recyclerView;
        }
    }

    /**
     * Resets the state of AllApps.
     */
    public void reset(boolean animate) {
        for (int i = 0; i < mAH.length; i++) {
            if (mAH[i].recyclerView != null) {
                mAH[i].recyclerView.scrollToTop();
            }
        }
        if (isHeaderVisible()) {
            mHeader.reset(animate);
        }
        // Reset the search bar and base recycler view after transitioning home
        mSearchUiManager.resetSearch();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // This is a focus listener that proxies focus from a view into the list view.  This is to
        // work around the search box from getting first focus and showing the cursor.
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && getActiveRecyclerView() != null) {
                getActiveRecyclerView().requestFocus();
            }
        });

        mHeader = findViewById(R.id.all_apps_header);
        rebindAdapters(mUsingTabs, true /* force */);

        mSearchContainer = findViewById(R.id.search_container_all_apps);
        mSearchUiManager = (SearchUiManager) mSearchContainer;
        mSearchUiManager.initialize(this);
    }

    public SearchUiManager getSearchUiManager() {
        return mSearchUiManager;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        DeviceProfile grid = mLauncher.getDeviceProfile();

        if (mNumAppsPerRow != grid.inv.numColumns ||
                mNumPredictedAppsPerRow != grid.inv.numColumns) {
            mNumAppsPerRow = grid.inv.numColumns;
            mNumPredictedAppsPerRow = grid.inv.numColumns;
            for (int i = 0; i < mAH.length; i++) {
                mAH[i].applyNumsPerRow();
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        mSearchUiManager.preDispatchKeyEvent(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) { }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        // This is filled in {@link AllAppsRecyclerView}
    }

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        int leftRightPadding = grid.desiredWorkspaceLeftRightMarginPx
                + grid.cellLayoutPaddingLeftRightPx;

        for (int i = 0; i < mAH.length; i++) {
            mAH[i].padding.bottom = insets.bottom;
            mAH[i].padding.left = mAH[i].padding.right = leftRightPadding;
            mAH[i].applyPadding();
        }

        ViewGroup.MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        if (grid.isVerticalBarLayout()) {
            mlp.leftMargin = insets.left;
            mlp.rightMargin = insets.right;
            setPadding(grid.workspacePadding.left, 0, grid.workspacePadding.right, 0);
        } else {
            mlp.leftMargin = mlp.rightMargin = 0;
            setPadding(0, 0, 0, 0);
        }
        setLayoutParams(mlp);

        mNavBarScrimHeight = insets.bottom;
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mNavBarScrimHeight > 0) {
            canvas.drawRect(0, getHeight() - mNavBarScrimHeight, getWidth(), getHeight(),
                    mNavBarScrimPaint);
        }
    }

    @Override
    public int getCanvasClipTopForOverscroll() {
        // Do not clip if the QSB is attached to the spring, otherwise the QSB will get clipped.
        return mSpringViews.get(getSearchView().getId()) ? 0 : mHeader.getTop();
    }

    private void rebindAdapters(boolean showTabs) {
        rebindAdapters(showTabs, false /* force */);
    }

    private void rebindAdapters(boolean showTabs, boolean force) {
        if (showTabs == mUsingTabs && !force) {
            return;
        }
        replaceRVContainer(showTabs);
        mUsingTabs = showTabs;

        mAllAppsStore.unregisterIconContainer(mAH[AdapterHolder.MAIN].recyclerView);
        mAllAppsStore.unregisterIconContainer(mAH[AdapterHolder.WORK].recyclerView);

        if (mUsingTabs) {
            mAH[AdapterHolder.MAIN].setup(mViewPager.getChildAt(0), mPersonalMatcher);
            mAH[AdapterHolder.WORK].setup(mViewPager.getChildAt(1), mWorkMatcher);
            onTabChanged(mViewPager.getNextPage());
            setupHeader();
        } else {
            mAH[AdapterHolder.MAIN].setup(findViewById(R.id.apps_list_view), null);
            mAH[AdapterHolder.WORK].recyclerView = null;
            if (FeatureFlags.ALL_APPS_PREDICTION_ROW_VIEW) {
                setupHeader();
            } else {
                mHeader.setVisibility(View.GONE);
            }
        }

        mAllAppsStore.registerIconContainer(mAH[AdapterHolder.MAIN].recyclerView);
        mAllAppsStore.registerIconContainer(mAH[AdapterHolder.WORK].recyclerView);
    }

    private void replaceRVContainer(boolean showTabs) {
        for (int i = 0; i < mAH.length; i++) {
            if (mAH[i].recyclerView != null) {
                mAH[i].recyclerView.setLayoutManager(null);
            }
        }
        View oldView = getRecyclerViewContainer();
        int index = indexOfChild(oldView);
        removeView(oldView);
        int layout = showTabs ? R.layout.all_apps_tabs : R.layout.all_apps_rv_layout;
        View newView = LayoutInflater.from(getContext()).inflate(layout, this, false);
        addView(newView, index);
        if (showTabs) {
            mViewPager = (AllAppsPagedView) newView;
            mViewPager.initParentViews(this);
            mViewPager.getPageIndicator().setContainerView(this);
        } else {
            mViewPager = null;
        }
    }

    public View getRecyclerViewContainer() {
        return mViewPager != null ? mViewPager : findViewById(R.id.apps_list_view);
    }

    public void onTabChanged(int pos) {
        mHeader.setMainActive(pos == 0);
        reset(true /* animate */);
        if (mAH[pos].recyclerView != null) {
            mAH[pos].recyclerView.bindFastScrollbar();

            findViewById(R.id.tab_personal)
                    .setOnClickListener((View view) -> mViewPager.snapToPage(AdapterHolder.MAIN));
            findViewById(R.id.tab_work)
                    .setOnClickListener((View view) -> mViewPager.snapToPage(AdapterHolder.WORK));

        }
        if (pos == AdapterHolder.WORK) {
            BottomUserEducationView.showIfNeeded(mLauncher);
        }
    }

    public void setPredictedApps(List<ComponentKeyMapper<AppInfo>> apps) {
        mAH[AdapterHolder.MAIN].appsList.setPredictedApps(apps);
        boolean hasPredictions = !apps.isEmpty();
        if (mHasPredictions != hasPredictions) {
            mHasPredictions = hasPredictions;
            if (FeatureFlags.ALL_APPS_PREDICTION_ROW_VIEW) {
                setupHeader();
            }
        }
    }

    public AppInfo findApp(ComponentKey mapper) {
        return mAllAppsStore.getApp(mapper);
    }

    public AlphabeticalAppsList getApps() {
        return mAH[AdapterHolder.MAIN].appsList;
    }

    public boolean isUsingTabs() {
        return mUsingTabs;
    }

    public FloatingHeaderView getFloatingHeaderView() {
        return mHeader;
    }

    public View getSearchView() {
        return mSearchContainer;
    }

    public View getContentView() {
        return mViewPager == null ? getActiveRecyclerView() : mViewPager;
    }

    public RecyclerViewFastScroller getScrollBar() {
        AllAppsRecyclerView rv = getActiveRecyclerView();
        return rv == null ? null : rv.getScrollbar();
    }

    public void setupHeader() {
        if (mHeader == null) {
            return;
        }
        mHeader.setVisibility(View.VISIBLE);
        mHeader.setup(mAH, mAH[AllAppsContainerView.AdapterHolder.WORK].recyclerView == null);

        int padding = mHeader.getMaxTranslation();
        if (mHasPredictions && !mUsingTabs) {
            padding += mHeader.getPaddingTop() + mHeader.getPaddingBottom();
        }
        for (int i = 0; i < mAH.length; i++) {
            mAH[i].paddingTopForTabs = padding;
            mAH[i].applyPadding();
        }
    }

    public void setLastSearchQuery(String query) {
        for (int i = 0; i < mAH.length; i++) {
            mAH[i].adapter.setLastSearchQuery(query);
        }
        if (mUsingTabs) {
            mSearchModeWhileUsingTabs = true;
            rebindAdapters(false); // hide tabs
        }
    }

    public void onClearSearchResult() {
        if (mSearchModeWhileUsingTabs) {
            rebindAdapters(true); // show tabs
            mSearchModeWhileUsingTabs = false;
        }
    }

    public void onSearchResultsChanged() {
        for (int i = 0; i < mAH.length; i++) {
            if (mAH[i].recyclerView != null) {
                mAH[i].recyclerView.onSearchResultsChanged();
            }
        }
    }

    public void setRecyclerViewPaddingTop(int top) {
        for (int i = 0; i < mAH.length; i++) {
            mAH[i].padding.top = top;
            mAH[i].applyPadding();
        }
    }

    public void setRecyclerViewVerticalFadingEdgeEnabled(boolean enabled) {
        for (int i = 0; i < mAH.length; i++) {
            mAH[i].applyVerticalFadingEdgeEnabled(enabled);
        }
    }

    public void addElevationController(RecyclerView.OnScrollListener scrollListener) {
        if (!mUsingTabs) {
            mAH[AdapterHolder.MAIN].recyclerView.addOnScrollListener(scrollListener);
        }
    }

    public boolean isHeaderVisible() {
        return mHeader != null && mHeader.getVisibility() == View.VISIBLE;
    }

    public void onScrollUpEnd() {
        if (mUsingTabs) {
            ((PersonalWorkSlidingTabStrip) findViewById(R.id.tabs)).highlightWorkTabIfNecessary();
        }
    }

    /**
     * Adds an update listener to {@param animator} that adds springs to the animation.
     */
    public void addSpringFromFlingUpdateListener(ValueAnimator animator, float velocity) {
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean shouldSpring = true;

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                if (shouldSpring
                        && valueAnimator.getAnimatedFraction() >= FLING_ANIMATION_THRESHOLD) {
                    int searchViewId = getSearchView().getId();
                    addSpringView(searchViewId);

                    finishWithShiftAndVelocity(1, velocity * FLING_VELOCITY_MULTIPLIER,
                            new DynamicAnimation.OnAnimationEndListener() {
                                @Override
                                public void onAnimationEnd(DynamicAnimation animation,
                                        boolean canceled, float value, float velocity) {
                                    removeSpringView(searchViewId);
                                }
                            });

                    shouldSpring = false;
                }
            }
        });
    }

    @Override
    public void getDrawingRect(Rect outRect) {
        super.getDrawingRect(outRect);
        outRect.offset(0, (int) getTranslationY());
    }

    public class AdapterHolder {
        public static final int MAIN = 0;
        public static final int WORK = 1;

        public final AllAppsGridAdapter adapter;
        final LinearLayoutManager layoutManager;
        final AlphabeticalAppsList appsList;
        final Rect padding = new Rect();
        int paddingTopForTabs;
        AllAppsRecyclerView recyclerView;
        boolean verticalFadingEdge;

        AdapterHolder(boolean isWork) {
            appsList = new AlphabeticalAppsList(mLauncher, mAllAppsStore, isWork);
            adapter = new AllAppsGridAdapter(mLauncher, appsList);
            appsList.setAdapter(adapter);
            layoutManager = adapter.getLayoutManager();
        }

        void setup(@NonNull View rv, @Nullable ItemInfoMatcher matcher) {
            appsList.updateItemFilter(matcher);
            recyclerView = (AllAppsRecyclerView) rv;
            recyclerView.setEdgeEffectFactory(createEdgeEffectFactory());
            recyclerView.setApps(appsList, mUsingTabs);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setAdapter(adapter);
            recyclerView.setHasFixedSize(true);
            // No animations will occur when changes occur to the items in this RecyclerView.
            recyclerView.setItemAnimator(null);
            FocusedItemDecorator focusedItemDecorator = new FocusedItemDecorator(recyclerView);
            recyclerView.addItemDecoration(focusedItemDecorator);
            adapter.setIconFocusListener(focusedItemDecorator.getFocusListener());
            applyVerticalFadingEdgeEnabled(verticalFadingEdge);
            applyPadding();
            applyNumsPerRow();
        }

        void applyPadding() {
            if (recyclerView != null) {
                int paddingTop = mUsingTabs || FeatureFlags.ALL_APPS_PREDICTION_ROW_VIEW
                        ? paddingTopForTabs : padding.top;
                recyclerView.setPadding(padding.left, paddingTop, padding.right, padding.bottom);
            }
        }

        void applyNumsPerRow() {
            if (mNumAppsPerRow > 0) {
                if (recyclerView != null) {
                    recyclerView.setNumAppsPerRow(mLauncher.getDeviceProfile(), mNumAppsPerRow);
                }
                adapter.setNumAppsPerRow(mNumAppsPerRow);
                appsList.setNumAppsPerRow(mNumAppsPerRow, mNumPredictedAppsPerRow);
            }
        }

        public void applyVerticalFadingEdgeEnabled(boolean enabled) {
            verticalFadingEdge = enabled;
            mAH[AdapterHolder.MAIN].recyclerView.setVerticalFadingEdgeEnabled(!mUsingTabs
                    && verticalFadingEdge);
        }
    }
}
