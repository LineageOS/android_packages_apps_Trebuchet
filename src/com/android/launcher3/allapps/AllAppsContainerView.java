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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.InsetDrawable;
import android.support.v7.widget.RecyclerView;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.method.TextKeyListener;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.android.launcher3.AppInfo;
import com.android.launcher3.BaseContainerView;
import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeleteDropTarget;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Folder;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherTransitionable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.settings.SettingsProvider;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Thunk;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * The all apps view container.
 */
public class AllAppsContainerView extends BaseContainerView implements DragSource,
        LauncherTransitionable, View.OnTouchListener, View.OnLongClickListener,
        AllAppsSearchBarController.Callbacks {

    public static final int SECTION_STRATEGY_GRID = 1;
    public static final int SECTION_STRATEGY_RAGGED = 2;

    public static final int GRID_THEME_LIGHT = 1;
    public static final int GRID_THEME_DARK = 2;

    @Thunk Launcher mLauncher;
    @Thunk AlphabeticalAppsList mApps;
    private AllAppsGridAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private RecyclerView.ItemDecoration mItemDecoration;

    @Thunk View mContent;
    @Thunk View mContainerView;
    @Thunk View mRevealView;
    @Thunk AllAppsRecyclerView mAppsRecyclerView;
    @Thunk AllAppsSearchBarController mSearchBarController;
    private ViewGroup mSearchBarContainerView;
    private View mSearchBarView;
    private SpannableStringBuilder mSearchQueryBuilder = null;

    private int mSectionStrategy = SECTION_STRATEGY_RAGGED;
    private int mGridTheme = GRID_THEME_DARK;
    private int mLastGridTheme = -1;

    private int mSectionNamesMargin;
    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;
    private int mRecyclerViewTopBottomPadding;
    // This coordinate is relative to this container view
    private final Point mBoundsCheckLastTouchDownPos = new Point(-1, -1);
    // This coordinate is relative to its parent
    private final Point mIconLastTouchPos = new Point();

    private boolean mReloadDrawer = false;

    private View.OnClickListener mSearchClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent searchIntent = (Intent) v.getTag();
            mLauncher.startActivitySafely(v, searchIntent, null);
        }
    };

    public AllAppsContainerView(Context context) {
        this(context, null);
    }

    public AllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Resources res = context.getResources();

        mLauncher = (Launcher) context;
        mApps = new AlphabeticalAppsList(context);
        mAdapter = new AllAppsGridAdapter(mLauncher, mApps, this, mLauncher,
                this);
        mApps.setAdapter(mAdapter);
        mLayoutManager = mAdapter.getLayoutManager();
        mItemDecoration = mAdapter.getItemDecoration();
        mRecyclerViewTopBottomPadding =
                res.getDimensionPixelSize(R.dimen.all_apps_list_top_bottom_padding);

        mSearchQueryBuilder = new SpannableStringBuilder();
        Selection.setSelection(mSearchQueryBuilder, 0);
    }

    public int getNumPredictedAppsPerRow() {
        return mNumPredictedAppsPerRow;
    }

    /**
     * Sets the current set of predicted apps by component.
     * Only usable when custom predicted apps are disabled.
     */
    public void setPredictedAppComponents(List<ComponentKey> apps) {
        mApps.setPredictedAppComponents(apps);
        updateScrubber();
    }

    /**
     * Sets the current set of predicted apps by info.
     * Only usable when custom predicated apps are enabled.
     */
    public void setPredictedApps(List<AppInfo> apps) {
        mApps.setPredictedApps(apps);
        updateScrubber();
    }

    /**
     * Set whether the predicted apps row will have a customized selection of apps.
     */
    public void setCustomPredictedAppsEnabled(boolean enabled) {
        mApps.mCustomPredictedAppsEnabled = enabled;
    }

    /**
     * Sets the current set of apps.
     */
    public void setApps(List<AppInfo> apps) {
        mApps.setApps(apps);
        updateScrubber();
    }

    /**
     * Adds new apps to the list.
     */
    public void addApps(List<AppInfo> apps) {
        mApps.addApps(apps);
        updateScrubber();
    }

    /**
     * Reloads the existing apps in the list
     */
    public void onReloadAppDrawer() {
        mReloadDrawer = true;
        List<AppInfo> apps = mApps.getApps();
        updateApps(apps);
        requestLayout();
    }

    /**
     * Updates existing apps in the list
     */
    public void updateApps(List<AppInfo> apps) {
        mApps.updateApps(apps);
        updateScrubber();
    }

    /**
     * Removes some apps from the list.
     */
    public void removeApps(List<AppInfo> apps) {
        mApps.removeApps(apps);
        updateScrubber();
    }

    private void updateScrubber() {
        if (useScroller() && useScrubber()) {
            mScrubber.updateSections();
        }
    }

    public List<AppInfo> getApps() {
        return mApps.getApps();
    }

    public int getSectionStrategy() {
        return mSectionStrategy;
    }

    private void updateSectionStrategy() {
        Context context = getContext();
        Resources res = context.getResources();
        boolean useCompactGrid = SettingsProvider.getBoolean(context,
                SettingsProvider.SETTINGS_UI_DRAWER_STYLE_USE_COMPACT,
                R.bool.preferences_interface_drawer_compact_default);
        mSectionStrategy = useCompactGrid ? SECTION_STRATEGY_GRID : SECTION_STRATEGY_RAGGED;
        mSectionNamesMargin = useCompactGrid ?
                res.getDimensionPixelSize(R.dimen.all_apps_grid_view_start_margin) :
                res.getDimensionPixelSize(R.dimen.all_apps_grid_view_start_margin_with_sections);
        mAdapter.setSectionStrategy(mSectionStrategy);
        mAppsRecyclerView.setSectionStrategy(mSectionStrategy);
    }

    private void updateGridTheme() {
        Context context = getContext();
        boolean useDarkColor= SettingsProvider.getBoolean(context,
                SettingsProvider.SETTINGS_UI_DRAWER_DARK,
                R.bool.preferences_interface_drawer_dark_default);
        mGridTheme = useDarkColor ? GRID_THEME_DARK : GRID_THEME_LIGHT;
        mAdapter.setGridTheme(mGridTheme);
        updateBackgroundAndPaddings(true);
    }

    /**
     * Sets the search bar that shows above the a-z list.
     */
    public void setSearchBarController(AllAppsSearchBarController searchController) {
        if (searchController == null) {
            mSearchBarController = null;
            return;
        }
        if (mSearchBarController != null) {
            throw new RuntimeException("Expected search bar controller to only be set once");
        }
        mSearchBarController = searchController;
        mSearchBarController.initialize(mApps, this);

        // Add the new search view to the layout
        View searchBarView = searchController.getView(mSearchBarContainerView);
        mSearchBarContainerView.addView(searchBarView);
        mSearchBarContainerView.setVisibility(View.VISIBLE);
        mSearchBarView = searchBarView;
        setHasSearchBar(true);

        updateBackgroundAndPaddings();
    }

    public void setSearchBarContainerViewVisibility(int visibility) {
        mSearchBarContainerView.setVisibility(visibility);
        updateBackgroundAndPaddings();
    }

    /**
     * Scrolls this list view to the top.
     */
    public void scrollToTop() {
        mAppsRecyclerView.scrollToTop();
    }

    /**
     * Returns the content view used for the launcher transitions.
     */
    public View getContentView() {
        return mContainerView;
    }

    /**
     * Returns the all apps search view.
     */
    public View getSearchBarView() {
        return mSearchBarView;
    }

    /**
     * Returns the reveal view used for the launcher transitions.
     */
    public View getRevealView() {
        return mRevealView;
    }

    /**
     * Returns an new instance of the default app search controller.
     */
    public AllAppsSearchBarController newDefaultAppSearchController() {
        return new DefaultAppSearchController(getContext(), this, mAppsRecyclerView);
    }

    /**
     * Focuses the search field and begins an app search.
     */
    public void startAppsSearch() {
        if (mSearchBarController != null) {
            mSearchBarController.focusSearchField();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        boolean isRtl = Utilities.isRtl(getResources());
        mAdapter.setRtl(isRtl);
        mContent = findViewById(R.id.content);

        // This is a focus listener that proxies focus from a view into the list view.  This is to
        // work around the search box from getting first focus and showing the cursor.
        View.OnFocusChangeListener focusProxyListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mAppsRecyclerView.requestFocus();
                }
            }
        };
        mSearchBarContainerView = (ViewGroup) findViewById(R.id.search_box_container);
        mSearchBarContainerView.setOnFocusChangeListener(focusProxyListener);
        mContainerView = findViewById(R.id.all_apps_container);
        mContainerView.setOnFocusChangeListener(focusProxyListener);
        mRevealView = findViewById(R.id.all_apps_reveal);

        // Load the all apps recycler view
        mAppsRecyclerView = (AllAppsRecyclerView) findViewById(R.id.apps_list_view);
        mAppsRecyclerView.setApps(mApps);
        mAppsRecyclerView.setLayoutManager(mLayoutManager);
        mAppsRecyclerView.setAdapter(mAdapter);
        mAppsRecyclerView.setHasFixedSize(true);
        if (mItemDecoration != null) {
            mAppsRecyclerView.addItemDecoration(mItemDecoration);
        }
        setScroller();
        updateGridTheme();
        updateSectionStrategy();
        updateBackgroundAndPaddings();
    }

    @Override
    public void onBoundsChanged(Rect newBounds) {
        mLauncher.updateOverlayBounds(newBounds);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Update the number of items in the grid before we measure the view
        int availableWidth = !mContentBounds.isEmpty() ? mContentBounds.width() :
                MeasureSpec.getSize(widthMeasureSpec);
        DeviceProfile grid = mLauncher.getDeviceProfile();
        grid.updateAppsViewNumCols(getResources(), availableWidth,
                mSectionStrategy);
        if (mNumAppsPerRow != grid.allAppsNumCols ||
                mNumPredictedAppsPerRow != grid.allAppsNumPredictiveCols) {
            mNumAppsPerRow = grid.allAppsNumCols;
            mNumPredictedAppsPerRow = grid.allAppsNumPredictiveCols;

            mAppsRecyclerView.setNumAppsPerRow(grid, mNumAppsPerRow);
            mAdapter.setNumAppsPerRow(mNumAppsPerRow);

            boolean mergeSections = mSectionStrategy == SECTION_STRATEGY_GRID;
            mApps.setNumAppsPerRow(mNumAppsPerRow, mNumPredictedAppsPerRow, mergeSections);

            mLauncher.getRemoteFolderManager().onMeasureDrawer(mNumPredictedAppsPerRow);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mReloadDrawer) {
            updateBackgroundAndPaddings(true);
            mReloadDrawer = false;
        }
    }

    /**
     * Update the background and padding of the Apps view and children.  Instead of insetting the
     * container view, we inset the background and padding of the recycler view to allow for the
     * recycler view to handle touch events (for fast scrolling) all the way to the edge.
     */
    @Override
    protected void onUpdateBackgroundAndPaddings(Rect searchBarBounds, Rect padding) {
        boolean isRtl = Utilities.isRtl(getResources());

        // TODO: Use quantum_panel instead of quantum_panel_shape
        int bgRes = mGridTheme == GRID_THEME_DARK ? R.drawable.quantum_panel_shape_dark :
                R.drawable.quantum_panel_shape;
        InsetDrawable background = new InsetDrawable(
                getResources().getDrawable(bgRes), padding.left, 0,
                padding.right, 0);
        Rect bgPadding = new Rect();
        background.getPadding(bgPadding);
        mContainerView.setBackground(background);
        mRevealView.setBackground(background.getConstantState().newDrawable());
        mAppsRecyclerView.updateBackgroundPadding(bgPadding);
        mAdapter.updateBackgroundPadding(bgPadding);

        // Hack: We are going to let the recycler view take the full width, so reset the padding on
        // the container to zero after setting the background and apply the top-bottom padding to
        // the content view instead so that the launcher transition clips correctly.
        mContent.setPadding(0, padding.top, 0, padding.bottom);
        mContainerView.setPadding(0, 0, 0, 0);

        // Pad the recycler view by the background padding plus the start margin (for the section
        // names)
        int startInset = Math.max(mSectionNamesMargin, mAppsRecyclerView.getMaxScrollbarWidth());
        int topBottomPadding = mRecyclerViewTopBottomPadding;
        final boolean useScrollerScrubber = useScroller() && useScrubber();
        if (isRtl) {
            mAppsRecyclerView.setPadding(padding.left + mAppsRecyclerView.getMaxScrollbarWidth(),
                    topBottomPadding, padding.right + startInset, useScrollerScrubber ?
                    mScrubberHeight + topBottomPadding : topBottomPadding);
            if (useScrollerScrubber) {
                mScrubberContainerView.setPadding(padding.left +
                        mAppsRecyclerView.getMaxScrollbarWidth(), 0, padding.right, 0);
            }
        } else {
            mAppsRecyclerView.setPadding(padding.left + startInset, topBottomPadding,
                    padding.right + mAppsRecyclerView.getMaxScrollbarWidth(), useScrollerScrubber ?
                    mScrubberHeight + topBottomPadding : topBottomPadding);
            if (useScrollerScrubber) {
                mScrubberContainerView.setPadding(padding.left, 0,
                        padding.right + mAppsRecyclerView.getMaxScrollbarWidth(), 0);
            }
        }

        // Inset the search bar to fit its bounds above the container
        if (mSearchBarView != null) {
            Rect backgroundPadding = new Rect();
            if (mSearchBarView.getBackground() != null) {
                mSearchBarView.getBackground().getPadding(backgroundPadding);
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)
                    mSearchBarContainerView.getLayoutParams();
            lp.leftMargin = searchBarBounds.left - backgroundPadding.left;
            lp.topMargin = searchBarBounds.top - backgroundPadding.top;
            lp.rightMargin = (getMeasuredWidth() - searchBarBounds.right)
                    - backgroundPadding.right;
            mSearchBarContainerView.requestLayout();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Determine if the key event was actual text, if so, focus the search bar and then dispatch
        // the key normally so that it can process this key event
        if (mSearchBarController != null &&
                !mSearchBarController.isSearchFieldFocused() &&
                event.getAction() == KeyEvent.ACTION_DOWN) {
            final int unicodeChar = event.getUnicodeChar();
            final boolean isKeyNotWhitespace = unicodeChar > 0 &&
                    !Character.isWhitespace(unicodeChar) && !Character.isSpaceChar(unicodeChar);
            if (isKeyNotWhitespace) {
                boolean gotKey = TextKeyListener.getInstance().onKeyDown(this, mSearchQueryBuilder,
                        event.getKeyCode(), event);
                if (gotKey && mSearchQueryBuilder.length() > 0) {
                    mSearchBarController.focusSearchField();
                }
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return handleTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int y = (int) ev.getY();
        int[] location = new int[2];
        int height = 0;

        // Ignore the touch if it is below scrubber (if enabled) or below app recycler view
        if (useScroller() && useScrubber()) {
            mScrubber.getLocationInWindow(location);
            height = mScrubber.getHeight();
        } else {
            mAppsRecyclerView.getLocationInWindow(location);
            height = mAppsRecyclerView.getHeight();
        }
        if (y >= location[1] + height) {
            return true;
        } else {
            return handleTouchEvent(ev);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                mIconLastTouchPos.set((int) ev.getX(), (int) ev.getY());
                break;
        }
        return false;
    }

    @Override
    public boolean onLongClick(View v) {
        // Return early if this is not initiated from a touch
        if (!v.isInTouchMode()) return false;
        // When we have exited all apps or are in transition, disregard long clicks
        if (!mLauncher.isAppsViewVisible() ||
                mLauncher.getWorkspace().isSwitchingState()) return false;
        // Return if global dragging is not enabled
        if (!mLauncher.isDraggingEnabled()) return false;

        // Start the drag
        mLauncher.getWorkspace().beginDragShared(v, mIconLastTouchPos, this, false);
        // Enter spring loaded mode
        mLauncher.enterSpringLoadedDragMode();

        return false;
    }

    @Override
    public boolean supportsFlingToDelete() {
        return true;
    }

    @Override
    public boolean supportsAppInfoDropTarget() {
        return true;
    }

    @Override
    public boolean supportsDeleteDropTarget() {
        return false;
    }

    @Override
    public float getIntrinsicIconScaleFactor() {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        return (float) grid.allAppsIconSizePx / grid.iconSizePx;
    }

    @Override
    public void onFlingToDeleteCompleted() {
        // We just dismiss the drag when we fling, so cleanup here
        mLauncher.exitSpringLoadedDragModeDelayed(true,
                Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
        mLauncher.unlockScreenOrientation(false);
    }

    @Override
    public void onDropCompleted(View target, DropTarget.DragObject d, boolean isFlingToDelete,
            boolean success) {
        if (isFlingToDelete || !success || (target != mLauncher.getWorkspace() &&
                !(target instanceof DeleteDropTarget) && !(target instanceof Folder))) {
            // Exit spring loaded mode if we have not successfully dropped or have not handled the
            // drop in Workspace
            mLauncher.exitSpringLoadedDragModeDelayed(true,
                    Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
        }
        mLauncher.unlockScreenOrientation(false);

        // Display an error message if the drag failed due to there not being enough space on the
        // target layout we were dropping on.
        if (!success) {
            boolean showOutOfSpaceMessage = false;
            if (target instanceof Workspace) {
                int currentScreen = mLauncher.getCurrentWorkspaceScreen();
                Workspace workspace = (Workspace) target;
                CellLayout layout = (CellLayout) workspace.getChildAt(currentScreen);
                ItemInfo itemInfo = (ItemInfo) d.dragInfo;
                if (layout != null) {
                    showOutOfSpaceMessage =
                            !layout.findCellForSpan(null, itemInfo.spanX, itemInfo.spanY);
                }
            }
            if (showOutOfSpaceMessage) {
                mLauncher.showOutOfSpaceMessage(false);
            }

            d.deferDragViewCleanupPostAnimation = false;
        }
    }

    @Override
    public void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace) {
        // Do nothing
    }

    @Override
    public void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace) {
        // Do nothing
    }

    @Override
    public void onLauncherTransitionStep(Launcher l, float t) {
        // Do nothing
    }

    @Override
    public void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace) {
        if (toWorkspace) {
            // Reset the search bar and base recycler view after transitioning home
            if (hasSearchBar()) {
                mSearchBarController.reset();
            }
            mAppsRecyclerView.reset();
        }
    }

    /**
     * Handles the touch events to dismiss all apps when clicking outside the bounds of the
     * recycler view.
     */
    private boolean handleTouchEvent(MotionEvent ev) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!mContentBounds.isEmpty()) {
                    // Outset the fixed bounds and check if the touch is outside all apps
                    Rect tmpRect = new Rect(mContentBounds);
                    tmpRect.inset(-grid.allAppsIconSizePx / 2, 0);
                    if (ev.getX() < tmpRect.left || ev.getX() > tmpRect.right) {
                        mBoundsCheckLastTouchDownPos.set(x, y);
                        return true;
                    }
                } else {
                    // Check if the touch is outside all apps
                    if (ev.getX() < getPaddingLeft() ||
                            ev.getX() > (getWidth() - getPaddingRight())) {
                        mBoundsCheckLastTouchDownPos.set(x, y);
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mBoundsCheckLastTouchDownPos.x > -1) {
                    ViewConfiguration viewConfig = ViewConfiguration.get(getContext());
                    float dx = ev.getX() - mBoundsCheckLastTouchDownPos.x;
                    float dy = ev.getY() - mBoundsCheckLastTouchDownPos.y;
                    float distance = (float) Math.hypot(dx, dy);
                    if (distance < viewConfig.getScaledTouchSlop()) {
                        // The background was clicked, so just go home
                        Launcher launcher = (Launcher) getContext();
                        launcher.showWorkspace(true);
                        return true;
                    }
                }
                // Fall through
            case MotionEvent.ACTION_CANCEL:
                mBoundsCheckLastTouchDownPos.set(-1, -1);
                break;
        }
        return false;
    }

    @Override
    public void onSearchResult(String query, ArrayList<ComponentKey> apps) {
        if (apps != null) {
            mApps.setOrderedFilter(apps);
            if (mGridTheme != GRID_THEME_LIGHT) {
                mLastGridTheme = mGridTheme;
                mGridTheme = GRID_THEME_LIGHT;
                updateBackgroundAndPaddings(true);
                mAdapter.setGridTheme(mGridTheme);
            }
            mAdapter.setLastSearchQuery(query);
            mAppsRecyclerView.onSearchResultsChanged();
        }
    }

    @Override
    public void clearSearchResult() {
        mApps.setOrderedFilter(null);
        mAppsRecyclerView.onSearchResultsChanged();
        if (mLastGridTheme != -1 && mLastGridTheme != GRID_THEME_LIGHT) {
            mGridTheme = mLastGridTheme;
            updateBackgroundAndPaddings(true);
            mAdapter.setGridTheme(mGridTheme);
            mLastGridTheme = -1;
        }
        // Clear the search query
        mSearchQueryBuilder.clear();
        mSearchQueryBuilder.clearSpans();
        Selection.setSelection(mSearchQueryBuilder, 0);
    }

    @Override
    protected BaseRecyclerView getRecyclerView() {
        return mAppsRecyclerView;
    }
}
