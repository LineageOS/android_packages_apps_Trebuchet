/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copytight (C) 2011 The CyanogenMod Project
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

package com.cyanogenmod.trebuchet;


import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.cyanogenmod.trebuchet.FolderIcon.FolderRingAnimator;
import com.cyanogenmod.trebuchet.InstallWidgetReceiver.WidgetMimeTypeHandlerData;
import com.cyanogenmod.trebuchet.preference.PreferencesProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * The workspace is a wide area with a wallpaper and a finite number of pages.
 * Each page contains a number of icons, folders or widgets the user can
 * interact with. A workspace is meant to be used with a fixed width only.
 */
public class Workspace extends PagedView
        implements DropTarget, DragSource, DragScroller, View.OnTouchListener,
        DragController.DragListener {
    @SuppressWarnings({"UnusedDeclaration"})
    private static final String TAG = "Launcher.Workspace";

    // Y rotation to apply to the workspace screens
    private static final float WORKSPACE_ROTATION = 12.5f;
    private static final float WORKSPACE_OVERSCROLL_ROTATION = 24f;
    private static final float WORKSPACE_ROTATION_ANGLE = 12.5f;
    private static float CAMERA_DISTANCE = 6500;

    private static final int CHILDREN_OUTLINE_FADE_OUT_DELAY = 0;
    private static final int CHILDREN_OUTLINE_FADE_OUT_DURATION = 375;
    private static final int CHILDREN_OUTLINE_FADE_IN_DURATION = 100;

    private static final int BACKGROUND_FADE_OUT_DURATION = 350;
    private static final int ADJACENT_SCREEN_DROP_DURATION = 300;

    // These animators are used to fade the children's outlines
    private ObjectAnimator mChildrenOutlineFadeInAnimation;
    private ObjectAnimator mChildrenOutlineFadeOutAnimation;
    private float mChildrenOutlineAlpha = 0;

    // These properties refer to the background protection gradient used for AllApps and Customize
    private ValueAnimator mBackgroundFadeInAnimation;
    private ValueAnimator mBackgroundFadeOutAnimation;
    private Drawable mBackground;
    boolean mDrawBackground = true;
    private float mBackgroundAlpha = 0;
    private float mOverScrollMaxBackgroundAlpha = 0.0f;
    private int mOverScrollPageIndex = -1;

    private float mWallpaperScrollRatio = 1.0f;

    private final WallpaperManager mWallpaperManager;
    private IBinder mWindowToken;
    private static final float WALLPAPER_SCREENS_SPAN = 2f;

    /**
     * CellInfo for the cell that is currently being dragged
     */
    private CellLayout.CellInfo mDragInfo;

    /**
     * Target drop area calculated during last acceptDrop call.
     */
    private int[] mTargetCell = new int[2];

    /**
     * The CellLayout that is currently being dragged over
     */
    private CellLayout mDragTargetLayout = null;

    private Launcher mLauncher;
    private IconCache mIconCache;
    private DragController mDragController;

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    private int[] mTempCell = new int[2];
    private int[] mTempEstimate = new int[2];
    private float[] mDragViewVisualCenter = new float[2];
    private float[] mTempDragCoordinates = new float[2];
    private float[] mTempCellLayoutCenterCoordinates = new float[2];
    private float[] mTempDragBottomRightCoordinates = new float[2];
    private Matrix mTempInverseMatrix = new Matrix();

    private SpringLoadedDragController mSpringLoadedDragController;
    private float mSpringLoadedShrinkFactor;

    // State variable that indicates whether the pages are small (ie when you're
    // in all apps or customize mode)

    enum State { NORMAL, SPRING_LOADED, SMALL };
    private State mState = State.NORMAL;
    private boolean mIsSwitchingState = false;
    private boolean mSwitchStateAfterFirstLayout = false;
    private State mStateAfterFirstLayout;

    private AnimatorSet mAnimator;
    private AnimatorListener mChangeStateAnimationListener;

    boolean mAnimatingViewIntoPlace = false;
    boolean mIsDragOccuring = false;
    boolean mChildrenLayersEnabled = true;

    /** Is the user is dragging an item near the edge of a page? */
    private boolean mInScrollArea = false;

    private final HolographicOutlineHelper mOutlineHelper = new HolographicOutlineHelper();
    private Bitmap mDragOutline = null;
    private final Rect mTempRect = new Rect();
    private final int[] mTempXY = new int[2];
    private int mDragViewMultiplyColor;
    private float mOverscrollFade = 0;

    // Paint used to draw external drop outline
    private final Paint mExternalDragOutlinePaint = new Paint();

    // Camera and Matrix used to determine the final position of a neighboring CellLayout
    private final Matrix mMatrix = new Matrix();
    private final Camera mCamera = new Camera();
    private final float mTempFloat2[] = new float[2];

    enum WallpaperVerticalOffset { TOP, MIDDLE, BOTTOM };
    int mWallpaperWidth;
    int mWallpaperHeight;
    WallpaperOffsetInterpolator mWallpaperOffset;
    boolean mUpdateWallpaperOffsetImmediately = false;
    private Runnable mDelayedResizeRunnable;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mWallpaperTravelWidth;

    // Variables relating to the creation of user folders by hovering shortcuts over shortcuts
    private static final int FOLDER_CREATION_TIMEOUT = 250;
    private final Alarm mFolderCreationAlarm = new Alarm();
    private FolderRingAnimator mDragFolderRingAnimator = null;
    private View mLastDragOverView = null;
    private boolean mCreateUserFolderOnDrop = false;

    // Variables relating to touch disambiguation (scrolling workspace vs. scrolling a widget)
    private float mXDown;
    private float mYDown;
    final static float START_DAMPING_TOUCH_SLOP_ANGLE = (float) Math.PI / 6;
    final static float MAX_SWIPE_ANGLE = (float) Math.PI / 3;
    final static float TOUCH_SLOP_DAMPING_FACTOR = 4;

    // These variables are used for storing the initial and final values during workspace animations
    private int mSavedScrollX;
    private float mSavedRotationY;
    private float mSavedTranslationX;
    private float mCurrentScaleX;
    private float mCurrentScaleY;
    private float mCurrentRotationY;
    private float mCurrentTranslationX;
    private float mCurrentTranslationY;
    private float[] mOldTranslationXs;
    private float[] mOldTranslationYs;
    private float[] mOldScaleXs;
    private float[] mOldScaleYs;
    private float[] mOldBackgroundAlphas;
    private float[] mOldBackgroundAlphaMultipliers;
    private float[] mOldAlphas;
    private float[] mOldRotations;
    private float[] mOldRotationYs;
    private float[] mNewTranslationXs;
    private float[] mNewTranslationYs;
    private float[] mNewScaleXs;
    private float[] mNewScaleYs;
    private float[] mNewBackgroundAlphas;
    private float[] mNewBackgroundAlphaMultipliers;
    private float[] mNewAlphas;
    private float[] mNewRotations;
    private float[] mNewRotationYs;
    private float mTransitionProgress;

    public enum TransitionEffect {
        Standard,
        Tablet,
        ZoomIn,
        ZoomOut,
        RotateUp,
        RotateDown,
        CubeIn,
        CubeOut,
        Stack
    }

    // Preferences
    private int mNumberHomescreens;
    private int mDefaultHomescreen;
    private int mScreenPaddingVertical;
    private int mScreenPaddingHorizontal;
    private boolean mShowSearchBar;
    private boolean mResizeAnyWidget;
    private boolean mHideIconLabels;
    private boolean mScrollWallpaper;
    private boolean mShowScrollingIndicator;
    private boolean mFadeScrollingIndicator;
    private boolean mShowDockDivider;
    private TransitionEffect mTransitionEffect;

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     */
    public Workspace(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     * @param defStyle Unused.
     */
    public Workspace(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContentIsRefreshable = false;

        // With workspace, data is available straight from the get-go
        setDataIsReady();

        // We handle our own fading
        mHandleFadeInAdjacentScreens = true;

        mWallpaperManager = WallpaperManager.getInstance(context);

        int cellCountX = context.getResources().getInteger(R.integer.config_workspaceColumns);
        int cellCountY = context.getResources().getInteger(R.integer.config_workspaceRows);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Workspace, defStyle, 0);

        final Resources res = context.getResources();
        if (LauncherApplication.isScreenLarge()) {
            int[] cellCount = getCellCountsForLarge(context);
            cellCountX = cellCount[0];
            cellCountY = cellCount[1];
        }

        mSpringLoadedShrinkFactor =
            res.getInteger(R.integer.config_workspaceSpringLoadShrinkPercentage) / 100.0f;
        mDragViewMultiplyColor = res.getColor(R.color.drag_view_multiply_color);

        // if the value is manually specified, use that instead
        cellCountX = a.getInt(R.styleable.Workspace_cellCountX, cellCountX);
        cellCountY = a.getInt(R.styleable.Workspace_cellCountY, cellCountY);
        a.recycle();

        // if there is a value set it the preferences, use that instead
        if (!LauncherApplication.isScreenLarge()) {
            cellCountX = PreferencesProvider.Interface.Homescreen.getCellCountX(context, cellCountX);
            cellCountY = PreferencesProvider.Interface.Homescreen.getCellCountY(context, cellCountY);
        }

        LauncherModel.updateWorkspaceLayoutCells(cellCountX, cellCountY);
        setHapticFeedbackEnabled(false);

        // Preferences
        mNumberHomescreens = PreferencesProvider.Interface.Homescreen.getNumberHomescreens(context);
        mDefaultHomescreen = PreferencesProvider.Interface.Homescreen.getDefaultHomescreen(context,
                mNumberHomescreens / 2);
        if (mDefaultHomescreen >= mNumberHomescreens) {
            mDefaultHomescreen = mNumberHomescreens / 2;
        }
        mScreenPaddingVertical = PreferencesProvider.Interface.Homescreen.getScreenPaddingVertical(context);
        mScreenPaddingHorizontal = PreferencesProvider.Interface.Homescreen.getScreenPaddingHorizontal(context);
        mShowSearchBar = PreferencesProvider.Interface.Homescreen.getShowSearchBar(context);
        mResizeAnyWidget = PreferencesProvider.Interface.Homescreen.getResizeAnyWidget(context);
        mHideIconLabels = PreferencesProvider.Interface.Homescreen.getHideIconLabels(context);
        mScrollWallpaper = PreferencesProvider.Interface.Homescreen.Scrolling.getScrollWallpaper(context);
        mTransitionEffect = PreferencesProvider.Interface.Homescreen.Scrolling.getTransitionEffect(context,
                res.getString(R.string.config_workspaceDefaultTransitionEffect));
        mFadeInAdjacentScreens = PreferencesProvider.Interface.Homescreen.Scrolling.getFadeInAdjacentScreens(context,
                res.getBoolean(R.bool.config_workspaceDefualtFadeInAdjacentScreens));
        mShowScrollingIndicator = PreferencesProvider.Interface.Homescreen.Indicator.getShowScrollingIndicator(context);
        mFadeScrollingIndicator = PreferencesProvider.Interface.Homescreen.Indicator.getFadeScrollingIndicator(context);
        mShowDockDivider = PreferencesProvider.Interface.Homescreen.Indicator.getShowDockDivider(context);

        mLauncher = (Launcher) context;
        initWorkspace();

        // Disable multitouch across the workspace/all apps/customize tray
        setMotionEventSplittingEnabled(true);
    }

    public static int[] getCellCountsForLarge(Context context) {
        int[] cellCount = new int[2];

        final Resources res = context.getResources();
        // Determine number of rows/columns dynamically
        // TODO: This code currently fails on tablets with an aspect ratio < 1.3.
        // Around that ratio we should make cells the same size in portrait and
        // landscape
        TypedArray actionBarSizeTypedArray =
            context.obtainStyledAttributes(new int[] { android.R.attr.actionBarSize });
        final float actionBarHeight = actionBarSizeTypedArray.getDimension(0, 0f);
        final float systemBarHeight = res.getDimension(R.dimen.status_bar_height);
        final float smallestScreenDim = res.getConfiguration().smallestScreenWidthDp;

        cellCount[0] = 1;
        while (CellLayout.widthInPortrait(res, cellCount[0] + 1) <= smallestScreenDim) {
            cellCount[0]++;
        }

        cellCount[1] = 1;
        while (actionBarHeight + CellLayout.heightInLandscape(res, cellCount[1] + 1)
                <= smallestScreenDim - systemBarHeight) {
            cellCount[1]++;
        }
        return cellCount;
    }

    // estimate the size of a widget with spans hSpan, vSpan. return MAX_VALUE for each
    // dimension if unsuccessful
    public int[] estimateItemSize(int hSpan, int vSpan,
            PendingAddItemInfo pendingItemInfo, boolean springLoaded) {
        int[] size = new int[2];
        if (getChildCount() > 0) {
            CellLayout cl = (CellLayout) mLauncher.getWorkspace().getPageAt(0);
            RectF r = estimateItemPosition(cl, pendingItemInfo, 0, 0, hSpan, vSpan);
            size[0] = (int) r.width();
            size[1] = (int) r.height();
            if (springLoaded) {
                size[0] *= mSpringLoadedShrinkFactor;
                size[1] *= mSpringLoadedShrinkFactor;
            }
            return size;
        } else {
            size[0] = Integer.MAX_VALUE;
            size[1] = Integer.MAX_VALUE;
            return size;
        }
    }
    public RectF estimateItemPosition(CellLayout cl, ItemInfo pendingInfo,
            int hCell, int vCell, int hSpan, int vSpan) {
        RectF r = new RectF();
        cl.cellToRect(hCell, vCell, hSpan, vSpan, r);
        if (pendingInfo instanceof PendingAddWidgetInfo) {
            PendingAddWidgetInfo widgetInfo = (PendingAddWidgetInfo) pendingInfo;
            Rect p = mLauncher.getDefaultPaddingForWidget(mContext,
                    widgetInfo.componentName, null);
            r.top += p.top;
            r.left += p.left;
            r.right -= p.right;
            r.bottom -= p.bottom;
        }
        return r;
    }

    public void buildPageHardwareLayers() {
        if (getWindowToken() != null) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                CellLayout cl = (CellLayout) getPageAt(i);
                cl.buildChildrenLayer();
            }
        }
    }

    public TransitionEffect getTransitionEffect() {
        return mTransitionEffect;
    }

    public State getState() {
        return mState;
    }

    public void onDragStart(DragSource source, Object info, int dragAction) {
        mIsDragOccuring = true;
        updateChildrenLayersEnabled();
        mLauncher.lockScreenOrientationOnLargeUI();
    }

    public void onDragEnd() {
        mIsDragOccuring = false;
        updateChildrenLayersEnabled();
        mLauncher.unlockScreenOrientationOnLargeUI();
    }

    /**
     * Initializes various states for this workspace.
     */
    protected void initWorkspace() {
        Context context = getContext();
        mCurrentPage = mDefaultHomescreen;
        Launcher.setScreen(mCurrentPage);
        LauncherApplication app = (LauncherApplication)context.getApplicationContext();
        mIconCache = app.getIconCache();
        mExternalDragOutlinePaint.setAntiAlias(true);
        setWillNotDraw(false);
        setChildrenDrawnWithCacheEnabled(true);

        final Resources res = getResources();

        LayoutInflater inflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        for (int i = 0; i < mNumberHomescreens; i++) {
            View screen = inflater.inflate(R.layout.workspace_screen, null);
            screen.setPadding(screen.getPaddingLeft() + mScreenPaddingHorizontal,
                    screen.getPaddingTop() + mScreenPaddingVertical,
                    screen.getPaddingRight() + mScreenPaddingHorizontal,
                    screen.getPaddingBottom() + mScreenPaddingVertical);
            addView(screen);        }

        try {
            mBackground = res.getDrawable(R.drawable.apps_customize_bg);
        } catch (Resources.NotFoundException e) {
            // In this case, we will skip drawing background protection
        }

        if (!mShowSearchBar) {
            int paddingTop = 0;
            if (mLauncher.getCurrentOrientation() == Configuration.ORIENTATION_PORTRAIT) {
                paddingTop = (int)res.getDimension(R.dimen.qsb_bar_hidden_inset);
            }
            setPadding(0, paddingTop, getPaddingRight(), getPaddingBottom());
        }

        if (!mShowScrollingIndicator) {
            disableScrollingIndicator();
        }

        mChangeStateAnimationListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsSwitchingState = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mIsSwitchingState = false;
                if (mScrollWallpaper) {
                    mWallpaperOffset.setOverrideHorizontalCatchupConstant(false);
                }
                mAnimator = null;
                updateChildrenLayersEnabled();
            }
        };

        mSnapVelocity = 100;
        Display display = mLauncher.getWindowManager().getDefaultDisplay();
        mDisplayWidth = display.getWidth();
        mDisplayHeight = display.getHeight();
        if (mScrollWallpaper) {
            mWallpaperOffset = new WallpaperOffsetInterpolator();
            mWallpaperTravelWidth = (int) (mDisplayWidth *
                    wallpaperTravelToScreenWidthRatio(mDisplayWidth, mDisplayHeight));
        }
    }

    @Override
    protected void onViewAdded(View child) {
        super.onViewAdded(child);
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        CellLayout cl = ((CellLayout) child);
        cl.setOnInterceptTouchListener(this);
        cl.setClickable(true);
        cl.enableHardwareLayers();
    }

    /**
     * @return The open folder on the current screen, or null if there is none
     */
    Folder getOpenFolder() {
        DragLayer dragLayer = mLauncher.getDragLayer();
        int count = dragLayer.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = dragLayer.getChildAt(i);
            if (child instanceof Folder) {
                Folder folder = (Folder) child;
                if (folder.getInfo().opened)
                    return folder;
            }
        }
        return null;
    }

    boolean isTouchActive() {
        return mTouchState != TOUCH_STATE_REST;
    }

    /**
     * Adds the specified child in the specified screen. The position and dimension of
     * the child are defined by x, y, spanX and spanY.
     *
     * @param child The child to add in one of the workspace's screens.
     * @param screen The screen in which to add the child.
     * @param x The X position of the child in the screen's grid.
     * @param y The Y position of the child in the screen's grid.
     * @param spanX The number of cells spanned horizontally by the child.
     * @param spanY The number of cells spanned vertically by the child.
     */
    void addInScreen(View child, long container, int screen, int x, int y, int spanX, int spanY) {
        addInScreen(child, container, screen, x, y, spanX, spanY, false);
    }

    /**
     * Adds the specified child in the specified screen. The position and dimension of
     * the child are defined by x, y, spanX and spanY.
     *
     * @param child The child to add in one of the workspace's screens.
     * @param screen The screen in which to add the child.
     * @param x The X position of the child in the screen's grid.
     * @param y The Y position of the child in the screen's grid.
     * @param spanX The number of cells spanned horizontally by the child.
     * @param spanY The number of cells spanned vertically by the child.
     * @param insert When true, the child is inserted at the beginning of the children list.
     */
    void addInScreen(View child, long container, int screen, int x, int y, int spanX, int spanY,
            boolean insert) {
        if (container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            if (screen < 0 || screen >= getChildCount()) {
                Log.e(TAG, "The screen must be >= 0 and < " + getChildCount()
                    + " (was " + screen + "); skipping child");
                return;
            }
        }

        final CellLayout layout;
        if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            layout = mLauncher.getHotseat().getLayout();
            child.setOnKeyListener(null);

            if (!mHideIconLabels) {
                // Hide titles in the hotseat
                if (child instanceof FolderIcon) {
                    ((FolderIcon) child).setTextVisible(false);
                } else if (child instanceof BubbleTextView) {
                    ((BubbleTextView) child).setTextVisible(false);
                }
            }

            if (screen < 0) {
                screen = mLauncher.getHotseat().getOrderInHotseat(x, y);
            } else {
                // Note: We do this to ensure that the hotseat is always laid out in the orientation
                // of the hotseat in order regardless of which orientation they were added
                x = mLauncher.getHotseat().getCellXFromOrder(screen);
                y = mLauncher.getHotseat().getCellYFromOrder(screen);
            }
        } else {
            if (!mHideIconLabels) {
                // Show titles if not in the hotseat
                if (child instanceof FolderIcon) {
                    ((FolderIcon) child).setTextVisible(true);
                } else if (child instanceof BubbleTextView) {
                    ((BubbleTextView) child).setTextVisible(true);
                }
            }

            layout = (CellLayout) getPageAt(screen);
            child.setOnKeyListener(new IconKeyEventListener());
        }

        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child.getLayoutParams();
        if (lp == null) {
            lp = new CellLayout.LayoutParams(x, y, spanX, spanY);
        } else {
            lp.cellX = x;
            lp.cellY = y;
            lp.cellHSpan = spanX;
            lp.cellVSpan = spanY;
        }

        if (spanX < 0 && spanY < 0) {
            lp.isLockedToGrid = false;
        }

        // Get the canonical child id to uniquely represent this view in this screen
        int childId = LauncherModel.getCellLayoutChildId(container, screen, x, y, spanX, spanY);
        boolean markCellsAsOccupied = !(child instanceof Folder);
        if (!layout.addViewToCellLayout(child, insert ? 0 : -1, childId, lp, markCellsAsOccupied)) {
            // TODO: This branch occurs when the workspace is adding views
            // outside of the defined grid
            // maybe we should be deleting these items from the LauncherModel?
            Log.w(TAG, "Failed to add to item at (" + lp.cellX + "," + lp.cellY + ") to CellLayout");
        }

        if (!(child instanceof Folder)) {
            child.setHapticFeedbackEnabled(false);
            child.setOnLongClickListener(mLongClickListener);
        }
        if (child instanceof DropTarget) {
            mDragController.addDropTarget((DropTarget) child);
        }
    }

    /**
     * Check if the point (x, y) hits a given page.
     */
    private boolean hitsPage(int index, float x, float y) {
        final View page = getPageAt(index);
        if (page != null) {
            float[] localXY = { x, y };
            mapPointFromSelfToChild(page, localXY);
            return (localXY[0] >= 0 && localXY[0] < page.getWidth()
                    && localXY[1] >= 0 && localXY[1] < page.getHeight());
        }
        return false;
    }

    @Override
    protected boolean hitsPreviousPage(float x, float y) {
        // mNextPage is set to INVALID_PAGE whenever we are stationary.
        // Calculating "next page" this way ensures that you scroll to whatever page you tap on
        final int current = (mNextPage == INVALID_PAGE) ? mCurrentPage : mNextPage;

        // Only allow tap to next page on large devices, where there's significant margin outside
        // the active workspace
        return LauncherApplication.isScreenLarge() && hitsPage(current - 1, x, y);
    }

    @Override
    protected boolean hitsNextPage(float x, float y) {
        // mNextPage is set to INVALID_PAGE whenever we are stationary.
        // Calculating "next page" this way ensures that you scroll to whatever page you tap on
        final int current = (mNextPage == INVALID_PAGE) ? mCurrentPage : mNextPage;

        // Only allow tap to next page on large devices, where there's significant margin outside
        // the active workspace
        return LauncherApplication.isScreenLarge() && hitsPage(current + 1, x, y);
    }

    /**
     * Called directly from a CellLayout (not by the framework), after we've been added as a
     * listener via setOnInterceptTouchEventListener(). This allows us to tell the CellLayout
     * that it should intercept touch events, which is not something that is normally supported.
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return (isSmall() || mIsSwitchingState);
    }

    public boolean isSwitchingState() {
        return mIsSwitchingState;
    }

    protected void onWindowVisibilityChanged (int visibility) {
        mLauncher.onWindowVisibilityChanged(visibility);
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (isSmall() || mIsSwitchingState) {
            // when the home screens are shrunken, shouldn't allow side-scrolling
            return false;
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
            mXDown = ev.getX();
            mYDown = ev.getY();
            break;
        case MotionEvent.ACTION_POINTER_UP:
        case MotionEvent.ACTION_UP:
            if (mTouchState == TOUCH_STATE_REST) {
                final CellLayout currentPage = (CellLayout) getPageAt(mCurrentPage);
                if (!currentPage.lastDownOnOccupiedCell()) {
                    onWallpaperTap(ev);
                }
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev) {
        if (!isSmall() && !mIsSwitchingState) {
            float deltaX = Math.abs(ev.getX() - mXDown);
            float deltaY = Math.abs(ev.getY() - mYDown);

            if (Float.compare(deltaX, 0f) == 0) return;

            float slope = deltaY / deltaX;
            float theta = (float) Math.atan(slope);

            if (deltaX > mTouchSlop || deltaY > mTouchSlop) {
                cancelCurrentPageLongPress();
            }

            if (theta > MAX_SWIPE_ANGLE) {
                // Above MAX_SWIPE_ANGLE, we don't want to ever start scrolling the workspace
                return;
            } else if (theta > START_DAMPING_TOUCH_SLOP_ANGLE) {
                // Above START_DAMPING_TOUCH_SLOP_ANGLE and below MAX_SWIPE_ANGLE, we want to
                // increase the touch slop to make it harder to begin scrolling the workspace. This 
                // results in vertically scrolling widgets to more easily. The higher the angle, the
                // more we increase touch slop.
                theta -= START_DAMPING_TOUCH_SLOP_ANGLE;
                float extraRatio = (float)
                        Math.sqrt((theta / (MAX_SWIPE_ANGLE - START_DAMPING_TOUCH_SLOP_ANGLE)));
                super.determineScrollingStart(ev, 1 + TOUCH_SLOP_DAMPING_FACTOR * extraRatio);
            } else {
                // Below START_DAMPING_TOUCH_SLOP_ANGLE, we don't do anything special
                super.determineScrollingStart(ev);
            }
        }
    }

    @Override
    protected boolean isScrollingIndicatorEnabled() {
        return mState != State.SPRING_LOADED;
    }

    protected void onPageBeginMoving() {
        super.onPageBeginMoving();

        if (isHardwareAccelerated()) {
            updateChildrenLayersEnabled();
        } else {
            if (mNextPage != INVALID_PAGE) {
                // we're snapping to a particular screen
                enableChildrenCache(mCurrentPage, mNextPage);
            } else {
                // this is when user is actively dragging a particular screen, they might
                // swipe it either left or right (but we won't advance by more than one screen)
                enableChildrenCache(mCurrentPage - 1, mCurrentPage + 1);
            }
        }

        // Only show page outlines as we pan if we are on large screen
        if (LauncherApplication.isScreenLarge()) {
            showOutlines();
        }
    }

    protected void onPageEndMoving() {
        if (mFadeScrollingIndicator) {
            hideScrollingIndicator(false);
        }

        if (isHardwareAccelerated()) {
            updateChildrenLayersEnabled();
        } else {
            clearChildrenCache();
        }

        // Hide the outlines, as long as we're not dragging
        if (!mDragController.dragging()) {
            // Only hide page outlines as we pan if we are on large screen
            if (LauncherApplication.isScreenLarge()) {
                hideOutlines();
            }
        }
        mOverScrollMaxBackgroundAlpha = 0.0f;
        mOverScrollPageIndex = -1;

        if (mDelayedResizeRunnable != null) {
            mDelayedResizeRunnable.run();
            mDelayedResizeRunnable = null;
        }
    }

    @Override
    protected void notifyPageSwitchListener() {
        super.notifyPageSwitchListener();
        Launcher.setScreen(mCurrentPage);
    };

    @Override
    protected void flashScrollingIndicator(boolean animated) {
        if (mFadeScrollingIndicator) {
            super.flashScrollingIndicator(animated);
        } else {
            showScrollingIndicator(true);
        }
    }

    // As a ratio of screen height, the total distance we want the parallax effect to span
    // horizontally
    private float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.5 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.2 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (ie travel) at any aspect ratio:

        final float ASPECT_RATIO_LANDSCAPE = 16/10f;
        final float ASPECT_RATIO_PORTRAIT = 10/16f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f;

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        final float x =
            (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
            (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        final float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    // The range of scroll values for Workspace
    private int getScrollRange() {
        return getChildOffset(getChildCount() - 1) - getChildOffset(0);
    }

    protected void setWallpaperDimension() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        mLauncher.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        final int maxDim = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
        final int minDim = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);

        // We need to ensure that there is enough extra space in the wallpaper for the intended
        // parallax effects
        if (LauncherApplication.isScreenLarge()) {
            mWallpaperWidth = (int) (maxDim * wallpaperTravelToScreenWidthRatio(maxDim, minDim));
            mWallpaperHeight = maxDim;
        } else {
            mWallpaperWidth = Math.max((int) (minDim * WALLPAPER_SCREENS_SPAN), maxDim);
            mWallpaperHeight = maxDim;
        }
        new Thread("setWallpaperDimension") {
            public void run() {
                mWallpaperManager.suggestDesiredDimensions(mWallpaperWidth, mWallpaperHeight);
            }
        }.start();
    }

    public void setVerticalWallpaperOffset(float offset) {
        mWallpaperOffset.setFinalY(offset);
    }
    public float getVerticalWallpaperOffset() {
        return mWallpaperOffset.getCurrY();
    }
    public void setHorizontalWallpaperOffset(float offset) {
        mWallpaperOffset.setFinalX(offset);
    }
    public float getHorizontalWallpaperOffset() {
        return mWallpaperOffset.getCurrX();
    }

    private float wallpaperOffsetForCurrentScroll() {
        // The wallpaper travel width is how far, from left to right, the wallpaper will move
        // at this orientation. On tablets in portrait mode we don't move all the way to the
        // edges of the wallpaper, or otherwise the parallax effect would be too strong.
        int wallpaperTravelWidth = mWallpaperWidth;
        if (LauncherApplication.isScreenLarge()) {
            wallpaperTravelWidth = mWallpaperTravelWidth;
        }

        // Set wallpaper offset steps (1 / (number of screens - 1))
        mWallpaperManager.setWallpaperOffsetSteps(1.0f / (getChildCount() - 1), 1.0f);

        // For the purposes of computing the scrollRange and overScrollOffset, we assume
        // that mLayoutScale is 1. This means that when we're in spring-loaded mode,
        // there's no discrepancy between the wallpaper offset for a given page.
        float layoutScale = mLayoutScale;
        mLayoutScale = 1f;
        int scrollRange = getScrollRange();

        // Again, we adjust the wallpaper offset to be consistent between values of mLayoutScale
        float adjustedScrollX = Math.max(0, Math.min(mScrollX, mMaxScrollX));
        adjustedScrollX *= mWallpaperScrollRatio;
        mLayoutScale = layoutScale;

        float scrollProgress =
            adjustedScrollX / (float) scrollRange;
        float offsetInDips = wallpaperTravelWidth * scrollProgress +
            (mWallpaperWidth - wallpaperTravelWidth) / 2; // center it
        float offset = offsetInDips / (float) mWallpaperWidth;
        return offset;
    }
    private void syncWallpaperOffsetWithScroll() {
        if (mScrollWallpaper) {
            mWallpaperOffset.setFinalX(wallpaperOffsetForCurrentScroll());
        }
    }

    private void centerWallpaperOffset() {
        mWallpaperManager.setWallpaperOffsetSteps(0.5f, 0);
        mWallpaperManager.setWallpaperOffsets(getWindowToken(), 0.5f, 0);
    }

    public void updateWallpaperOffsetImmediately() {
        mUpdateWallpaperOffsetImmediately = true;
    }

    private void updateWallpaperOffsets() {
        boolean updateNow = false;
        boolean keepUpdating = true;
        if (mUpdateWallpaperOffsetImmediately) {
            updateNow = true;
            keepUpdating = false;
            mWallpaperOffset.jumpToFinal();
            mUpdateWallpaperOffsetImmediately = false;
        } else {
            updateNow = keepUpdating = mWallpaperOffset.computeScrollOffset();
        }
        if (updateNow) {
            if (mWindowToken != null) {
                mWallpaperManager.setWallpaperOffsets(mWindowToken,
                        mWallpaperOffset.getCurrX(), mWallpaperOffset.getCurrY());
            }
        }
        if (keepUpdating) {
            fastInvalidate();
        }
    }

    @Override
    protected void updateCurrentPageScroll() {
        super.updateCurrentPageScroll();
        if (mScrollWallpaper) {
            computeWallpaperScrollRatio(mCurrentPage);
        }
    }

    @Override
    protected void snapToPage(int whichPage) {
        super.snapToPage(whichPage);
        if (mScrollWallpaper) {
            computeWallpaperScrollRatio(whichPage);
        }
    }

    private void computeWallpaperScrollRatio(int page) {
        // Here, we determine what the desired scroll would be with and without a layout scale,
        // and compute a ratio between the two. This allows us to adjust the wallpaper offset
        // as though there is no layout scale.
        float layoutScale = mLayoutScale;
        int scaled = getChildOffset(page) - getRelativeChildOffset(page);
        mLayoutScale = 1.0f;
        float unscaled = getChildOffset(page) - getRelativeChildOffset(page);
        mLayoutScale = layoutScale;
        if (scaled > 0) {
            mWallpaperScrollRatio = (1.0f * unscaled) / scaled;
        } else {
            mWallpaperScrollRatio = 1f;
        }
    }

    class WallpaperOffsetInterpolator {
        float mFinalHorizontalWallpaperOffset = 0.0f;
        float mFinalVerticalWallpaperOffset = 0.5f;
        float mHorizontalWallpaperOffset = 0.0f;
        float mVerticalWallpaperOffset = 0.5f;
        long mLastWallpaperOffsetUpdateTime;
        boolean mIsMovingFast;
        boolean mOverrideHorizontalCatchupConstant;
        float mHorizontalCatchupConstant = 0.35f;
        float mVerticalCatchupConstant = 0.35f;

        public WallpaperOffsetInterpolator() {
        }

        public void setOverrideHorizontalCatchupConstant(boolean override) {
            mOverrideHorizontalCatchupConstant = override;
        }

        public void setHorizontalCatchupConstant(float f) {
            mHorizontalCatchupConstant = f;
        }

        public void setVerticalCatchupConstant(float f) {
            mVerticalCatchupConstant = f;
        }

        public boolean computeScrollOffset() {
            if (Float.compare(mHorizontalWallpaperOffset, mFinalHorizontalWallpaperOffset) == 0 &&
                    Float.compare(mVerticalWallpaperOffset, mFinalVerticalWallpaperOffset) == 0) {
                mIsMovingFast = false;
                return false;
            }
            boolean isLandscape = mDisplayWidth > mDisplayHeight;

            long currentTime = System.currentTimeMillis();
            long timeSinceLastUpdate = currentTime - mLastWallpaperOffsetUpdateTime;
            timeSinceLastUpdate = Math.min((long) (1000/30f), timeSinceLastUpdate);
            timeSinceLastUpdate = Math.max(1L, timeSinceLastUpdate);

            float xdiff = Math.abs(mFinalHorizontalWallpaperOffset - mHorizontalWallpaperOffset);
            if (!mIsMovingFast && xdiff > 0.07) {
                mIsMovingFast = true;
            }

            float fractionToCatchUpIn1MsHorizontal;
            if (mOverrideHorizontalCatchupConstant) {
                fractionToCatchUpIn1MsHorizontal = mHorizontalCatchupConstant;
            } else if (mIsMovingFast) {
                fractionToCatchUpIn1MsHorizontal = isLandscape ? 0.5f : 0.75f;
            } else {
                // slow
                fractionToCatchUpIn1MsHorizontal = isLandscape ? 0.27f : 0.5f;
            }
            float fractionToCatchUpIn1MsVertical = mVerticalCatchupConstant;

            fractionToCatchUpIn1MsHorizontal /= 33f;
            fractionToCatchUpIn1MsVertical /= 33f;

            final float UPDATE_THRESHOLD = 0.00001f;
            float hOffsetDelta = mFinalHorizontalWallpaperOffset - mHorizontalWallpaperOffset;
            float vOffsetDelta = mFinalVerticalWallpaperOffset - mVerticalWallpaperOffset;
            boolean jumpToFinalValue = Math.abs(hOffsetDelta) < UPDATE_THRESHOLD &&
                Math.abs(vOffsetDelta) < UPDATE_THRESHOLD;

            // Don't have any lag between workspace and wallpaper on non-large devices
            if (!LauncherApplication.isScreenLarge() || jumpToFinalValue) {
                mHorizontalWallpaperOffset = mFinalHorizontalWallpaperOffset;
                mVerticalWallpaperOffset = mFinalVerticalWallpaperOffset;
            } else {
                float percentToCatchUpVertical =
                    Math.min(1.0f, timeSinceLastUpdate * fractionToCatchUpIn1MsVertical);
                float percentToCatchUpHorizontal =
                    Math.min(1.0f, timeSinceLastUpdate * fractionToCatchUpIn1MsHorizontal);
                mHorizontalWallpaperOffset += percentToCatchUpHorizontal * hOffsetDelta;
                mVerticalWallpaperOffset += percentToCatchUpVertical * vOffsetDelta;
            }

            mLastWallpaperOffsetUpdateTime = System.currentTimeMillis();
            return true;
        }

        public float getCurrX() {
            return mHorizontalWallpaperOffset;
        }

        public float getFinalX() {
            return mFinalHorizontalWallpaperOffset;
        }

        public float getCurrY() {
            return mVerticalWallpaperOffset;
        }

        public float getFinalY() {
            return mFinalVerticalWallpaperOffset;
        }

        public void setFinalX(float x) {
            mFinalHorizontalWallpaperOffset = Math.max(0f, Math.min(x, 1.0f));
        }

        public void setFinalY(float y) {
            mFinalVerticalWallpaperOffset = Math.max(0f, Math.min(y, 1.0f));
        }

        public void jumpToFinal() {
            mHorizontalWallpaperOffset = mFinalHorizontalWallpaperOffset;
            mVerticalWallpaperOffset = mFinalVerticalWallpaperOffset;
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScrollWallpaper) {
            syncWallpaperOffsetWithScroll();
        }
    }

    void showOutlines() {
        if (!isSmall() && !mIsSwitchingState) {
            if (mChildrenOutlineFadeOutAnimation != null) mChildrenOutlineFadeOutAnimation.cancel();
            if (mChildrenOutlineFadeInAnimation != null) mChildrenOutlineFadeInAnimation.cancel();
            mChildrenOutlineFadeInAnimation = ObjectAnimator.ofFloat(this, "childrenOutlineAlpha", 1.0f);
            mChildrenOutlineFadeInAnimation.setDuration(CHILDREN_OUTLINE_FADE_IN_DURATION);
            mChildrenOutlineFadeInAnimation.start();
        }
    }

    void hideOutlines() {
        if (!isSmall() && !mIsSwitchingState) {
            if (mChildrenOutlineFadeInAnimation != null) mChildrenOutlineFadeInAnimation.cancel();
            if (mChildrenOutlineFadeOutAnimation != null) mChildrenOutlineFadeOutAnimation.cancel();
            mChildrenOutlineFadeOutAnimation = ObjectAnimator.ofFloat(this, "childrenOutlineAlpha", 0.0f);
            mChildrenOutlineFadeOutAnimation.setDuration(CHILDREN_OUTLINE_FADE_OUT_DURATION);
            mChildrenOutlineFadeOutAnimation.setStartDelay(CHILDREN_OUTLINE_FADE_OUT_DELAY);
            mChildrenOutlineFadeOutAnimation.start();
        }
    }

    public void showOutlinesTemporarily() {
        if (!mIsPageMoving && !isTouchActive()) {
            snapToPage(mCurrentPage);
        }
    }

    public void setChildrenOutlineAlpha(float alpha) {
        mChildrenOutlineAlpha = alpha;
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            cl.setBackgroundAlpha(alpha);
        }
    }

    public float getChildrenOutlineAlpha() {
        return mChildrenOutlineAlpha;
    }

    void disableBackground() {
        mDrawBackground = false;
    }
    void enableBackground() {
        mDrawBackground = true;
    }

    private void animateBackgroundGradient(float finalAlpha, boolean animated) {
        if (mBackground == null) return;
        if (mBackgroundFadeInAnimation != null) {
            mBackgroundFadeInAnimation.cancel();
            mBackgroundFadeInAnimation = null;
        }
        if (mBackgroundFadeOutAnimation != null) {
            mBackgroundFadeOutAnimation.cancel();
            mBackgroundFadeOutAnimation = null;
        }
        float startAlpha = getBackgroundAlpha();
        if (finalAlpha != startAlpha) {
            if (animated) {
                mBackgroundFadeOutAnimation = ValueAnimator.ofFloat(startAlpha, finalAlpha);
                mBackgroundFadeOutAnimation.addUpdateListener(new AnimatorUpdateListener() {
                    public void onAnimationUpdate(ValueAnimator animation) {
                        setBackgroundAlpha(((Float) animation.getAnimatedValue()).floatValue());
                    }
                });
                mBackgroundFadeOutAnimation.setInterpolator(new DecelerateInterpolator(1.5f));
                mBackgroundFadeOutAnimation.setDuration(BACKGROUND_FADE_OUT_DURATION);
                mBackgroundFadeOutAnimation.start();
            } else {
                setBackgroundAlpha(finalAlpha);
            }
        }
    }

    public void setBackgroundAlpha(float alpha) {
        if (alpha != mBackgroundAlpha) {
            mBackgroundAlpha = alpha;
            invalidate();
        }
    }

    public float getBackgroundAlpha() {
        return mBackgroundAlpha;
    }

    /**
     * Due to 3D transformations, if two CellLayouts are theoretically touching each other,
     * on the xy plane, when one is rotated along the y-axis, the gap between them is perceived
     * as being larger. This method computes what offset the rotated view should be translated
     * in order to minimize this perceived gap.
     * @param degrees Angle of the view
     * @param width Width of the view
     * @param height Height of the view
     * @return Offset to be used in a View.setTranslationX() call
     */
    protected float getOffsetXForRotation(float degrees, int width, int height) {
        mMatrix.reset();
        mCamera.save();
        mCamera.rotateY(Math.abs(degrees));
        mCamera.getMatrix(mMatrix);
        mCamera.restore();

        mMatrix.preTranslate(-width * 0.5f, -height * 0.5f);
        mMatrix.postTranslate(width * 0.5f, height * 0.5f);
        mTempFloat2[0] = width;
        mTempFloat2[1] = height;
        mMatrix.mapPoints(mTempFloat2);
        return (width - mTempFloat2[0]) * (degrees > 0.0f ? 1.0f : -1.0f);
    }

    float backgroundAlphaInterpolator(float r) {
        float pivotA = 0.1f;
        float pivotB = 0.4f;
        if (r < pivotA) {
            return 0;
        } else if (r > pivotB) {
            return 1.0f;
        } else {
            return (r - pivotA)/(pivotB - pivotA);
        }
    }

    float overScrollBackgroundAlphaInterpolator(float r) {
        float threshold = 0.08f;

        if (r > mOverScrollMaxBackgroundAlpha) {
            mOverScrollMaxBackgroundAlpha = r;
        } else if (r < mOverScrollMaxBackgroundAlpha) {
            r = mOverScrollMaxBackgroundAlpha;
        }

        return Math.min(r / threshold, 1.0f);
    }

    private void screenScrolledStandard(int screenScroll) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                if (mFadeInAdjacentScreens && !isSmall()) {
                    float alpha = 1 - Math.abs(scrollProgress);
                    cl.setFastAlpha(alpha);
                    cl.fastInvalidate();
                }
            }
        }
    }

    private void screenScrolledTablet(int screenScroll) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float rotation = WORKSPACE_ROTATION * scrollProgress;
                float translationX = getOffsetXForRotation(rotation, cl.getWidth(), cl.getHeight());

                cl.setFastTranslationX(translationX);
                cl.setFastRotationY(rotation);
                if (mFadeInAdjacentScreens && !isSmall()) {
                    float alpha = 1 - Math.abs(scrollProgress);
                    cl.setFastAlpha(alpha);
                }
                cl.fastInvalidate();
            }
        }
        invalidate();
    }

    private void screenScrolledZoom(int screenScroll, boolean in) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float scale = 1.0f + (in ? -0.2f : 0.1f) * Math.abs(scrollProgress);

                // Extra translation to account for the increase in size
                if (!in) {
                    float translationX = cl.getMeasuredWidth() * 0.1f * -scrollProgress;
                    cl.setFastTranslationX(translationX);
                }

                cl.setFastScaleX(scale);
                cl.setFastScaleY(scale);
                if (mFadeInAdjacentScreens && !isSmall()) {
                    float alpha = 1 - Math.abs(scrollProgress);
                    cl.setFastAlpha(alpha);
                }
                cl.fastInvalidate();
            }
        }
    }

    private void screenScrolledRotate(int screenScroll, boolean up) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float rotation =
                        (up ? WORKSPACE_ROTATION_ANGLE : -WORKSPACE_ROTATION_ANGLE) * scrollProgress;
                float translationX = cl.getMeasuredWidth() * scrollProgress;

                float rotatePoint =
                        (cl.getMeasuredWidth() * 0.5f) /
                        (float) Math.tan(Math.toRadians((double) (WORKSPACE_ROTATION_ANGLE * 0.5f)));

                cl.setPivotX(cl.getMeasuredWidth() * 0.5f);
                if (up) {
                    cl.setPivotY(-rotatePoint);
                } else {
                    cl.setPivotY(cl.getMeasuredHeight() + rotatePoint);
                }

                cl.setRotation(rotation);
                cl.setFastTranslationX(translationX);
                if (mFadeInAdjacentScreens && !isSmall()) {
                    float alpha = 1 - Math.abs(scrollProgress);
                    cl.setFastAlpha(alpha);
                }
                cl.fastInvalidate();
            }
        }
    }

    private void screenScrolledCube(int screenScroll, boolean in) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float rotation = (in ? 90.0f : -90.0f) * scrollProgress;
                float alpha = 1 - Math.abs(scrollProgress);

                if (in) {
                    cl.setCameraDistance(mDensity * CAMERA_DISTANCE);
                }

                cl.setPivotX(scrollProgress < 0 ? 0 : cl.getMeasuredWidth());
                cl.setPivotY(cl.getMeasuredHeight() * 0.5f);
                cl.setRotationY(rotation);
                cl.setFastAlpha(alpha);
                cl.fastInvalidate();
            }
        }
    }

    private void screenScrolledStack(int screenScroll) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout cl = (CellLayout) getPageAt(i);
            if (cl != null) {
                float scrollProgress = getScrollProgress(screenScroll, cl, i);
                float interpolatedProgress =
                        mZInterpolator.getInterpolation(Math.abs(Math.min(scrollProgress, 0)));
                float scale = (1 - interpolatedProgress) + interpolatedProgress * 0.76f;
                float translationX = Math.min(0, scrollProgress) * cl.getMeasuredWidth();
                float alpha;

                if (!LauncherApplication.isScreenLarge() || scrollProgress < 0) {
                    alpha = scrollProgress < 0 ? mAlphaInterpolator.getInterpolation(
                        1 - Math.abs(scrollProgress)) : 1.0f;
                } else {
                    // On large screens we need to fade the page as it nears its leftmost position
                    alpha = mLeftScreenAlphaInterpolator.getInterpolation(1 - scrollProgress);
                }

                cl.setFastTranslationX(translationX);
                cl.setFastScaleX(scale);
                cl.setFastScaleY(scale);
                cl.setFastAlpha(alpha);

                // If the view has 0 alpha, we set it to be invisible so as to prevent
                // it from accepting touches
                if (alpha <= 0) {
                    cl.setVisibility(INVISIBLE);
                } else if (cl.getVisibility() != VISIBLE) {
                    cl.setVisibility(VISIBLE);
                }
                cl.fastInvalidate();
            }
        }
        invalidate();
    }

    @Override
    protected void screenScrolled(int screenScroll) {
        super.screenScrolled(screenScroll);
        if (isSwitchingState()) return;
        if (isSmall()) {
            for (int i = 0; i < getChildCount(); i++) {
                CellLayout cl = (CellLayout) getPageAt(i);
                if (cl != null) {
                    float scrollProgress = getScrollProgress(screenScroll, cl, i);
                    float rotation = WORKSPACE_ROTATION * scrollProgress;
                    cl.setFastTranslationX(0.0f);
                    cl.setFastRotationY(rotation);
                    cl.fastInvalidate();
                }
            }
        } else if (mOverScrollX < 0 || mOverScrollX > mMaxScrollX) {
            int index = mOverScrollX < 0 ? 0 : getChildCount() - 1;
            CellLayout cl = (CellLayout) getPageAt(index);
            if (!LauncherApplication.isScreenLarge()) {
                if (cl != null) {
                    float scrollProgress = getScrollProgress(screenScroll, cl, index);
                    cl.setOverScrollAmount(Math.abs(scrollProgress), index == 0);
                    float rotation = - WORKSPACE_OVERSCROLL_ROTATION * scrollProgress;
                    cl.setCameraDistance(mDensity * CAMERA_DISTANCE);
                    cl.setPivotX(cl.getMeasuredWidth() * (index == 0 ? 0.75f : 0.25f));
                    cl.setPivotY(cl.getMeasuredHeight() * 0.5f);
                    cl.setRotationY(rotation);
                    cl.setTranslationX(0.0f);
                    cl.setOverscrollTransformsDirty(true);
                    setFadeForOverScroll(Math.abs(scrollProgress));
                }
            } else {
                if (cl != null) {
                    float scrollProgress = getScrollProgress(screenScroll, cl, index);
                    cl.setOverScrollAmount(Math.abs(scrollProgress), index == 0);
                    float rotation = - WORKSPACE_ROTATION * scrollProgress;
                    cl.setBackgroundAlphaMultiplier(
                            overScrollBackgroundAlphaInterpolator(Math.abs(scrollProgress)));
                    mOverScrollPageIndex = index;
                    cl.setPivotX(cl.getMeasuredWidth() * (index == 0 ? 0.75f : 0.25f));
                    cl.setPivotY(cl.getMeasuredHeight() * 0.5f);
                    cl.setOverscrollTransformsDirty(true);
                }
            }
        } else {
            if (LauncherApplication.isScreenLarge()) {
                for (int i = 0; i < getChildCount(); i++) {
                    if (mOverScrollPageIndex != i) {
                        CellLayout cl = (CellLayout) getPageAt(i);
                        if (cl != null) {
                            float scrollProgress = getScrollProgress(screenScroll, cl, i);
                            cl.setBackgroundAlphaMultiplier(backgroundAlphaInterpolator(Math.abs(scrollProgress)));
                        }
                    }
                }
            }

            if (mOverscrollFade != 0) {
                setFadeForOverScroll(0);
            }

            // Reset transforms when we aren't in overscroll
            if (!isSwitchingState()) {
                ((CellLayout) getPageAt(0)).resetOverscrollTransforms();
                ((CellLayout) getPageAt(getChildCount() - 1)).resetOverscrollTransforms();
            }
            switch (mTransitionEffect) {
                case Standard:
                    screenScrolledStandard(screenScroll);
                    break;
                case Tablet:
                    screenScrolledTablet(screenScroll);
                    break;
                case ZoomIn:
                    screenScrolledZoom(screenScroll, true);
                    break;
                case ZoomOut:
                    screenScrolledZoom(screenScroll, false);
                    break;
                case RotateUp:
                    screenScrolledRotate(screenScroll, true);
                    break;
                case RotateDown:
                    screenScrolledRotate(screenScroll, false);
                    break;
                case CubeIn:
                    screenScrolledCube(screenScroll, true);
                    break;
                case CubeOut:
                    screenScrolledCube(screenScroll, false);
                    break;
                case Stack:
                    screenScrolledStack(screenScroll);
                    break;
            }
        }
    }

    @Override
    protected void overScroll(float amount) {
        if (LauncherApplication.isScreenLarge()) {
            dampedOverScroll(amount);
        } else {
            acceleratedOverScroll(amount);
        }
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mWindowToken = getWindowToken();
        computeScroll();
        mDragController.setWindowToken(mWindowToken);
    }

    protected void onDetachedFromWindow() {
        mWindowToken = null;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < getChildCount()) {
            mUpdateWallpaperOffsetImmediately = true;
        }
        super.onLayout(changed, left, top, right, bottom);

        // if shrinkToBottom() is called on initialization, it has to be deferred
        // until after the first call to onLayout so that it has the correct width
        if (mSwitchStateAfterFirstLayout) {
            mSwitchStateAfterFirstLayout = false;
            // shrink can trigger a synchronous onLayout call, so we
            // post this to avoid a stack overflow / tangled onLayout calls
            post(new Runnable() {
                public void run() {
                    changeState(mStateAfterFirstLayout, false);
                }
            });
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mScrollWallpaper) {
            updateWallpaperOffsets();
        }

        // Draw the background gradient if necessary
        if (mBackground != null && mBackgroundAlpha > 0.0f && mDrawBackground) {
            int alpha = (int) (mBackgroundAlpha * 255);
            mBackground.setAlpha(alpha);
            mBackground.setBounds(mScrollX, 0, mScrollX + getMeasuredWidth(),
                    getMeasuredHeight());
            mBackground.draw(canvas);
        }

        super.onDraw(canvas);
    }

    boolean isDrawingBackgroundGradient() {
        return (mBackground != null && mBackgroundAlpha > 0.0f && mDrawBackground);
    }

    public void scrollTo (int x, int y) {
        super.scrollTo(x, y);
        syncChildrenLayersEnabledOnVisiblePages();
    }

    // This method just applies the value mChildrenLayersEnabled to all the pages that
    // will be rendered on the next frame.
    // We do this because calling setChildrenLayersEnabled on a view that's not
    // visible/rendered causes slowdowns on some graphics cards
    private void syncChildrenLayersEnabledOnVisiblePages() {
        if (mChildrenLayersEnabled) {
            getVisiblePages(mTempVisiblePagesRange);
            final int leftScreen = mTempVisiblePagesRange[0];
            final int rightScreen = mTempVisiblePagesRange[1];
            if (leftScreen != -1 && rightScreen != -1) {
                for (int i = leftScreen; i <= rightScreen; i++) {
                    ViewGroup page = (ViewGroup) getPageAt(i);
                    if (page.getVisibility() == VISIBLE &&
                            page.getAlpha() > ViewConfiguration.ALPHA_THRESHOLD) {
                        ((ViewGroup)getPageAt(i)).setChildrenLayersEnabled(true);
                    }
                }
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mInScrollArea && !LauncherApplication.isScreenLarge()) {
            final int width = getWidth();
            final int height = getHeight();
            final int pageHeight = getPageAt(0).getHeight();

            // Set the height of the outline to be the height of the page
            final int offset = (height - pageHeight - mPaddingTop - mPaddingBottom) / 2;
            final int paddingTop = mPaddingTop + offset;
            final int paddingBottom = mPaddingBottom + offset;

            final CellLayout leftPage = (CellLayout) getPageAt(mCurrentPage - 1);
            final CellLayout rightPage = (CellLayout) getPageAt(mCurrentPage + 1);

            if (leftPage != null && leftPage.getIsDragOverlapping()) {
                final Drawable d = getResources().getDrawable(R.drawable.page_hover_left_holo);
                d.setBounds(mScrollX, paddingTop, mScrollX + d.getIntrinsicWidth(),
                        height - paddingBottom);
                d.draw(canvas);
            } else if (rightPage != null && rightPage.getIsDragOverlapping()) {
                final Drawable d = getResources().getDrawable(R.drawable.page_hover_right_holo);
                d.setBounds(mScrollX + width - d.getIntrinsicWidth(), paddingTop, mScrollX + width,
                        height - paddingBottom);
                d.draw(canvas);
            }
        }
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (!mLauncher.isAllAppsVisible()) {
            final Folder openFolder = getOpenFolder();
            if (openFolder != null) {
                return openFolder.requestFocus(direction, previouslyFocusedRect);
            } else {
                return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
            }
        }
        return false;
    }

    @Override
    public int getDescendantFocusability() {
        if (isSmall()) {
            return ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        }
        return super.getDescendantFocusability();
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (!mLauncher.isAllAppsVisible()) {
            final Folder openFolder = getOpenFolder();
            if (openFolder != null) {
                openFolder.addFocusables(views, direction);
            } else {
                super.addFocusables(views, direction, focusableMode);
            }
        }
    }

    public boolean isSmall() {
        return mState == State.SMALL || mState == State.SPRING_LOADED;
    }

    void enableChildrenCache(int fromPage, int toPage) {
        if (fromPage > toPage) {
            final int temp = fromPage;
            fromPage = toPage;
            toPage = temp;
        }

        final int screenCount = getChildCount();

        fromPage = Math.max(fromPage, 0);
        toPage = Math.min(toPage, screenCount - 1);

        for (int i = fromPage; i <= toPage; i++) {
            final CellLayout layout = (CellLayout) getPageAt(i);
            layout.setChildrenDrawnWithCacheEnabled(true);
            layout.setChildrenDrawingCacheEnabled(true);
        }
    }

    void clearChildrenCache() {
        final int screenCount = getChildCount();
        for (int i = 0; i < screenCount; i++) {
            final CellLayout layout = (CellLayout) getPageAt(i);
            layout.setChildrenDrawnWithCacheEnabled(false);
            // In software mode, we don't want the items to continue to be drawn into bitmaps
            if (!isHardwareAccelerated()) {
                layout.setChildrenDrawingCacheEnabled(false);
            }
        }
    }

    private void updateChildrenLayersEnabled() {
        boolean small = isSmall() || mIsSwitchingState;
        boolean dragging = mAnimatingViewIntoPlace || mIsDragOccuring;
        boolean enableChildrenLayers = small || dragging || isPageMoving();

        if (enableChildrenLayers != mChildrenLayersEnabled) {
            mChildrenLayersEnabled = enableChildrenLayers;
            // calling setChildrenLayersEnabled on a view that's not visible/rendered
            // causes slowdowns on some graphics cards, so we only disable it here and leave
            // the enabling to dispatchDraw
            if (!enableChildrenLayers) {
                for (int i = 0; i < getPageCount(); i++) {
                    ((ViewGroup)getPageAt(i)).setChildrenLayersEnabled(false);
                }
            }
        }
    }

    protected void onWallpaperTap(MotionEvent ev) {
        final int[] position = mTempCell;
        getLocationOnScreen(position);

        int pointerIndex = ev.getActionIndex();
        position[0] += (int) ev.getX(pointerIndex);
        position[1] += (int) ev.getY(pointerIndex);

        mWallpaperManager.sendWallpaperCommand(getWindowToken(),
                ev.getAction() == MotionEvent.ACTION_UP
                        ? WallpaperManager.COMMAND_TAP : WallpaperManager.COMMAND_SECONDARY_TAP,
                position[0], position[1], 0, null);
    }

    /*
     * This interpolator emulates the rate at which the perceived scale of an object changes
     * as its distance from a camera increases. When this interpolator is applied to a scale
     * animation on a view, it evokes the sense that the object is shrinking due to moving away
     * from the camera. 
     */
    static class ZInterpolator implements TimeInterpolator {
        private float focalLength;

        public ZInterpolator(float foc) {
            focalLength = foc;
        }

        public float getInterpolation(float input) {
            return (1.0f - focalLength / (focalLength + input)) /
                (1.0f - focalLength / (focalLength + 1.0f));
        }
    }

    /*
     * The exact reverse of ZInterpolator.
     */
    static class InverseZInterpolator implements TimeInterpolator {
        private ZInterpolator zInterpolator;
        public InverseZInterpolator(float foc) {
            zInterpolator = new ZInterpolator(foc);
        }
        public float getInterpolation(float input) {
            return 1 - zInterpolator.getInterpolation(1 - input);
        }
    }

    /*
     * ZInterpolator compounded with an ease-out.
     */
    static class ZoomOutInterpolator implements TimeInterpolator {
        private final DecelerateInterpolator decelerate = new DecelerateInterpolator(0.75f);
        private final ZInterpolator zInterpolator = new ZInterpolator(0.13f);

        public float getInterpolation(float input) {
            return decelerate.getInterpolation(zInterpolator.getInterpolation(input));
        }
    }

    /*
     * InvereZInterpolator compounded with an ease-out.
     */
    static class ZoomInInterpolator implements TimeInterpolator {
        private final InverseZInterpolator inverseZInterpolator = new InverseZInterpolator(0.35f);
        private final DecelerateInterpolator decelerate = new DecelerateInterpolator(3.0f);

        public float getInterpolation(float input) {
            return decelerate.getInterpolation(inverseZInterpolator.getInterpolation(input));
        }
    }

    private final ZoomInInterpolator mZoomInInterpolator = new ZoomInInterpolator();
    private final ZInterpolator mZInterpolator = new ZInterpolator(0.5f);
    private AccelerateInterpolator mAlphaInterpolator = new AccelerateInterpolator(0.9f);
    private DecelerateInterpolator mLeftScreenAlphaInterpolator = new DecelerateInterpolator(4);


    /*
    *
    * We call these methods (onDragStartedWithItemSpans/onDragStartedWithSize) whenever we
    * start a drag in Launcher, regardless of whether the drag has ever entered the Workspace
    *
    * These methods mark the appropriate pages as accepting drops (which alters their visual
    * appearance).
    *
    */
    public void onDragStartedWithItem(View v) {
        final Canvas canvas = new Canvas();

        // We need to add extra padding to the bitmap to make room for the glow effect
        final int bitmapPadding = HolographicOutlineHelper.MAX_OUTER_BLUR_RADIUS;

        // The outline is used to visualize where the item will land if dropped
        mDragOutline = createDragOutline(v, canvas, bitmapPadding);
    }

    public void onDragStartedWithItem(PendingAddItemInfo info, Bitmap b, Paint alphaClipPaint) {
        final Canvas canvas = new Canvas();

        // We need to add extra padding to the bitmap to make room for the glow effect
        final int bitmapPadding = HolographicOutlineHelper.MAX_OUTER_BLUR_RADIUS;

        int[] size = estimateItemSize(info.spanX, info.spanY, info, false);

        // The outline is used to visualize where the item will land if dropped
        mDragOutline = createDragOutline(b, canvas, bitmapPadding, size[0], size[1], alphaClipPaint);
    }

    // we call this method whenever a drag and drop in Launcher finishes, even if Workspace was
    // never dragged over
    public void onDragStopped(boolean success) {
        // In the success case, DragController has already called onDragExit()
        if (!success) {
            doDragExit(null);
        }
    }

    public void exitWidgetResizeMode() {
        DragLayer dragLayer = mLauncher.getDragLayer();
        dragLayer.clearAllResizeFrames();
    }

    private void initAnimationArrays() {
        final int childCount = getChildCount();
        if (mOldTranslationXs != null) return;
        mOldTranslationXs = new float[childCount];
        mOldTranslationYs = new float[childCount];
        mOldScaleXs = new float[childCount];
        mOldScaleYs = new float[childCount];
        mOldBackgroundAlphas = new float[childCount];
        mOldBackgroundAlphaMultipliers = new float[childCount];
        mOldAlphas = new float[childCount];
        mOldRotations = new float[childCount];
        mOldRotationYs = new float[childCount];
        mNewTranslationXs = new float[childCount];
        mNewTranslationYs = new float[childCount];
        mNewScaleXs = new float[childCount];
        mNewScaleYs = new float[childCount];
        mNewBackgroundAlphas = new float[childCount];
        mNewBackgroundAlphaMultipliers = new float[childCount];
        mNewAlphas = new float[childCount];
        mNewRotations = new float[childCount];
        mNewRotationYs = new float[childCount];
    }

    public void changeState(State shrinkState) {
        changeState(shrinkState, true);
    }

    void changeState(final State state, boolean animated) {
        changeState(state, animated, 0);
    }

    void changeState(final State state, boolean animated, int delay) {
        if (mState == state) {
            return;
        }
        if (mFirstLayout) {
            // (mFirstLayout == "first layout has not happened yet")
            // cancel any pending shrinks that were set earlier
            mSwitchStateAfterFirstLayout = false;
            mStateAfterFirstLayout = state;
            return;
        }

        // Initialize animation arrays for the first time if necessary
        initAnimationArrays();

        // Cancel any running transition animations
        if (mAnimator != null) mAnimator.cancel();
        mAnimator = new AnimatorSet();

        // Stop any scrolling, move to the current page right away
        setCurrentPage((mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage);

        final State oldState = mState;
        final boolean oldStateIsNormal = (oldState == State.NORMAL);
        final boolean oldStateIsSmall = (oldState == State.SMALL);
        mState = state;
        final boolean stateIsNormal = (state == State.NORMAL);
        final boolean stateIsSpringLoaded = (state == State.SPRING_LOADED);
        final boolean stateIsSmall = (state == State.SMALL);
        float finalScaleFactor = 1.0f;
        float finalBackgroundAlpha = stateIsSpringLoaded ? 1.0f : 0f;
        float translationX = 0;
        float translationY = 0;
        boolean zoomIn = true;

        if (state != State.NORMAL) {
            finalScaleFactor = mSpringLoadedShrinkFactor - (stateIsSmall ? 0.1f : 0);
            if (oldStateIsNormal && stateIsSmall) {
                zoomIn = false;
                if (animated) {
                    hideScrollingIndicator(false, sScrollIndicatorFadeOutShortDuration);
                }
                setLayoutScale(finalScaleFactor);
                updateChildrenLayersEnabled();
            } else {
                finalBackgroundAlpha = 1.0f;
                setLayoutScale(finalScaleFactor);
            }
        } else {
            setLayoutScale(1.0f);
        }

        final int duration = zoomIn ? 
                getResources().getInteger(R.integer.config_workspaceUnshrinkTime) :
                getResources().getInteger(R.integer.config_appsCustomizeWorkspaceShrinkTime);
        for (int i = 0; i < getChildCount(); i++) {
            final CellLayout cl = (CellLayout) getPageAt(i);
            float rotation = 0f;
            float rotationY = 0f;
            float initialAlpha = cl.getAlpha();
            float finalAlphaMultiplierValue = 1f;
            float finalAlpha = (!mFadeInAdjacentScreens || stateIsSpringLoaded ||
                    (i == mCurrentPage)) ? 1f : 0f;

            // Determine the pages alpha during the state transition
            if ((oldStateIsSmall && stateIsNormal) ||
                (oldStateIsNormal && stateIsSmall)) {
                // To/from workspace - only show the current page unless the transition is not
                //                     animated and the animation end callback below doesn't run
                if (i == mCurrentPage || !animated) {
                    finalAlpha = 1f;
                    finalAlphaMultiplierValue = 0f;
                } else {
                    initialAlpha = 0f;
                    finalAlpha = 0f;
                }
            }

            // Update the rotation of the screen
            if (mTransitionEffect == TransitionEffect.Tablet || stateIsSmall || stateIsSpringLoaded) {
                if (i < mCurrentPage) {
                    rotationY = WORKSPACE_ROTATION;
                } else if (i > mCurrentPage) {
                    rotationY = -WORKSPACE_ROTATION;
                }
            }

            // Make sure the pages are visible with the stack effect
            if (mTransitionEffect == TransitionEffect.Stack) {
                if (stateIsSmall || stateIsSpringLoaded) {
                    cl.setVisibility(VISIBLE);
                } else if (stateIsNormal) {
                    if (i <= mCurrentPage) {
                        cl.setVisibility(VISIBLE);
                    } else {
                        cl.setVisibility(GONE);
                    }
                }
            }

            if ((mTransitionEffect == TransitionEffect.Tablet && stateIsNormal) ||
                    (LauncherApplication.isScreenLarge() && (stateIsSmall || stateIsSpringLoaded))) {
                translationX = getOffsetXForRotation(rotationY, cl.getWidth(), cl.getHeight());
            }

            if (stateIsNormal && (mTransitionEffect == TransitionEffect.RotateUp ||
                    mTransitionEffect == TransitionEffect.RotateDown)) {
                rotation = (mTransitionEffect == TransitionEffect.RotateUp ? WORKSPACE_ROTATION_ANGLE : -WORKSPACE_ROTATION_ANGLE) *
                        Math.abs(mCurrentPage - i);
            }

            if (stateIsSmall || stateIsSpringLoaded) {
                cl.setCameraDistance(1280 * mDensity);
                if (mTransitionEffect == TransitionEffect.RotateUp ||
                        mTransitionEffect == TransitionEffect.RotateDown) {
                    cl.setTranslationX(0.0f);
                }
                cl.setPivotX(cl.getMeasuredWidth() * 0.5f);
                cl.setPivotY(cl.getMeasuredHeight() * 0.5f);
            }

            mOldAlphas[i] = initialAlpha;
            mNewAlphas[i] = finalAlpha;
            if (animated) {
                mOldTranslationXs[i] = cl.getTranslationX();
                mOldTranslationYs[i] = cl.getTranslationY();
                mOldScaleXs[i] = cl.getScaleX();
                mOldScaleYs[i] = cl.getScaleY();
                mOldBackgroundAlphas[i] = cl.getBackgroundAlpha();
                mOldBackgroundAlphaMultipliers[i] = cl.getBackgroundAlphaMultiplier();
                mOldRotations[i] = cl.getRotation();
                mOldRotationYs[i] = cl.getRotationY();

                mNewTranslationXs[i] = translationX;
                mNewTranslationYs[i] = translationY;
                mNewScaleXs[i] = finalScaleFactor;
                mNewScaleYs[i] = finalScaleFactor;
                mNewBackgroundAlphas[i] = finalBackgroundAlpha;
                mNewBackgroundAlphaMultipliers[i] = finalAlphaMultiplierValue;
                mNewRotations[i] = rotation;
                mNewRotationYs[i] = rotationY;
            } else {
                cl.setTranslationX(translationX);
                cl.setTranslationY(translationY);
                cl.setScaleX(finalScaleFactor);
                cl.setScaleY(finalScaleFactor);
                cl.setBackgroundAlpha(finalBackgroundAlpha);
                cl.setBackgroundAlphaMultiplier(finalAlphaMultiplierValue);
                cl.setAlpha(finalAlpha);
                cl.setRotation(rotation);
                cl.setRotationY(rotationY);
                mChangeStateAnimationListener.onAnimationEnd(null);
            }
        }

        if (animated) {
            ValueAnimator animWithInterpolator =
                ValueAnimator.ofFloat(0f, 1f).setDuration(duration);

            if (zoomIn) {
                animWithInterpolator.setInterpolator(mZoomInInterpolator);
            }

            animWithInterpolator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    // The above code to determine initialAlpha and finalAlpha will ensure that only
                    // the current page is visible during (and subsequently, after) the transition
                    // animation.  If fade adjacent pages is disabled, then re-enable the page
                    // visibility after the transition animation.
                    if (!mFadeInAdjacentScreens && stateIsNormal && oldStateIsSmall) {
                        for (int i = 0; i < getChildCount(); i++) {
                            final CellLayout cl = (CellLayout) getPageAt(i);
                            cl.setAlpha(1f);
                        }
                    }
                }
            });
            animWithInterpolator.addUpdateListener(new LauncherAnimatorUpdateListener() {
                public void onAnimationUpdate(float a, float b) {
                    mTransitionProgress = b;
                    if (b == 0f) {
                        // an optimization, but not required
                        return;
                    }
                    invalidate();
                    for (int i = 0; i < getChildCount(); i++) {
                        final CellLayout cl = (CellLayout) getPageAt(i);
                        cl.invalidate();
                        cl.setFastTranslationX(a * mOldTranslationXs[i] + b * mNewTranslationXs[i]);
                        cl.setFastTranslationY(a * mOldTranslationYs[i] + b * mNewTranslationYs[i]);
                        cl.setFastScaleX(a * mOldScaleXs[i] + b * mNewScaleXs[i]);
                        cl.setFastScaleY(a * mOldScaleYs[i] + b * mNewScaleYs[i]);
                        cl.setFastBackgroundAlpha(
                                a * mOldBackgroundAlphas[i] + b * mNewBackgroundAlphas[i]);
                        cl.setBackgroundAlphaMultiplier(a * mOldBackgroundAlphaMultipliers[i] +
                                b * mNewBackgroundAlphaMultipliers[i]);
                        cl.setFastAlpha(a * mOldAlphas[i] + b * mNewAlphas[i]);
                        cl.invalidate();
                    }
                    syncChildrenLayersEnabledOnVisiblePages();
                }
            });

            ValueAnimator rotationAnim =
                ValueAnimator.ofFloat(0f, 1f).setDuration(duration);
            rotationAnim.setInterpolator(new DecelerateInterpolator(2.0f));
            rotationAnim.addUpdateListener(new LauncherAnimatorUpdateListener() {
                public void onAnimationUpdate(float a, float b) {
                    if (b == 0f) {
                        // an optimization, but not required
                        return;
                    }
                    for (int i = 0; i < getChildCount(); i++) {
                        final CellLayout cl = (CellLayout) getPageAt(i);
                        if (mOldRotations[i] != mNewRotations[i]) {
                            cl.setRotation(a * mOldRotations[i] + b * mNewRotations[i]);
                        }
                        cl.setFastRotationY(a * mOldRotationYs[i] + b * mNewRotationYs[i]);
                    }
                }
            });

            mAnimator.playTogether(animWithInterpolator, rotationAnim);
            mAnimator.setStartDelay(delay);
            // If we call this when we're not animated, onAnimationEnd is never called on
            // the listener; make sure we only use the listener when we're actually animating
            mAnimator.addListener(mChangeStateAnimationListener);
            mAnimator.start();
        }

        if (stateIsSpringLoaded) {
            // Right now we're covered by Apps Customize
            // Show the background gradient immediately, so the gradient will
            // be showing once AppsCustomize disappears
            animateBackgroundGradient(getResources().getInteger(
                    R.integer.config_appsCustomizeSpringLoadedBgAlpha) / 100f, false);
        } else {
            // Fade the background gradient away
            animateBackgroundGradient(0f, true);
        }
        syncChildrenLayersEnabledOnVisiblePages();
    }

    /**
     * Draw the View v into the given Canvas.
     *
     * @param v the view to draw
     * @param destCanvas the canvas to draw on
     * @param padding the horizontal and vertical padding to use when drawing
     */
    private void drawDragView(View v, Canvas destCanvas, int padding, boolean pruneToDrawable) {
        final Rect clipRect = mTempRect;
        v.getDrawingRect(clipRect);

        boolean textVisible = false;

        destCanvas.save();
        if (v instanceof TextView && pruneToDrawable) {
            Drawable d = ((TextView) v).getCompoundDrawables()[1];
            clipRect.set(0, 0, d.getIntrinsicWidth() + padding, d.getIntrinsicHeight() + padding);
            destCanvas.translate(padding / 2, padding / 2);
            d.draw(destCanvas);
        } else {
            if (v instanceof FolderIcon) {
                if (!mHideIconLabels) {
                    // For FolderIcons the text can bleed into the icon area, and so we need to
                    // hide the text completely (which can't be achieved by clipping).
                    if (((FolderIcon) v).getTextVisible()) {
                        ((FolderIcon) v).setTextVisible(false);
                        textVisible = true;
                    }
                }
            } else if (v instanceof BubbleTextView) {
                final BubbleTextView tv = (BubbleTextView) v;
                clipRect.bottom = tv.getExtendedPaddingTop() - (int) BubbleTextView.PADDING_V +
                        tv.getLayout().getLineTop(0);
            } else if (v instanceof TextView) {
                final TextView tv = (TextView) v;
                clipRect.bottom = tv.getExtendedPaddingTop() - tv.getCompoundDrawablePadding() +
                        tv.getLayout().getLineTop(0);
            }
            destCanvas.translate(-v.getScrollX() + padding / 2, -v.getScrollY() + padding / 2);
            destCanvas.clipRect(clipRect, Op.REPLACE);
            v.draw(destCanvas);

            // Restore text visibility of FolderIcon if necessary
            if (!mHideIconLabels && textVisible) {
                ((FolderIcon) v).setTextVisible(true);
            }
        }
        destCanvas.restore();
    }

    /**
     * Returns a new bitmap to show when the given View is being dragged around.
     * Responsibility for the bitmap is transferred to the caller.
     */
    public Bitmap createDragBitmap(View v, Canvas canvas, int padding) {
        final int outlineColor = getResources().getColor(android.R.color.holo_blue_light);
        Bitmap b;

        if (v instanceof TextView) {
            Drawable d = ((TextView) v).getCompoundDrawables()[1];
            b = Bitmap.createBitmap(d.getIntrinsicWidth() + padding,
                    d.getIntrinsicHeight() + padding, Bitmap.Config.ARGB_8888);
        } else {
            b = Bitmap.createBitmap(
                    v.getWidth() + padding, v.getHeight() + padding, Bitmap.Config.ARGB_8888);
        }

        canvas.setBitmap(b);
        drawDragView(v, canvas, padding, true);
        mOutlineHelper.applyOuterBlur(b, canvas, outlineColor);
        canvas.drawColor(mDragViewMultiplyColor, PorterDuff.Mode.MULTIPLY);
        canvas.setBitmap(null);

        return b;
    }

    /**
     * Returns a new bitmap to be used as the object outline, e.g. to visualize the drop location.
     * Responsibility for the bitmap is transferred to the caller.
     */
    private Bitmap createDragOutline(View v, Canvas canvas, int padding) {
        final int outlineColor = getResources().getColor(android.R.color.holo_blue_light);
        final Bitmap b = Bitmap.createBitmap(
                v.getWidth() + padding, v.getHeight() + padding, Bitmap.Config.ARGB_8888);

        canvas.setBitmap(b);
        drawDragView(v, canvas, padding, true);
        mOutlineHelper.applyMediumExpensiveOutlineWithBlur(b, canvas, outlineColor, outlineColor);
        canvas.setBitmap(null);
        return b;
    }

    /**
     * Returns a new bitmap to be used as the object outline, e.g. to visualize the drop location.
     * Responsibility for the bitmap is transferred to the caller.
     */
    private Bitmap createDragOutline(Bitmap orig, Canvas canvas, int padding, int w, int h,
            Paint alphaClipPaint) {
        final int outlineColor = getResources().getColor(android.R.color.holo_blue_light);
        final Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(b);

        Rect src = new Rect(0, 0, orig.getWidth(), orig.getHeight());
        float scaleFactor = Math.min((w - padding) / (float) orig.getWidth(),
                (h - padding) / (float) orig.getHeight());
        int scaledWidth = (int) (scaleFactor * orig.getWidth());
        int scaledHeight = (int) (scaleFactor * orig.getHeight());
        Rect dst = new Rect(0, 0, scaledWidth, scaledHeight);

        // center the image
        dst.offset((w - scaledWidth) / 2, (h - scaledHeight) / 2);

        canvas.drawBitmap(orig, src, dst, null);
        mOutlineHelper.applyMediumExpensiveOutlineWithBlur(b, canvas, outlineColor, outlineColor,
                alphaClipPaint);
        canvas.setBitmap(null);

        return b;
    }

    /**
     * Creates a drag outline to represent a drop (that we don't have the actual information for
     * yet).  May be changed in the future to alter the drop outline slightly depending on the
     * clip description mime data.
     */
    private Bitmap createExternalDragOutline(Canvas canvas, int padding) {
        Resources r = getResources();
        final int outlineColor = r.getColor(android.R.color.holo_blue_light);
        final int iconWidth = r.getDimensionPixelSize(R.dimen.workspace_cell_width);
        final int iconHeight = r.getDimensionPixelSize(R.dimen.workspace_cell_height);
        final int rectRadius = r.getDimensionPixelSize(R.dimen.external_drop_icon_rect_radius);
        final int inset = (int) (Math.min(iconWidth, iconHeight) * 0.2f);
        final Bitmap b = Bitmap.createBitmap(
                iconWidth + padding, iconHeight + padding, Bitmap.Config.ARGB_8888);

        canvas.setBitmap(b);
        canvas.drawRoundRect(new RectF(inset, inset, iconWidth - inset, iconHeight - inset),
                rectRadius, rectRadius, mExternalDragOutlinePaint);
        mOutlineHelper.applyMediumExpensiveOutlineWithBlur(b, canvas, outlineColor, outlineColor);
        canvas.setBitmap(null);
        return b;
    }

    void startDrag(CellLayout.CellInfo cellInfo) {
        View child = cellInfo.cell;

        // Make sure the drag was started by a long press as opposed to a long click.
        if (!child.isInTouchMode()) {
            return;
        }

        mDragInfo = cellInfo;
        child.setVisibility(GONE);

        child.clearFocus();
        child.setPressed(false);

        final Canvas canvas = new Canvas();

        // We need to add extra padding to the bitmap to make room for the glow effect
        final int bitmapPadding = HolographicOutlineHelper.MAX_OUTER_BLUR_RADIUS;

        // The outline is used to visualize where the item will land if dropped
        mDragOutline = createDragOutline(child, canvas, bitmapPadding);
        beginDragShared(child, this);
    }

    public void beginDragShared(View child, DragSource source) {
        Resources r = getResources();

        // We need to add extra padding to the bitmap to make room for the glow effect
        final int bitmapPadding = HolographicOutlineHelper.MAX_OUTER_BLUR_RADIUS;

        // The drag bitmap follows the touch point around on the screen
        final Bitmap b = createDragBitmap(child, new Canvas(), bitmapPadding);

        final int bmpWidth = b.getWidth();

        mLauncher.getDragLayer().getLocationInDragLayer(child, mTempXY);
        final int dragLayerX = (int) mTempXY[0] + (child.getWidth() - bmpWidth) / 2;
        int dragLayerY = mTempXY[1] - bitmapPadding / 2;

        Point dragVisualizeOffset = null;
        Rect dragRect = null;
        if (child instanceof BubbleTextView || child instanceof PagedViewIcon) {
            int iconSize = r.getDimensionPixelSize(R.dimen.app_icon_size);
            int iconPaddingTop = r.getDimensionPixelSize(R.dimen.app_icon_padding_top);
            int top = child.getPaddingTop();
            int left = (bmpWidth - iconSize) / 2;
            int right = left + iconSize;
            int bottom = top + iconSize;
            dragLayerY += top;
            // Note: The drag region is used to calculate drag layer offsets, but the
            // dragVisualizeOffset in addition to the dragRect (the size) to position the outline.
            dragVisualizeOffset = new Point(-bitmapPadding / 2, iconPaddingTop - bitmapPadding / 2);
            dragRect = new Rect(left, top, right, bottom);
        } else if (child instanceof FolderIcon) {
            int previewSize = r.getDimensionPixelSize(R.dimen.folder_preview_size);
            dragRect = new Rect(0, 0, child.getWidth(), previewSize);
        }

        // Clear the pressed state if necessary
        if (child instanceof BubbleTextView) {
            BubbleTextView icon = (BubbleTextView) child;
            icon.clearPressedOrFocusedBackground();
        }

        mDragController.startDrag(b, dragLayerX, dragLayerY, source, child.getTag(),
                DragController.DRAG_ACTION_MOVE, dragVisualizeOffset, dragRect);
        b.recycle();
    }

    void addApplicationShortcut(ShortcutInfo info, CellLayout target, long container, int screen,
            int cellX, int cellY, boolean insertAtFirst, int intersectX, int intersectY) {
        View view = mLauncher.createShortcut(R.layout.application, target, (ShortcutInfo) info);

        final int[] cellXY = new int[2];
        target.findCellForSpanThatIntersects(cellXY, 1, 1, intersectX, intersectY);
        addInScreen(view, container, screen, cellXY[0], cellXY[1], 1, 1, insertAtFirst);
        LauncherModel.addOrMoveItemInDatabase(mLauncher, info, container, screen, cellXY[0],
                cellXY[1]);
    }

    public boolean transitionStateShouldAllowDrop() {
        return (!isSwitchingState() || mTransitionProgress > 0.5f);
    }

    /**
     * {@inheritDoc}
     */
    public boolean acceptDrop(DragObject d) {
        // If it's an external drop (e.g. from All Apps), check if it should be accepted
        if (d.dragSource != this) {
            // Don't accept the drop if we're not over a screen at time of drop
            if (mDragTargetLayout == null) {
                return false;
            }
            if (!transitionStateShouldAllowDrop()) return false;

            mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset,
                    d.dragView, mDragViewVisualCenter);

            // We want the point to be mapped to the dragTarget.
            if (mLauncher.isHotseatLayout(mDragTargetLayout)) {
                mapPointFromSelfToSibling(mLauncher.getHotseat(), mDragViewVisualCenter);
            } else {
                mapPointFromSelfToChild(mDragTargetLayout, mDragViewVisualCenter, null);
            }

            int spanX = 1;
            int spanY = 1;
            View ignoreView = null;
            if (mDragInfo != null) {
                final CellLayout.CellInfo dragCellInfo = mDragInfo;
                spanX = dragCellInfo.spanX;
                spanY = dragCellInfo.spanY;
                ignoreView = dragCellInfo.cell;
            } else {
                final ItemInfo dragInfo = (ItemInfo) d.dragInfo;
                spanX = dragInfo.spanX;
                spanY = dragInfo.spanY;
            }

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], spanX, spanY, mDragTargetLayout, mTargetCell);
            if (willCreateUserFolder((ItemInfo) d.dragInfo, mDragTargetLayout, mTargetCell, true)) {
                return true;
            }
            if (willAddToExistingUserFolder((ItemInfo) d.dragInfo, mDragTargetLayout,
                    mTargetCell)) {
                return true;
            }

            // Don't accept the drop if there's no room for the item
            if (!mDragTargetLayout.findCellForSpanIgnoring(null, spanX, spanY, ignoreView)) {
                // Don't show the message if we are dropping on the AllApps button and the hotseat
                // is full
                if (mTargetCell != null && mLauncher.isHotseatLayout(mDragTargetLayout)) {
                    Hotseat hotseat = mLauncher.getHotseat();
                    if (Hotseat.isAllAppsButtonRank(
                            hotseat.getOrderInHotseat(mTargetCell[0], mTargetCell[1]))) {
                        return false;
                    }
                }

                mLauncher.showOutOfSpaceMessage();
                return false;
            }
        }
        return true;
    }

    boolean willCreateUserFolder(ItemInfo info, CellLayout target, int[] targetCell,
            boolean considerTimeout) {
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);

        boolean hasntMoved = false;
        if (mDragInfo != null) {
            CellLayout cellParent = getParentCellLayoutForView(mDragInfo.cell);
            hasntMoved = (mDragInfo.cellX == targetCell[0] &&
                    mDragInfo.cellY == targetCell[1]) && (cellParent == target);
        }

        if (dropOverView == null || hasntMoved || (considerTimeout && !mCreateUserFolderOnDrop)) {
            return false;
        }

        boolean aboveShortcut = (dropOverView.getTag() instanceof ShortcutInfo);
        boolean willBecomeShortcut =
                (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                info.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT);

        return (aboveShortcut && willBecomeShortcut);
    }

    boolean willAddToExistingUserFolder(Object dragInfo, CellLayout target, int[] targetCell) {
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(dragInfo)) {
                return true;
            }
        }
        return false;
    }

    boolean createUserFolderIfNecessary(View newView, long container, CellLayout target,
            int[] targetCell, boolean external, DragView dragView, Runnable postAnimationRunnable) {
        View v = target.getChildAt(targetCell[0], targetCell[1]);
        boolean hasntMoved = false;
        if (mDragInfo != null) {
            CellLayout cellParent = getParentCellLayoutForView(mDragInfo.cell);
            hasntMoved = (mDragInfo.cellX == targetCell[0] &&
                    mDragInfo.cellY == targetCell[1]) && (cellParent == target);
        }

        if (v == null || hasntMoved || !mCreateUserFolderOnDrop) return false;
        mCreateUserFolderOnDrop = false;
        final int screen = (targetCell == null) ? mDragInfo.screen : indexOfChild(target);

        boolean aboveShortcut = (v.getTag() instanceof ShortcutInfo);
        boolean willBecomeShortcut = (newView.getTag() instanceof ShortcutInfo);

        if (aboveShortcut && willBecomeShortcut) {
            ShortcutInfo sourceInfo = (ShortcutInfo) newView.getTag();
            ShortcutInfo destInfo = (ShortcutInfo) v.getTag();
            // if the drag started here, we need to remove it from the workspace
            if (!external) {
                getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
            }

            Rect folderLocation = new Rect();
            float scale = mLauncher.getDragLayer().getDescendantRectRelativeToSelf(v, folderLocation);
            target.removeView(v);

            FolderIcon fi =
                mLauncher.addFolder(target, container, screen, targetCell[0], targetCell[1]);
            destInfo.cellX = -1;
            destInfo.cellY = -1;
            sourceInfo.cellX = -1;
            sourceInfo.cellY = -1;

            // If the dragView is null, we can't animate
            boolean animate = dragView != null;
            if (animate) {
                fi.performCreateAnimation(destInfo, v, sourceInfo, dragView, folderLocation, scale,
                        postAnimationRunnable);
            } else {
                fi.addItem(destInfo);
                fi.addItem(sourceInfo);
            }
            return true;
        }
        return false;
    }

    boolean addToExistingFolderIfNecessary(View newView, CellLayout target, int[] targetCell,
            DragObject d, boolean external) {
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(d.dragInfo)) {
                fi.onDrop(d);

                // if the drag started here, we need to remove it from the workspace
                if (!external) {
                    getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
                }
                return true;
            }
        }
        return false;
    }

    public void onDrop(DragObject d) {
        mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset, d.dragView,
                mDragViewVisualCenter);

        // We want the point to be mapped to the dragTarget.
        if (mDragTargetLayout != null) {
            if (mLauncher.isHotseatLayout(mDragTargetLayout)) {
                mapPointFromSelfToSibling(mLauncher.getHotseat(), mDragViewVisualCenter);
            } else {
                mapPointFromSelfToChild(mDragTargetLayout, mDragViewVisualCenter, null);
            }
        }

        CellLayout dropTargetLayout = mDragTargetLayout;

        int snapScreen = -1;
        if (d.dragSource != this) {
            final int[] touchXY = new int[] { (int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1] };
            onDropExternal(touchXY, d.dragInfo, dropTargetLayout, false, d);
        } else if (mDragInfo != null) {
            final View cell = mDragInfo.cell;

            if (dropTargetLayout != null) {
                // Move internally
                boolean hasMovedLayouts = (getParentCellLayoutForView(cell) != dropTargetLayout);
                boolean hasMovedIntoHotseat = mLauncher.isHotseatLayout(dropTargetLayout);
                long container = hasMovedIntoHotseat ?
                        LauncherSettings.Favorites.CONTAINER_HOTSEAT :
                        LauncherSettings.Favorites.CONTAINER_DESKTOP;
                int screen = (mTargetCell[0] < 0) ?
                        mDragInfo.screen : indexOfChild(dropTargetLayout);
                int spanX = mDragInfo != null ? mDragInfo.spanX : 1;
                int spanY = mDragInfo != null ? mDragInfo.spanY : 1;
                // First we find the cell nearest to point at which the item is
                // dropped, without any consideration to whether there is an item there.
                mTargetCell = findNearestArea((int) mDragViewVisualCenter[0], (int)
                        mDragViewVisualCenter[1], spanX, spanY, dropTargetLayout, mTargetCell);
                // If the item being dropped is a shortcut and the nearest drop
                // cell also contains a shortcut, then create a folder with the two shortcuts.
                if (!mInScrollArea && createUserFolderIfNecessary(cell, container,
                        dropTargetLayout, mTargetCell, false, d.dragView, null)) {
                    return;
                }

                if (addToExistingFolderIfNecessary(cell, dropTargetLayout, mTargetCell, d, false)) {
                    return;
                }

                // Aside from the special case where we're dropping a shortcut onto a shortcut,
                // we need to find the nearest cell location that is vacant
                mTargetCell = findNearestVacantArea((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], mDragInfo.spanX, mDragInfo.spanY, cell,
                        dropTargetLayout, mTargetCell);

                if (mCurrentPage != screen && !hasMovedIntoHotseat) {
                    snapScreen = screen;
                    snapToPage(screen);
                }

                if (mTargetCell[0] >= 0 && mTargetCell[1] >= 0) {
                    if (hasMovedLayouts) {
                        // Reparent the view
                        getParentCellLayoutForView(cell).removeView(cell);
                        addInScreen(cell, container, screen, mTargetCell[0], mTargetCell[1],
                                mDragInfo.spanX, mDragInfo.spanY);
                    }

                    // update the item's position after drop
                    final ItemInfo info = (ItemInfo) cell.getTag();
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                    dropTargetLayout.onMove(cell, mTargetCell[0], mTargetCell[1]);
                    lp.cellX = mTargetCell[0];
                    lp.cellY = mTargetCell[1];
                    cell.setId(LauncherModel.getCellLayoutChildId(container, mDragInfo.screen,
                            mTargetCell[0], mTargetCell[1], mDragInfo.spanX, mDragInfo.spanY));

                    if (container != LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
                            cell instanceof LauncherAppWidgetHostView) {
                        final CellLayout cellLayout = dropTargetLayout;
                        // We post this call so that the widget has a chance to be placed
                        // in its final location

                        final LauncherAppWidgetHostView hostView = (LauncherAppWidgetHostView) cell;
                        AppWidgetProviderInfo pinfo = hostView.getAppWidgetInfo();
                        boolean hasMovedCells = ((mTargetCell[0] != mDragInfo.cellX) || (mTargetCell[1] != mDragInfo.cellY));

                        if ((pinfo.resizeMode != AppWidgetProviderInfo.RESIZE_NONE || mResizeAnyWidget)
                                && !hasMovedCells && !hasMovedLayouts) {
                            final Runnable resizeRunnable = new Runnable() {
                                public void run() {
                                    DragLayer dragLayer = mLauncher.getDragLayer();
                                    dragLayer.addResizeFrame(info, hostView, cellLayout);
                                }
                            };
                            post(new Runnable() {
                                public void run() {
                                    if (!isPageMoving()) {
                                        resizeRunnable.run();
                                    } else {
                                        mDelayedResizeRunnable = resizeRunnable;
                                    }
                                }
                            });
                        }
                    }

                    LauncherModel.moveItemInDatabase(mLauncher, info, container, screen, lp.cellX,
                            lp.cellY);
                }
            }

            final CellLayout parent = (CellLayout) cell.getParent().getParent();

            // Prepare it to be animated into its new position
            // This must be called after the view has been re-parented
            final Runnable disableHardwareLayersRunnable = new Runnable() {
                @Override
                public void run() {
                    mAnimatingViewIntoPlace = false;
                    updateChildrenLayersEnabled();
                }
            };
            mAnimatingViewIntoPlace = true;
            if (d.dragView.hasDrawn()) {
                int duration = snapScreen < 0 ? -1 : ADJACENT_SCREEN_DROP_DURATION;
                setFinalScrollForPageChange(snapScreen);
                mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, cell, duration,
                        disableHardwareLayersRunnable);
                resetFinalScrollForPageChange(snapScreen);
            } else {
                cell.setVisibility(VISIBLE);
            }
            parent.onDropChild(cell);
        }
    }

    public void setFinalScrollForPageChange(int screen) {
        if (screen >= 0) {
            mSavedScrollX = getScrollX();
            CellLayout cl = (CellLayout) getPageAt(screen);
            mSavedTranslationX = cl.getTranslationX();
            mSavedRotationY = cl.getRotationY();
            final int newX = getChildOffset(screen) - getRelativeChildOffset(screen);
            setScrollX(newX);
            cl.setTranslationX(0f);
            cl.setRotationY(0f);
        }
    }

    public void resetFinalScrollForPageChange(int screen) {
        if (screen >= 0) {
            CellLayout cl = (CellLayout) getPageAt(screen);
            setScrollX(mSavedScrollX);
            cl.setTranslationX(mSavedTranslationX);
            cl.setRotationY(mSavedRotationY);
        }
    }

    public void getViewLocationRelativeToSelf(View v, int[] location) {
        getLocationInWindow(location);
        int x = location[0];
        int y = location[1];

        v.getLocationInWindow(location);
        int vX = location[0];
        int vY = location[1];

        location[0] = vX - x;
        location[1] = vY - y;
    }

    public void onDragEnter(DragObject d) {
        if (mDragTargetLayout != null) {
            mDragTargetLayout.setIsDragOverlapping(false);
            mDragTargetLayout.onDragExit();
        }
        mDragTargetLayout = getCurrentDropLayout();
        mDragTargetLayout.setIsDragOverlapping(true);
        mDragTargetLayout.onDragEnter();

        // Because we don't have space in the Phone UI (the CellLayouts run to the edge) we
        // don't need to show the outlines
        if (LauncherApplication.isScreenLarge()) {
            showOutlines();
        }
    }

    private void doDragExit(DragObject d) {
        // Clean up folders
        cleanupFolderCreation(d);

        // Reset the scroll area and previous drag target
        onResetScrollArea();

        if (mDragTargetLayout != null) {
            mDragTargetLayout.setIsDragOverlapping(false);
            mDragTargetLayout.onDragExit();
        }
        mLastDragOverView = null;
        mSpringLoadedDragController.cancel();

        if (!mIsPageMoving) {
            hideOutlines();
        }
    }

    public void onDragExit(DragObject d) {
        doDragExit(d);
    }

    public DropTarget getDropTargetDelegate(DragObject d) {
        return null;
    }

    /**
     * Tests to see if the drop will be accepted by Launcher, and if so, includes additional data
     * in the returned structure related to the widgets that match the drop (or a null list if it is
     * a shortcut drop).  If the drop is not accepted then a null structure is returned.
     */
    private Pair<Integer, List<WidgetMimeTypeHandlerData>> validateDrag(DragEvent event) {
        final LauncherModel model = mLauncher.getModel();
        final ClipDescription desc = event.getClipDescription();
        final int mimeTypeCount = desc.getMimeTypeCount();
        for (int i = 0; i < mimeTypeCount; ++i) {
            final String mimeType = desc.getMimeType(i);
            if (mimeType.equals(InstallShortcutReceiver.SHORTCUT_MIMETYPE)) {
                return new Pair<Integer, List<WidgetMimeTypeHandlerData>>(i, null);
            } else {
                final List<WidgetMimeTypeHandlerData> widgets =
                    model.resolveWidgetsForMimeType(mContext, mimeType);
                if (widgets.size() > 0) {
                    return new Pair<Integer, List<WidgetMimeTypeHandlerData>>(i, widgets);
                }
            }
        }
        return null;
    }

    /**
     * Global drag and drop handler
     */
    @Override
    public boolean onDragEvent(DragEvent event) {
        final ClipDescription desc = event.getClipDescription();
        final CellLayout layout = (CellLayout) getPageAt(mCurrentPage);
        final int[] pos = new int[2];
        layout.getLocationOnScreen(pos);
        // We need to offset the drag coordinates to layout coordinate space
        final int x = (int) event.getX() - pos[0];
        final int y = (int) event.getY() - pos[1];

        switch (event.getAction()) {
        case DragEvent.ACTION_DRAG_STARTED: {
            // Validate this drag
            Pair<Integer, List<WidgetMimeTypeHandlerData>> test = validateDrag(event);
            if (test != null) {
                boolean isShortcut = (test.second == null);
                if (isShortcut) {
                    // Check if we have enough space on this screen to add a new shortcut
                    if (!layout.findCellForSpan(pos, 1, 1)) {
                        mLauncher.showOutOfSpaceMessage();
                        return false;
                    }
                }
            } else {
                // Show error message if we couldn't accept any of the items
                Toast.makeText(mContext, mContext.getString(R.string.external_drop_widget_error),
                        Toast.LENGTH_SHORT).show();
                return false;
            }

            // Create the drag outline
            // We need to add extra padding to the bitmap to make room for the glow effect
            final Canvas canvas = new Canvas();
            final int bitmapPadding = HolographicOutlineHelper.MAX_OUTER_BLUR_RADIUS;
            mDragOutline = createExternalDragOutline(canvas, bitmapPadding);

            // Show the current page outlines to indicate that we can accept this drop
            showOutlines();
            layout.onDragEnter();
            layout.visualizeDropLocation(null, mDragOutline, x, y, 1, 1, null, null);

            return true;
        }
        case DragEvent.ACTION_DRAG_LOCATION:
            // Visualize the drop location
            layout.visualizeDropLocation(null, mDragOutline, x, y, 1, 1, null, null);
            return true;
        case DragEvent.ACTION_DROP: {
            // Try and add any shortcuts
            final LauncherModel model = mLauncher.getModel();
            final ClipData data = event.getClipData();

            // We assume that the mime types are ordered in descending importance of
            // representation. So we enumerate the list of mime types and alert the
            // user if any widgets can handle the drop.  Only the most preferred
            // representation will be handled.
            pos[0] = x;
            pos[1] = y;
            Pair<Integer, List<WidgetMimeTypeHandlerData>> test = validateDrag(event);
            if (test != null) {
                final int index = test.first;
                final List<WidgetMimeTypeHandlerData> widgets = test.second;
                final boolean isShortcut = (widgets == null);
                final String mimeType = desc.getMimeType(index);
                if (isShortcut) {
                    final Intent intent = data.getItemAt(index).getIntent();
                    Object info = model.infoFromShortcutIntent(mContext, intent, data.getIcon());
                    if (info != null) {
                        onDropExternal(new int[] { x, y }, info, layout, false);
                    }
                } else {
                    if (widgets.size() == 1) {
                        // If there is only one item, then go ahead and add and configure
                        // that widget
                        final AppWidgetProviderInfo widgetInfo = widgets.get(0).widgetInfo;
                        final PendingAddWidgetInfo createInfo =
                                new PendingAddWidgetInfo(widgetInfo, mimeType, data);
                        mLauncher.addAppWidgetFromDrop(createInfo,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP, mCurrentPage, null, pos);
                    } else {
                        // Show the widget picker dialog if there is more than one widget
                        // that can handle this data type
                        final InstallWidgetReceiver.WidgetListAdapter adapter =
                            new InstallWidgetReceiver.WidgetListAdapter(mLauncher, mimeType,
                                    data, widgets, layout, mCurrentPage, pos);
                        final AlertDialog.Builder builder =
                            new AlertDialog.Builder(mContext);
                        builder.setAdapter(adapter, adapter);
                        builder.setCancelable(true);
                        builder.setTitle(mContext.getString(
                                R.string.external_drop_widget_pick_title));
                        builder.setIcon(R.drawable.ic_no_applications);
                        builder.show();
                    }
                }
            }
            return true;
        }
        case DragEvent.ACTION_DRAG_ENDED:
            // Hide the page outlines after the drop
            layout.onDragExit();
            hideOutlines();
            return true;
        }
        return super.onDragEvent(event);
    }

    /*
    *
    * Convert the 2D coordinate xy from the parent View's coordinate space to this CellLayout's
    * coordinate space. The argument xy is modified with the return result.
    *
    */
   void mapPointFromSelfToChild(View v, float[] xy) {
       mapPointFromSelfToChild(v, xy, null);
   }

   /*
    *
    * Convert the 2D coordinate xy from the parent View's coordinate space to this CellLayout's
    * coordinate space. The argument xy is modified with the return result.
    *
    * if cachedInverseMatrix is not null, this method will just use that matrix instead of
    * computing it itself; we use this to avoid redundant matrix inversions in
    * findMatchingPageForDragOver
    *
    */
   void mapPointFromSelfToChild(View v, float[] xy, Matrix cachedInverseMatrix) {
       if (cachedInverseMatrix == null) {
           v.getMatrix().invert(mTempInverseMatrix);
           cachedInverseMatrix = mTempInverseMatrix;
       }
       xy[0] = xy[0] + mScrollX - v.getLeft();
       xy[1] = xy[1] + mScrollY - v.getTop();
       cachedInverseMatrix.mapPoints(xy);
   }

   /*
    * Maps a point from the Workspace's coordinate system to another sibling view's. (Workspace
    * covers the full screen)
    */
   void mapPointFromSelfToSibling(View v, float[] xy) {
       xy[0] = xy[0] - v.getLeft();
       xy[1] = xy[1] - v.getTop();
   }

   /*
    *
    * Convert the 2D coordinate xy from this CellLayout's coordinate space to
    * the parent View's coordinate space. The argument xy is modified with the return result.
    *
    */
   void mapPointFromChildToSelf(View v, float[] xy) {
       v.getMatrix().mapPoints(xy);
       xy[0] -= (mScrollX - v.getLeft());
       xy[1] -= (mScrollY - v.getTop());
   }

   static private float squaredDistance(float[] point1, float[] point2) {
        float distanceX = point1[0] - point2[0];
        float distanceY = point2[1] - point2[1];
        return distanceX * distanceX + distanceY * distanceY;
   }

    /*
     *
     * Returns true if the passed CellLayout cl overlaps with dragView
     *
     */
    boolean overlaps(CellLayout cl, DragView dragView,
            int dragViewX, int dragViewY, Matrix cachedInverseMatrix) {
        // Transform the coordinates of the item being dragged to the CellLayout's coordinates
        final float[] draggedItemTopLeft = mTempDragCoordinates;
        draggedItemTopLeft[0] = dragViewX;
        draggedItemTopLeft[1] = dragViewY;
        final float[] draggedItemBottomRight = mTempDragBottomRightCoordinates;
        draggedItemBottomRight[0] = draggedItemTopLeft[0] + dragView.getDragRegionWidth();
        draggedItemBottomRight[1] = draggedItemTopLeft[1] + dragView.getDragRegionHeight();

        // Transform the dragged item's top left coordinates
        // to the CellLayout's local coordinates
        mapPointFromSelfToChild(cl, draggedItemTopLeft, cachedInverseMatrix);
        float overlapRegionLeft = Math.max(0f, draggedItemTopLeft[0]);
        float overlapRegionTop = Math.max(0f, draggedItemTopLeft[1]);

        if (overlapRegionLeft <= cl.getWidth() && overlapRegionTop >= 0) {
            // Transform the dragged item's bottom right coordinates
            // to the CellLayout's local coordinates
            mapPointFromSelfToChild(cl, draggedItemBottomRight, cachedInverseMatrix);
            float overlapRegionRight = Math.min(cl.getWidth(), draggedItemBottomRight[0]);
            float overlapRegionBottom = Math.min(cl.getHeight(), draggedItemBottomRight[1]);

            if (overlapRegionRight >= 0 && overlapRegionBottom <= cl.getHeight()) {
                float overlap = (overlapRegionRight - overlapRegionLeft) *
                         (overlapRegionBottom - overlapRegionTop);
                if (overlap > 0) {
                    return true;
                }
             }
        }
        return false;
    }

    /*
     *
     * This method returns the CellLayout that is currently being dragged to. In order to drag
     * to a CellLayout, either the touch point must be directly over the CellLayout, or as a second
     * strategy, we see if the dragView is overlapping any CellLayout and choose the closest one
     *
     * Return null if no CellLayout is currently being dragged over
     *
     */
    private CellLayout findMatchingPageForDragOver(
            DragView dragView, float originX, float originY, boolean exact) {
        // We loop through all the screens (ie CellLayouts) and see which ones overlap
        // with the item being dragged and then choose the one that's closest to the touch point
        final int screenCount = getChildCount();
        CellLayout bestMatchingScreen = null;
        float smallestDistSoFar = Float.MAX_VALUE;

        for (int i = 0; i < screenCount; i++) {
            CellLayout cl = (CellLayout) getPageAt(i);

            final float[] touchXy = {originX, originY};
            // Transform the touch coordinates to the CellLayout's local coordinates
            // If the touch point is within the bounds of the cell layout, we can return immediately
            cl.getMatrix().invert(mTempInverseMatrix);
            mapPointFromSelfToChild(cl, touchXy, mTempInverseMatrix);

            if (touchXy[0] >= 0 && touchXy[0] <= cl.getWidth() &&
                    touchXy[1] >= 0 && touchXy[1] <= cl.getHeight()) {
                return cl;
            }

            if (!exact) {
                // Get the center of the cell layout in screen coordinates
                final float[] cellLayoutCenter = mTempCellLayoutCenterCoordinates;
                cellLayoutCenter[0] = cl.getWidth()/2;
                cellLayoutCenter[1] = cl.getHeight()/2;
                mapPointFromChildToSelf(cl, cellLayoutCenter);

                touchXy[0] = originX;
                touchXy[1] = originY;

                // Calculate the distance between the center of the CellLayout
                // and the touch point
                float dist = squaredDistance(touchXy, cellLayoutCenter);

                if (dist < smallestDistSoFar) {
                    smallestDistSoFar = dist;
                    bestMatchingScreen = cl;
                }
            }
        }
        return bestMatchingScreen;
    }

    // This is used to compute the visual center of the dragView. This point is then
    // used to visualize drop locations and determine where to drop an item. The idea is that
    // the visual center represents the user's interpretation of where the item is, and hence
    // is the appropriate point to use when determining drop location.
    private float[] getDragViewVisualCenter(int x, int y, int xOffset, int yOffset,
            DragView dragView, float[] recycle) {
        float res[];
        if (recycle == null) {
            res = new float[2];
        } else {
            res = recycle;
        }

        // First off, the drag view has been shifted in a way that is not represented in the
        // x and y values or the x/yOffsets. Here we account for that shift.
        x += getResources().getDimensionPixelSize(R.dimen.dragViewOffsetX);
        y += getResources().getDimensionPixelSize(R.dimen.dragViewOffsetY);

        // These represent the visual top and left of drag view if a dragRect was provided.
        // If a dragRect was not provided, then they correspond to the actual view left and
        // top, as the dragRect is in that case taken to be the entire dragView.
        // R.dimen.dragViewOffsetY.
        int left = x - xOffset;
        int top = y - yOffset;

        // In order to find the visual center, we shift by half the dragRect
        res[0] = left + dragView.getDragRegion().width() / 2;
        res[1] = top + dragView.getDragRegion().height() / 2;

        return res;
    }

    private boolean isDragWidget(DragObject d) {
        return (d.dragInfo instanceof LauncherAppWidgetInfo ||
                d.dragInfo instanceof PendingAddWidgetInfo);
    }
    private boolean isExternalDragWidget(DragObject d) {
        return d.dragSource != this && isDragWidget(d);
    }

    public void onDragOver(DragObject d) {
        // Skip drag over events while we are dragging over side pages
        if (mInScrollArea) return;
        if (mIsSwitchingState) return;

        Rect r = new Rect();
        CellLayout layout = null;
        ItemInfo item = (ItemInfo) d.dragInfo;

        // Ensure that we have proper spans for the item that we are dropping
        if (item.spanX < 0 || item.spanY < 0) throw new RuntimeException("Improper spans found");
        mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset,
            d.dragView, mDragViewVisualCenter);

        // Identify whether we have dragged over a side page
        if (isSmall()) {
            if (mLauncher.getHotseat() != null && !isExternalDragWidget(d)) {
                mLauncher.getHotseat().getHitRect(r);
                if (r.contains(d.x, d.y)) {
                    layout = mLauncher.getHotseat().getLayout();
                }
            }
            if (layout == null) {
                layout = findMatchingPageForDragOver(d.dragView, d.x, d.y, false);
            }
            if (layout != mDragTargetLayout) {
                // Cancel all intermediate folder states
                cleanupFolderCreation(d);

                if (mDragTargetLayout != null) {
                    mDragTargetLayout.setIsDragOverlapping(false);
                    mDragTargetLayout.onDragExit();
                }
                mDragTargetLayout = layout;
                if (mDragTargetLayout != null) {
                    mDragTargetLayout.setIsDragOverlapping(true);
                    mDragTargetLayout.onDragEnter();
                } else {
                    mLastDragOverView = null;
                }

                boolean isInSpringLoadedMode = (mState == State.SPRING_LOADED);
                if (isInSpringLoadedMode) {
                    if (mLauncher.isHotseatLayout(layout)) {
                        mSpringLoadedDragController.cancel();
                    } else {
                        mSpringLoadedDragController.setAlarm(mDragTargetLayout);
                    }
                }
            }
        } else {
            // Test to see if we are over the hotseat otherwise just use the current page
            if (mLauncher.getHotseat() != null && !isDragWidget(d)) {
                mLauncher.getHotseat().getHitRect(r);
                if (r.contains(d.x, d.y)) {
                    layout = mLauncher.getHotseat().getLayout();
                }
            }
            if (layout == null) {
                layout = getCurrentDropLayout();
            }
            if (layout != mDragTargetLayout) {
                if (mDragTargetLayout != null) {
                    mDragTargetLayout.setIsDragOverlapping(false);
                    mDragTargetLayout.onDragExit();
                }
                mDragTargetLayout = layout;
                mDragTargetLayout.setIsDragOverlapping(true);
                mDragTargetLayout.onDragEnter();
            }
        }

        // Handle the drag over
        if (mDragTargetLayout != null) {
            final View child = (mDragInfo == null) ? null : mDragInfo.cell;

            // We want the point to be mapped to the dragTarget.
            if (mLauncher.isHotseatLayout(mDragTargetLayout)) {
                mapPointFromSelfToSibling(mLauncher.getHotseat(), mDragViewVisualCenter);
            } else {
                mapPointFromSelfToChild(mDragTargetLayout, mDragViewVisualCenter, null);
            }
            ItemInfo info = (ItemInfo) d.dragInfo;

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], 1, 1, mDragTargetLayout, mTargetCell);
            final View dragOverView = mDragTargetLayout.getChildAt(mTargetCell[0],
                    mTargetCell[1]);

            boolean userFolderPending = willCreateUserFolder(info, mDragTargetLayout,
                    mTargetCell, false);
            boolean isOverFolder = dragOverView instanceof FolderIcon;
            if (dragOverView != mLastDragOverView) {
                cancelFolderCreation();
                if (mLastDragOverView != null && mLastDragOverView instanceof FolderIcon) {
                    ((FolderIcon) mLastDragOverView).onDragExit(d.dragInfo);
                }
            }

            if (userFolderPending && dragOverView != mLastDragOverView) {
                mFolderCreationAlarm.setOnAlarmListener(new
                        FolderCreationAlarmListener(mDragTargetLayout, mTargetCell[0], mTargetCell[1]));
                mFolderCreationAlarm.setAlarm(FOLDER_CREATION_TIMEOUT);
            }

            if (dragOverView != mLastDragOverView && isOverFolder) {
                ((FolderIcon) dragOverView).onDragEnter(d.dragInfo);
                if (mDragTargetLayout != null) {
                    mDragTargetLayout.clearDragOutlines();
                }
            }
            mLastDragOverView = dragOverView;

            if (!mCreateUserFolderOnDrop && !isOverFolder) {
                mDragTargetLayout.visualizeDropLocation(child, mDragOutline,
                        (int) mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1],
                        item.spanX, item.spanY, d.dragView.getDragVisualizeOffset(),
                        d.dragView.getDragRegion());
            }
        }
    }

    private void cleanupFolderCreation(DragObject d) {
        if (mDragFolderRingAnimator != null && mCreateUserFolderOnDrop) {
            mDragFolderRingAnimator.animateToNaturalState();
        }
        if (mLastDragOverView != null && mLastDragOverView instanceof FolderIcon) {
            if (d != null) {
                ((FolderIcon) mLastDragOverView).onDragExit(d.dragInfo);
            }
        }
        mFolderCreationAlarm.cancelAlarm();
    }

    private void cancelFolderCreation() {
        if (mDragFolderRingAnimator != null && mCreateUserFolderOnDrop) {
            mDragFolderRingAnimator.animateToNaturalState();
        }
        mCreateUserFolderOnDrop = false;
        mFolderCreationAlarm.cancelAlarm();
    }

    class FolderCreationAlarmListener implements OnAlarmListener {
        CellLayout layout;
        int cellX;
        int cellY;

        public FolderCreationAlarmListener(CellLayout layout, int cellX, int cellY) {
            this.layout = layout;
            this.cellX = cellX;
            this.cellY = cellY;
        }

        public void onAlarm(Alarm alarm) {
            if (mDragFolderRingAnimator == null) {
                mDragFolderRingAnimator = new FolderRingAnimator(mLauncher, null);
            }
            mDragFolderRingAnimator.setCell(cellX, cellY);
            mDragFolderRingAnimator.setCellLayout(layout);
            mDragFolderRingAnimator.animateToAcceptState();
            layout.showFolderAccept(mDragFolderRingAnimator);
            layout.clearDragOutlines();
            mCreateUserFolderOnDrop = true;
        }
    }

    @Override
    public void getHitRect(Rect outRect) {
        // We want the workspace to have the whole area of the display (it will find the correct
        // cell layout to drop to in the existing drag/drop logic.
        outRect.set(0, 0, mDisplayWidth, mDisplayHeight);
    }

    /**
     * Add the item specified by dragInfo to the given layout.
     * @return true if successful
     */
    public boolean addExternalItemToScreen(ItemInfo dragInfo, CellLayout layout) {
        if (layout.findCellForSpan(mTempEstimate, dragInfo.spanX, dragInfo.spanY)) {
            onDropExternal(dragInfo.dropPos, dragInfo, layout, false);
            return true;
        }
        mLauncher.showOutOfSpaceMessage();
        return false;
    }

    private void onDropExternal(int[] touchXY, Object dragInfo,
            CellLayout cellLayout, boolean insertAtFirst) {
        onDropExternal(touchXY, dragInfo, cellLayout, insertAtFirst, null);
    }

    /**
     * Drop an item that didn't originate on one of the workspace screens.
     * It may have come from Launcher (e.g. from all apps or customize), or it may have
     * come from another app altogether.
     *
     * NOTE: This can also be called when we are outside of a drag event, when we want
     * to add an item to one of the workspace screens.
     */
    private void onDropExternal(final int[] touchXY, final Object dragInfo,
            final CellLayout cellLayout, boolean insertAtFirst, DragObject d) {
        final Runnable exitSpringLoadedRunnable = new Runnable() {
            @Override
            public void run() {
                mLauncher.exitSpringLoadedDragModeDelayed(true, false);
            }
        };

        ItemInfo info = (ItemInfo) dragInfo;
        int spanX = info.spanX;
        int spanY = info.spanY;
        if (mDragInfo != null) {
            spanX = mDragInfo.spanX;
            spanY = mDragInfo.spanY;
        }

        final long container = mLauncher.isHotseatLayout(cellLayout) ?
                LauncherSettings.Favorites.CONTAINER_HOTSEAT :
                    LauncherSettings.Favorites.CONTAINER_DESKTOP;
        final int screen = indexOfChild(cellLayout);
        if (!mLauncher.isHotseatLayout(cellLayout) && screen != mCurrentPage
                && mState != State.SPRING_LOADED) {
            snapToPage(screen);
        }

        if (info instanceof PendingAddItemInfo) {
            final PendingAddItemInfo pendingInfo = (PendingAddItemInfo) dragInfo;

            boolean findNearestVacantCell = true;
            if (pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
                mTargetCell = findNearestArea((int) touchXY[0], (int) touchXY[1], spanX, spanY,
                        cellLayout, mTargetCell);
                if (willCreateUserFolder((ItemInfo) d.dragInfo, mDragTargetLayout, mTargetCell,
                        true) || willAddToExistingUserFolder((ItemInfo) d.dragInfo,
                                mDragTargetLayout, mTargetCell)) {
                    findNearestVacantCell = false;
                }
            }
            if (findNearestVacantCell) {
                    mTargetCell = findNearestVacantArea(touchXY[0], touchXY[1], spanX, spanY, null,
                        cellLayout, mTargetCell);
            }

            Runnable onAnimationCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    // When dragging and dropping from customization tray, we deal with creating
                    // widgets/shortcuts/folders in a slightly different way
                    switch (pendingInfo.itemType) {
                    case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                        mLauncher.addAppWidgetFromDrop((PendingAddWidgetInfo) pendingInfo,
                                container, screen, mTargetCell, null);
                        break;
                    case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                        mLauncher.processShortcutFromDrop(pendingInfo.componentName,
                                container, screen, mTargetCell, null);
                        break;
                    default:
                        throw new IllegalStateException("Unknown item type: " +
                                pendingInfo.itemType);
                    }
                    cellLayout.onDragExit();
                }
            };

            // Now we animate the dragView, (ie. the widget or shortcut preview) into its final
            // location and size on the home screen.
            RectF r = estimateItemPosition(cellLayout, pendingInfo,
                    mTargetCell[0], mTargetCell[1], spanX, spanY);
            int loc[] = new int[2];
            loc[0] = (int) r.left;
            loc[1] = (int) r.top;
            setFinalTransitionTransform(cellLayout);
            float cellLayoutScale =
                    mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(cellLayout, loc);
            resetTransitionTransform(cellLayout);

            float dragViewScale =  Math.min(r.width() / d.dragView.getMeasuredWidth(),
                    r.height() / d.dragView.getMeasuredHeight());
            // The animation will scale the dragView about its center, so we need to center about
            // the final location.
            loc[0] -= (d.dragView.getMeasuredWidth() - cellLayoutScale * r.width()) / 2;
            loc[1] -= (d.dragView.getMeasuredHeight() - cellLayoutScale * r.height()) / 2;

            mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, loc,
                    dragViewScale * cellLayoutScale, onAnimationCompleteRunnable);
        } else {
            // This is for other drag/drop cases, like dragging from All Apps
            View view;

            switch (info.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                if (info.container == NO_ID && info instanceof ApplicationInfo) {
                    // Came from all apps -- make a copy
                    info = new ShortcutInfo((ApplicationInfo) info);
                }
                view = mLauncher.createShortcut(R.layout.application, cellLayout,
                        (ShortcutInfo) info);
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                view = FolderIcon.fromXml(R.layout.folder_icon, mLauncher, cellLayout,
                        (FolderInfo) info, mIconCache);
                if (mHideIconLabels) {
                    ((FolderIcon) view).setTextVisible(false);
                }
                break;
            default:
                throw new IllegalStateException("Unknown item type: " + info.itemType);
            }

            // First we find the cell nearest to point at which the item is
            // dropped, without any consideration to whether there is an item there.
            if (touchXY != null) {
                mTargetCell = findNearestArea((int) touchXY[0], (int) touchXY[1], spanX, spanY,
                        cellLayout, mTargetCell);
                d.postAnimationRunnable = exitSpringLoadedRunnable;
                if (createUserFolderIfNecessary(view, container, cellLayout, mTargetCell, true,
                        d.dragView, d.postAnimationRunnable)) {
                    return;
                }
                if (addToExistingFolderIfNecessary(view, cellLayout, mTargetCell, d, true)) {
                    return;
                }
            }

            if (touchXY != null) {
                // when dragging and dropping, just find the closest free spot
                mTargetCell = findNearestVacantArea(touchXY[0], touchXY[1], 1, 1, null,
                        cellLayout, mTargetCell);
            } else {
                cellLayout.findCellForSpan(mTargetCell, 1, 1);
            }
            addInScreen(view, container, screen, mTargetCell[0], mTargetCell[1], info.spanX,
                    info.spanY, insertAtFirst);
            cellLayout.onDropChild(view);
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) view.getLayoutParams();
            cellLayout.getChildrenLayout().measureChild(view);

            LauncherModel.addOrMoveItemInDatabase(mLauncher, info, container, screen,
                    lp.cellX, lp.cellY);

            if (d.dragView != null) {
                // We wrap the animation call in the temporary set and reset of the current
                // cellLayout to its final transform -- this means we animate the drag view to
                // the correct final location.
                setFinalTransitionTransform(cellLayout);
                mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, view,
                        exitSpringLoadedRunnable);
                resetTransitionTransform(cellLayout);
            }
        }
    }

    public void setFinalTransitionTransform(CellLayout layout) {
        if (isSwitchingState()) {
            int index = indexOfChild(layout);
            mCurrentScaleX = layout.getScaleX();
            mCurrentScaleY = layout.getScaleY();
            mCurrentTranslationX = layout.getTranslationX();
            mCurrentTranslationY = layout.getTranslationY();
            mCurrentRotationY = layout.getRotationY();
            layout.setScaleX(mNewScaleXs[index]);
            layout.setScaleY(mNewScaleYs[index]);
            layout.setTranslationX(mNewTranslationXs[index]);
            layout.setTranslationY(mNewTranslationYs[index]);
            layout.setRotationY(mNewRotationYs[index]);
        }
    }
    public void resetTransitionTransform(CellLayout layout) {
        if (isSwitchingState()) {
            mCurrentScaleX = layout.getScaleX();
            mCurrentScaleY = layout.getScaleY();
            mCurrentTranslationX = layout.getTranslationX();
            mCurrentTranslationY = layout.getTranslationY();
            mCurrentRotationY = layout.getRotationY();
            layout.setScaleX(mCurrentScaleX);
            layout.setScaleY(mCurrentScaleY);
            layout.setTranslationX(mCurrentTranslationX);
            layout.setTranslationY(mCurrentTranslationY);
            layout.setRotationY(mCurrentRotationY);
        }
    }

    /**
     * Return the current {@link CellLayout}, correctly picking the destination
     * screen while a scroll is in progress.
     */
    public CellLayout getCurrentDropLayout() {
        return (CellLayout) getPageAt(mNextPage == INVALID_PAGE ? mCurrentPage : mNextPage);
    }

    /**
     * Return the current CellInfo describing our current drag; this method exists
     * so that Launcher can sync this object with the correct info when the activity is created/
     * destroyed
     *
     */
    public CellLayout.CellInfo getDragInfo() {
        return mDragInfo;
    }

    /**
     * Calculate the nearest cell where the given object would be dropped.
     *
     * pixelX and pixelY should be in the coordinate system of layout
     */
    private int[] findNearestVacantArea(int pixelX, int pixelY,
            int spanX, int spanY, View ignoreView, CellLayout layout, int[] recycle) {
        return layout.findNearestVacantArea(
                pixelX, pixelY, spanX, spanY, ignoreView, recycle);
    }

    /**
     * Calculate the nearest cell where the given object would be dropped.
     *
     * pixelX and pixelY should be in the coordinate system of layout
     */
    private int[] findNearestArea(int pixelX, int pixelY,
            int spanX, int spanY, CellLayout layout, int[] recycle) {
        return layout.findNearestArea(
                pixelX, pixelY, spanX, spanY, recycle);
    }

    void setup(DragController dragController) {
        mSpringLoadedDragController = new SpringLoadedDragController(mLauncher);
        mDragController = dragController;

        // hardware layers on children are enabled on startup, but should be disabled until
        // needed
        updateChildrenLayersEnabled();
        setWallpaperDimension();
        if (!mScrollWallpaper) {
            centerWallpaperOffset();
        }
    }

    /**
     * Called at the end of a drag which originated on the workspace.
     */
    public void onDropCompleted(View target, DragObject d, boolean success) {
        if (success) {
            if (target != this) {
                if (mDragInfo != null) {
                    getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
                    if (mDragInfo.cell instanceof DropTarget) {
                        mDragController.removeDropTarget((DropTarget) mDragInfo.cell);
                    }
                }
            }
        } else if (mDragInfo != null) {
            // NOTE: When 'success' is true, onDragExit is called by the DragController before
            // calling onDropCompleted(). We call it ourselves here, but maybe this should be
            // moved into DragController.cancelDrag().
            doDragExit(null);
            CellLayout cellLayout;
            if (mLauncher.isHotseatLayout(target)) {
                cellLayout = mLauncher.getHotseat().getLayout();
            } else {
                cellLayout = (CellLayout) getPageAt(mDragInfo.screen);
            }
            cellLayout.onDropChild(mDragInfo.cell);
        }
        if (d.cancelled &&  mDragInfo.cell != null) {
                mDragInfo.cell.setVisibility(VISIBLE);
        }
        mDragOutline = null;
        mDragInfo = null;
    }

    public boolean isDropEnabled() {
        return true;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
        Launcher.setScreen(mCurrentPage);
    }

    @Override
    public void scrollLeft() {
        if (!isSmall() && !mIsSwitchingState) {
            super.scrollLeft();
        }
        Folder openFolder = getOpenFolder();
        if (openFolder != null) {
            openFolder.completeDragExit();
        }
    }

    @Override
    public void scrollRight() {
        if (!isSmall() && !mIsSwitchingState) {
            super.scrollRight();
        }
        Folder openFolder = getOpenFolder();
        if (openFolder != null) {
            openFolder.completeDragExit();
        }
    }

    @Override
    public boolean onEnterScrollArea(int x, int y, int direction) {
        // Ignore the scroll area if we are dragging over the hot seat
        if (mLauncher.getHotseat() != null) {
            Rect r = new Rect();
            mLauncher.getHotseat().getHitRect(r);
            if (r.contains(x, y)) {
                return false;
            }
        }

        boolean result = false;
        if (!isSmall() && !mIsSwitchingState) {
            mInScrollArea = true;

            final int page = mCurrentPage + (direction == DragController.SCROLL_LEFT ? -1 : 1);
            final CellLayout layout = (CellLayout) getPageAt(page);
            cancelFolderCreation();

            if (layout != null) {
                // Exit the current layout and mark the overlapping layout
                if (mDragTargetLayout != null) {
                    mDragTargetLayout.setIsDragOverlapping(false);
                    mDragTargetLayout.onDragExit();
                }
                mDragTargetLayout = layout;
                mDragTargetLayout.setIsDragOverlapping(true);

                // Workspace is responsible for drawing the edge glow on adjacent pages,
                // so we need to redraw the workspace when this may have changed.
                invalidate();
                result = true;
            }
        }
        return result;
    }

    @Override
    public boolean onExitScrollArea() {
        boolean result = false;
        if (mInScrollArea) {
            if (mDragTargetLayout != null) {
                // Unmark the overlapping layout and re-enter the current layout
                mDragTargetLayout.setIsDragOverlapping(false);
                mDragTargetLayout = getCurrentDropLayout();
                mDragTargetLayout.onDragEnter();

                // Workspace is responsible for drawing the edge glow on adjacent pages,
                // so we need to redraw the workspace when this may have changed.
                invalidate();
                result = true;
            }
            mInScrollArea = false;
        }
        return result;
    }

    private void onResetScrollArea() {
        if (mDragTargetLayout != null) {
            // Unmark the overlapping layout
            mDragTargetLayout.setIsDragOverlapping(false);

            // Workspace is responsible for drawing the edge glow on adjacent pages,
            // so we need to redraw the workspace when this may have changed.
            invalidate();
        }
        mInScrollArea = false;
    }

    /**
     * Returns a specific CellLayout
     */
    CellLayout getParentCellLayoutForView(View v) {
        ArrayList<CellLayout> layouts = getWorkspaceAndHotseatCellLayouts();
        for (CellLayout layout : layouts) {
            if (layout.getChildrenLayout().indexOfChild(v) > -1) {
                return layout;
            }
        }
        return null;
    }

    /**
     * Returns a list of all the CellLayouts in the workspace.
     */
    ArrayList<CellLayout> getWorkspaceAndHotseatCellLayouts() {
        ArrayList<CellLayout> layouts = new ArrayList<CellLayout>();
        int screenCount = getChildCount();
        for (int screen = 0; screen < screenCount; screen++) {
            layouts.add(((CellLayout) getPageAt(screen)));
        }
        if (mLauncher.getHotseat() != null) {
            layouts.add(mLauncher.getHotseat().getLayout());
        }
        return layouts;
    }

    /**
     * We should only use this to search for specific children.  Do not use this method to modify
     * CellLayoutChildren directly.
     */
    ArrayList<CellLayoutChildren> getWorkspaceAndHotseatCellLayoutChildren() {
        ArrayList<CellLayoutChildren> childrenLayouts = new ArrayList<CellLayoutChildren>();
        int screenCount = getChildCount();
        for (int screen = 0; screen < screenCount; screen++) {
            childrenLayouts.add(((CellLayout) getPageAt(screen)).getChildrenLayout());
        }
        if (mLauncher.getHotseat() != null) {
            childrenLayouts.add(mLauncher.getHotseat().getLayout().getChildrenLayout());
        }
        return childrenLayouts;
    }

    public Folder getFolderForTag(Object tag) {
        ArrayList<CellLayoutChildren> childrenLayouts = getWorkspaceAndHotseatCellLayoutChildren();
        for (CellLayoutChildren layout: childrenLayouts) {
            int count = layout.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = layout.getChildAt(i);
                if (child instanceof Folder) {
                    Folder f = (Folder) child;
                    if (f.getInfo() == tag && f.getInfo().opened) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    public View getViewForTag(Object tag) {
        ArrayList<CellLayoutChildren> childrenLayouts = getWorkspaceAndHotseatCellLayoutChildren();
        for (CellLayoutChildren layout: childrenLayouts) {
            int count = layout.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = layout.getChildAt(i);
                if (child.getTag() == tag) {
                    return child;
                }
            }
        }
        return null;
    }

    void clearDropTargets() {
        ArrayList<CellLayoutChildren> childrenLayouts = getWorkspaceAndHotseatCellLayoutChildren();
        for (CellLayoutChildren layout: childrenLayouts) {
            int childCount = layout.getChildCount();
            for (int j = 0; j < childCount; j++) {
                View v = layout.getChildAt(j);
                if (v instanceof DropTarget) {
                    mDragController.removeDropTarget((DropTarget) v);
                }
            }
        }
    }

    void removeItems(final ArrayList<ApplicationInfo> apps) {
        final AppWidgetManager widgets = AppWidgetManager.getInstance(getContext());

        final HashSet<String> packageNames = new HashSet<String>();
        final int appCount = apps.size();
        for (ApplicationInfo app : apps) {
            packageNames.add(app.componentName.getPackageName());
        }

        ArrayList<CellLayout> cellLayouts = getWorkspaceAndHotseatCellLayouts();
        for (final CellLayout layoutParent: cellLayouts) {
            final ViewGroup layout = layoutParent.getChildrenLayout();

            // Avoid ANRs by treating each screen separately
            post(new Runnable() {
                public void run() {
                    final ArrayList<View> childrenToRemove = new ArrayList<View>();
                    childrenToRemove.clear();

                    int childCount = layout.getChildCount();
                    for (int j = 0; j < childCount; j++) {
                        final View view = layout.getChildAt(j);
                        Object tag = view.getTag();

                        if (tag instanceof ShortcutInfo) {
                            final ShortcutInfo info = (ShortcutInfo) tag;
                            final Intent intent = info.intent;
                            final ComponentName name = intent.getComponent();

                            if (Intent.ACTION_MAIN.equals(intent.getAction()) && name != null) {
                                for (String packageName: packageNames) {
                                    if (packageName.equals(name.getPackageName())) {
                                        LauncherModel.deleteItemFromDatabase(mLauncher, info);
                                        childrenToRemove.add(view);
                                    }
                                }
                            }
                        } else if (tag instanceof FolderInfo) {
                            final FolderInfo info = (FolderInfo) tag;
                            final ArrayList<ShortcutInfo> contents = info.contents;
                            final int contentsCount = contents.size();
                            final ArrayList<ShortcutInfo> appsToRemoveFromFolder =
                                    new ArrayList<ShortcutInfo>();

                            for (final ShortcutInfo appInfo : contents) {
                                final Intent intent = appInfo.intent;
                                final ComponentName name = intent.getComponent();

                                if (Intent.ACTION_MAIN.equals(intent.getAction()) && name != null) {
                                    for (String packageName : packageNames) {
                                        if (packageName.equals(name.getPackageName())) {
                                            appsToRemoveFromFolder.add(appInfo);
                                        }
                                    }
                                }
                            }
                            for (ShortcutInfo item: appsToRemoveFromFolder) {
                                info.remove(item);
                                LauncherModel.deleteItemFromDatabase(mLauncher, item);
                            }
                        } else if (tag instanceof LauncherAppWidgetInfo) {
                            final LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) tag;
                            final AppWidgetProviderInfo provider =
                                    widgets.getAppWidgetInfo(info.appWidgetId);
                            if (provider != null) {
                                for (String packageName: packageNames) {
                                    if (packageName.equals(provider.provider.getPackageName())) {
                                        LauncherModel.deleteItemFromDatabase(mLauncher, info);
                                        childrenToRemove.add(view);
                                    }
                                }
                            }
                        }
                    }

                    childCount = childrenToRemove.size();
                    for (int j = 0; j < childCount; j++) {
                        View child = childrenToRemove.get(j);
                        // Note: We can not remove the view directly from CellLayoutChildren as this
                        // does not re-mark the spaces as unoccupied.
                        layoutParent.removeViewInLayout(child);
                        if (child instanceof DropTarget) {
                            mDragController.removeDropTarget((DropTarget)child);
                        }
                    }

                    if (childCount > 0) {
                        layout.requestLayout();
                        layout.invalidate();
                    }
                }
            });
        }
    }

    void updateShortcuts(ArrayList<ApplicationInfo> apps) {
        ArrayList<CellLayoutChildren> childrenLayouts = getWorkspaceAndHotseatCellLayoutChildren();
        for (CellLayoutChildren layout: childrenLayouts) {
            int childCount = layout.getChildCount();
            for (int j = 0; j < childCount; j++) {
                final View view = layout.getChildAt(j);
                Object tag = view.getTag();
                if (tag instanceof ShortcutInfo) {
                    ShortcutInfo info = (ShortcutInfo)tag;
                    // We need to check for ACTION_MAIN otherwise getComponent() might
                    // return null for some shortcuts (for instance, for shortcuts to
                    // web pages.)
                    final Intent intent = info.intent;
                    final ComponentName name = intent.getComponent();
                    if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION &&
                            Intent.ACTION_MAIN.equals(intent.getAction()) && name != null) {
                        final int appCount = apps.size();
                        for (ApplicationInfo app : apps) {
                            if (app.componentName.equals(name)) {
                                info.setIcon(mIconCache.getIcon(info.intent));
                                ((TextView) view).setCompoundDrawablesWithIntrinsicBounds(null,
                                        new FastBitmapDrawable(info.getIcon(mIconCache)),
                                        null, null);
                            }
                        }
                    }
                }
            }
        }
    }

    void moveToDefaultScreen(boolean animate) {
        if (!isSmall()) {
            if (animate) {
                snapToPage(mDefaultHomescreen);
            } else {
                setCurrentPage(mDefaultHomescreen);
            }
        }
        getPageAt(mDefaultHomescreen).requestFocus();
    }

    @Override
    public void syncPages() {
    }

    @Override
    public void syncPageItems(int page, boolean immediate) {
    }

    @Override
    protected String getCurrentPageDescription() {
        int page = (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
        return String.format(mContext.getString(R.string.workspace_scroll_format),
                page + 1, getChildCount());
    }

    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(this, loc);
    }

    void setFadeForOverScroll(float fade) {
        if (!isScrollingIndicatorEnabled()) return;

        mOverscrollFade = fade;
        float reducedFade = 0.5f + 0.5f * (1 - fade);
        final ViewGroup parent = (ViewGroup) getParent();

        final ImageView qsbDivider = (ImageView) (parent.findViewById(R.id.qsb_divider));
        final ImageView dockDivider = (ImageView) (parent.findViewById(R.id.dock_divider));
        final ImageView scrollIndicator = getScrollingIndicator();

        cancelScrollingIndicatorAnimations();
        if (qsbDivider != null && mShowSearchBar) qsbDivider.setAlpha(reducedFade);
        if (dockDivider != null && mShowDockDivider) dockDivider.setAlpha(reducedFade);
        if (scrollIndicator != null && mShowScrollingIndicator) scrollIndicator.setAlpha(1 - fade);
    }
}
