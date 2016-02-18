/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Advanceable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.PagedView.PageSwitchListener;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.LauncherActivityInfoCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.list.SettingsPinnedHeaderAdapter;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.settings.SettingsProvider;
import com.android.launcher3.stats.LauncherStats;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LongArrayMap;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.WidgetHostViewLoader;
import com.android.launcher3.widget.WidgetsContainerView;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default launcher application.
 */
public class Launcher extends Activity
        implements View.OnClickListener, OnLongClickListener, LauncherModel.Callbacks,
                   View.OnTouchListener, PageSwitchListener, LauncherProviderChangeListener {
    static final String TAG = "Launcher";
    static final boolean LOGD = false;

    static final boolean PROFILE_STARTUP = false;
    static final boolean DEBUG_WIDGETS = true;
    static final boolean DEBUG_STRICT_MODE = false;
    static final boolean DEBUG_RESUME_TIME = false;
    static final boolean DEBUG_DUMP_LOG = false;

    static final boolean ENABLE_DEBUG_INTENTS = false; // allow DebugIntents to run

    private static final int REQUEST_CREATE_SHORTCUT = 1;
    private static final int REQUEST_CREATE_APPWIDGET = 5;
    private static final int REQUEST_PICK_APPWIDGET = 9;
    private static final int REQUEST_PICK_WALLPAPER = 10;

    private static final int REQUEST_BIND_APPWIDGET = 11;
    private static final int REQUEST_RECONFIGURE_APPWIDGET = 12;

    private static final int REQUEST_PERMISSION_CALL_PHONE = 13;

    public static final int REQUEST_OPEN_PROTECTED_FOLDER = 14;
    public static final int REQUEST_PROTECT_FOLDER = 15;
    public static final int REQUEST_UNPROTECT_FOLDER = 16;

    private static final int WORKSPACE_BACKGROUND_GRADIENT = 0;
    private static final int WORKSPACE_BACKGROUND_TRANSPARENT = 1;
    private static final int WORKSPACE_BACKGROUND_BLACK = 2;

    private static final float BOUNCE_ANIMATION_TENSION = 1.3f;


    public static final String LONGPRESS_CHANGE = "wallpaper_changed_by_longpress";


    /**
     * IntentStarter uses request codes starting with this. This must be greater than all activity
     * request codes used internally.
     */
    protected static final int REQUEST_LAST = 100;

    static final int SCREEN_COUNT = 5;

    // To turn on these properties, type
    // adb shell setprop log.tag.PROPERTY_NAME [VERBOSE | SUPPRESS]
    static final String DUMP_STATE_PROPERTY = "launcher_dump_state";

    // The Intent extra that defines whether to ignore the launch animation
    static final String INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION =
            "com.android.launcher3.intent.extra.shortcut.INGORE_LAUNCH_ANIMATION";

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: int
    private static final String RUNTIME_STATE = "launcher.state";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CONTAINER = "launcher.add_container";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SCREEN = "launcher.add_screen";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_X = "launcher.add_cell_x";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_Y = "launcher.add_cell_y";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_X = "launcher.add_span_x";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_Y = "launcher.add_span_y";
    // Type: parcelable
    private static final String RUNTIME_STATE_PENDING_ADD_WIDGET_INFO = "launcher.add_widget_info";
    // Type: parcelable
    private static final String RUNTIME_STATE_PENDING_ADD_WIDGET_ID = "launcher.add_widget_id";
    // Type: int[]
    private static final String RUNTIME_STATE_VIEW_IDS = "launcher.view_ids";

    static final String INTRO_SCREEN_DISMISSED = "launcher.intro_screen_dismissed";
    static final String FIRST_RUN_ACTIVITY_DISPLAYED = "launcher.first_run_activity_displayed";

    static final String FIRST_LOAD_COMPLETE = "launcher.first_load_complete";
    static final String ACTION_FIRST_LOAD_COMPLETE =
            "com.android.launcher3.action.FIRST_LOAD_COMPLETE";

    public static final String SHOW_WEIGHT_WATCHER = "debug.show_mem";
    public static final boolean SHOW_WEIGHT_WATCHER_DEFAULT = false;

    private static final String QSB_WIDGET_ID = "qsb_widget_id";
    private static final String QSB_WIDGET_PROVIDER = "qsb_widget_provider";

    public static final String USER_HAS_MIGRATED = "launcher.user_migrated_from_old_data";

    /** The different states that Launcher can be in. */
    enum State { NONE, WORKSPACE, APPS, APPS_SPRING_LOADED, WIDGETS, WIDGETS_SPRING_LOADED }

    @Thunk State mState = State.WORKSPACE;
    @Thunk LauncherStateTransitionAnimation mStateTransitionAnimation;

    private boolean mIsSafeModeEnabled;

    LauncherOverlayCallbacks mLauncherOverlayCallbacks = new LauncherOverlayCallbacksImpl();
    LauncherOverlay mLauncherOverlay;
    InsettableFrameLayout mLauncherOverlayContainer;

    static final int APPWIDGET_HOST_ID = 1024;
    public static final int EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT = 300;
    private static final int ON_ACTIVITY_RESULT_ANIMATION_DELAY = 500;
    private static final int ACTIVITY_START_DELAY = 1000;

    private HashMap<Integer, Integer> mItemIdToViewId = new HashMap<Integer, Integer>();
    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    // How long to wait before the new-shortcut animation automatically pans the workspace
    private static int NEW_APPS_PAGE_MOVE_DELAY = 500;
    private static int NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS = 5;
    @Thunk static int NEW_APPS_ANIMATION_DELAY = 500;

    private final BroadcastReceiver mCloseSystemDialogsReceiver
            = new CloseSystemDialogsIntentReceiver();

    @Thunk final NetworkConnectionReceiver mConnectionReceiver = new NetworkConnectionReceiver();
    @Thunk final DeviceUnlockedReceiver mDeviceUnlockedReceiver = new DeviceUnlockedReceiver();

    private LayoutInflater mInflater;

    @Thunk Workspace mWorkspace;
    private View mLauncherView;
    private View mPageIndicators;
    @Thunk DragLayer mDragLayer;
    private DragController mDragController;
    private View mWeightWatcher;
    private DynamicGridSizeFragment mDynamicGridSizeFragment;

    protected static RemoteFolderManager sRemoteFolderManager;

    private AppWidgetManagerCompat mAppWidgetManager;
    private LauncherAppWidgetHost mAppWidgetHost;

    @Thunk ItemInfo mPendingAddInfo = new ItemInfo();
    private LauncherAppWidgetProviderInfo mPendingAddWidgetInfo;
    private int mPendingAddWidgetId = -1;

    private int[] mTmpAddItemCellCoordinates = new int[2];

    protected FolderIcon mHiddenFolderIcon;
    private boolean mHiddenFolderLockStateChanged = false;
    private boolean mHiddenFolderAuth = false;

    @Thunk Hotseat mHotseat;
    private VerticalSlidingPanel mOverviewPanel;
    private View mDarkPanel;
    OverviewSettingsPanel mOverviewSettingsPanel;

    private View mAllAppsButton;
    private View mWidgetsButton;

    private SearchDropTargetBar mSearchDropTargetBar;

    // Main container view for the all apps screen.
    @Thunk AllAppsContainerView mAppsView;

    // Main container view and the model for the widget tray screen.
    @Thunk WidgetsContainerView mWidgetsView;
    @Thunk WidgetsModel mWidgetsModel;

    private boolean mAutoAdvanceRunning = false;
    private AppWidgetHostView mQsb;

    private Bundle mSavedState;
    // We set the state in both onCreate and then onNewIntent in some cases, which causes both
    // scroll issues (because the workspace may not have been measured yet) and extra work.
    // Instead, just save the state that we need to restore Launcher to, and commit it in onResume.
    private State mOnResumeState = State.NONE;

    private SpannableStringBuilder mDefaultKeySsb = null;

    @Thunk boolean mWorkspaceLoading = true;

    private boolean mPaused = true;
    private boolean mRestoring;
    private boolean mWaitingForResult;
    private boolean mOnResumeNeedsLoad;

    private ArrayList<Runnable> mBindOnResumeCallbacks = new ArrayList<Runnable>();
    private ArrayList<Runnable> mOnResumeCallbacks = new ArrayList<Runnable>();

    private Bundle mSavedInstanceState;

    protected LauncherModel mModel;
    private IconCache mIconCache;
    @Thunk boolean mUserPresent = true;
    private boolean mVisible = false;
    private boolean mHasFocus = false;
    private boolean mAttached = false;

    private LauncherClings mClings;

    private static LongArrayMap<FolderInfo> sFolders = new LongArrayMap<>();

    private View.OnTouchListener mHapticFeedbackTouchListener;

    // Related to the auto-advancing of widgets
    private final int ADVANCE_MSG = 1;
    private final int mAdvanceInterval = 20000;
    private final int mAdvanceStagger = 250;
    private long mAutoAdvanceSentTime;
    private long mAutoAdvanceTimeLeft = -1;
    @Thunk HashMap<View, AppWidgetProviderInfo> mWidgetsToAdvance =
        new HashMap<View, AppWidgetProviderInfo>();

    // Determines how long to wait after a rotation before restoring the screen orientation to
    // match the sensor state.
    private final int mRestoreScreenOrientationDelay = 500;

    @Thunk Drawable mWorkspaceBackgroundDrawable;

    private final ArrayList<Integer> mSynchronouslyBoundPages = new ArrayList<Integer>();
    private static final boolean DISABLE_SYNCHRONOUS_BINDING_CURRENT_PAGE = false;

    static final ArrayList<String> sDumpLogs = new ArrayList<String>();
    static Date sDateStamp = new Date();
    static DateFormat sDateFormat =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    static long sRunStart = System.currentTimeMillis();
    static final String CORRUPTION_EMAIL_SENT_KEY = "corruptionEmailSent";

    // We only want to get the SharedPreferences once since it does an FS stat each time we get
    // it from the context.
    private SharedPreferences mSharedPrefs;

    // Holds the page that we need to animate to, and the icon views that we need to animate up
    // when we scroll to that page on resume.
    @Thunk ImageView mFolderIconImageView;
    private Bitmap mFolderIconBitmap;
    private Canvas mFolderIconCanvas;
    private Rect mRectForFolderAnimation = new Rect();

    private DeviceProfile mDeviceProfile;

    private boolean mUseScrubber = true;

    private boolean mIsDrawerSearchBarEnabled;

    // This is set to the view that launched the activity that navigated the user away from
    // launcher. Since there is no callback for when the activity has finished launching, enable
    // the press state and keep this reference to reset the press state when we return to launcher.
    BubbleTextView mWaitingForResume;

    private long mDefaultScreenId;

    public Animator.AnimatorListener mAnimatorListener = new Animator.AnimatorListener() {
        @Override
        public void onAnimationStart(Animator arg0) {}
        @Override
        public void onAnimationRepeat(Animator arg0) {}
        @Override
        public void onAnimationEnd(Animator arg0) {
            mDarkPanel.setVisibility(View.GONE);
        }
        @Override
        public void onAnimationCancel(Animator arg0) {}
    };

    Runnable mReloadLauncherRunnable = new Runnable() {
        @Override
        public void run() {
            reloadLauncher(mWorkspace.getRestorePage(), true, false);
        }
    };

    private BroadcastReceiver protectedAppsChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Update the workspace
            if (waitUntilResume(mReloadLauncherRunnable, true)) {
                return;
            }

            reloadLauncher(mWorkspace.getRestorePage(), true, false);
        }
    };

    private BroadcastReceiver searchBarVisibilityChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Update the workspace
            if (waitUntilResume(mReloadLauncherRunnable, true)) {
                return;
            }

            reloadLauncher(mWorkspace.getRestorePage(), true, false);
        }
    };

    // Preferences
    private boolean mHideIconLabels;

    protected static HashMap<String, CustomAppWidget> sCustomAppWidgets =
            new HashMap<String, CustomAppWidget>();

    private static final boolean ENABLE_CUSTOM_WIDGET_TEST = false;
    static {
        if (ENABLE_CUSTOM_WIDGET_TEST) {
            sCustomAppWidgets.put(DummyWidget.class.getName(), new DummyWidget());
        }
    }

    @Thunk Runnable mBuildLayersRunnable = new Runnable() {
        public void run() {
            if (mWorkspace != null) {
                mWorkspace.buildPageHardwareLayers();
            }
        }
    };

    private static PendingAddArguments sPendingAddItem;

    @Thunk static class PendingAddArguments {
        int requestCode;
        Intent intent;
        long container;
        long screenId;
        int cellX;
        int cellY;
        int appWidgetId;
    }

    private Stats mStats;
    FocusIndicatorView mFocusHandler;
    private boolean mRotationEnabled = false;

    @Thunk void setOrientation() {
        if (mRotationEnabled) {
            unlockScreenOrientation(true);
        } else {
            setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        }
    }

    private Runnable mUpdateOrientationRunnable = new Runnable() {
        public void run() {
            setOrientation();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG_STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.preOnCreate();
        }

        super.onCreate(savedInstanceState);

        LauncherAppState.setApplicationContext(getApplicationContext());
        LauncherAppState app = LauncherAppState.getInstance();

        initializeDeviceProfile(app);

        mSharedPrefs = getSharedPreferences(LauncherAppState.getSharedPreferencesKey(),
                Context.MODE_PRIVATE);
        mIsSafeModeEnabled = getPackageManager().isSafeMode();

        mDragController = new DragController(this);
        mInflater = getLayoutInflater();
        mStateTransitionAnimation = new LauncherStateTransitionAnimation(this);

        mStats = new Stats(this);

        mAppWidgetManager = AppWidgetManagerCompat.getInstance(this);

        mAppWidgetHost = new LauncherAppWidgetHost(this, APPWIDGET_HOST_ID);
        mAppWidgetHost.startListening();

        if (sRemoteFolderManager == null) {
            sRemoteFolderManager = new RemoteFolderManager(this);
        } else {
            sRemoteFolderManager.onRecreateLauncher(this);
        }

        // If we are getting an onCreate, we can actually preempt onResume and unset mPaused here,
        // this also ensures that any synchronous binding below doesn't re-trigger another
        // LauncherModel load.
        mPaused = false;

        if (PROFILE_STARTUP) {
            android.os.Debug.startMethodTracing(
                    Environment.getExternalStorageDirectory() + "/launcher");
        }

        setContentView(R.layout.launcher);

        setupViews();
        mDeviceProfile.layout(this);

        lockAllApps();

        mSavedState = savedInstanceState;
        restoreState(mSavedState);

        if (PROFILE_STARTUP) {
            android.os.Debug.stopMethodTracing();
        }

        if (!mRestoring) {
            if (DISABLE_SYNCHRONOUS_BINDING_CURRENT_PAGE) {
                // If the user leaves launcher, then we should just load items asynchronously when
                // they return.
                mModel.startLoader(PagedView.INVALID_RESTORE_PAGE);
            } else {
                // We only load the page synchronously if the user rotates (or triggers a
                // configuration change) while launcher is in the foreground
                mModel.startLoader(mWorkspace.getRestorePage());
            }
        }

        // For handling default keys
        mDefaultKeySsb = new SpannableStringBuilder();
        Selection.setSelection(mDefaultKeySsb, 0);

        IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mCloseSystemDialogsReceiver, filter);

        filter = new IntentFilter(NetworkConnectionReceiver.INTENT_ACTION);
        registerReceiver(mConnectionReceiver, filter);

        filter = new IntentFilter(DeviceUnlockedReceiver.INTENT_ACTION);
        registerReceiver(mDeviceUnlockedReceiver, filter);

        mRotationEnabled = Utilities.isRotationAllowedForDevice(getApplicationContext());
        // In case we are on a device with locked rotation, we should look at preferences to check
        // if the user has specifically allowed rotation.
        if (!mRotationEnabled) {
            mRotationEnabled = Utilities.isAllowRotationPrefEnabled(getApplicationContext(), false);
        }

        // On large interfaces, or on devices that a user has specifically enabled screen rotation,
        // we want the screen to auto-rotate based on the current orientation
        setOrientation();

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onCreate(savedInstanceState);
            if (mLauncherCallbacks.hasLauncherOverlay()) {
                ViewStub stub = (ViewStub) findViewById(R.id.launcher_overlay_stub);
                mLauncherOverlayContainer = (InsettableFrameLayout) stub.inflate();
                mLauncherOverlay = mLauncherCallbacks.setLauncherOverlayView(
                        mLauncherOverlayContainer, mLauncherOverlayCallbacks);
                mWorkspace.setLauncherOverlay(mLauncherOverlay);
            }
        }

        if (shouldShowIntroScreen()) {
            showIntroScreen();
        } else {
            showFirstRunActivity();
            showFirstRunClings();
        }

        IntentFilter protectedAppsFilter = new IntentFilter(
                cyanogenmod.content.Intent.ACTION_PROTECTED_CHANGED);
        registerReceiver(protectedAppsChangedReceiver, protectedAppsFilter,
                cyanogenmod.platform.Manifest.permission.PROTECTED_APP, null);

        IntentFilter searchBarVisibilityFilter = new IntentFilter(
                SettingsPinnedHeaderAdapter.ACTION_SEARCH_BAR_VISIBILITY_CHANGED);
        registerReceiver(searchBarVisibilityChangedReceiver, searchBarVisibilityFilter);
    }

    @Override
    public void onSettingsChanged(String settings, boolean value) {
        if (SettingsProvider.SETTINGS_UI_ALLOW_ROTATION.equals(settings)) {
            mRotationEnabled = value;
            if (!waitUntilResume(mUpdateOrientationRunnable, true)) {
                mUpdateOrientationRunnable.run();
            }
        }
    }

    private LauncherCallbacks mLauncherCallbacks;

    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onPostCreate(savedInstanceState);
        }
    }

    public boolean setLauncherCallbacks(LauncherCallbacks callbacks) {
        mLauncherCallbacks = callbacks;
        mLauncherCallbacks.setLauncherSearchCallback(new Launcher.LauncherSearchCallbacks() {
            private boolean mWorkspaceImportanceStored = false;
            private boolean mHotseatImportanceStored = false;
            private int mWorkspaceImportanceForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
            private int mHotseatImportanceForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;

            @Override
            public void onSearchOverlayOpened() {
                if (mWorkspaceImportanceStored || mHotseatImportanceStored) {
                    return;
                }
                // The underlying workspace and hotseat are temporarily suppressed by the search
                // overlay. So they sholudn't be accessible.
                if (mWorkspace != null) {
                    mWorkspaceImportanceForAccessibility =
                            mWorkspace.getImportantForAccessibility();
                    mWorkspace.setImportantForAccessibility(
                            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                    mWorkspaceImportanceStored = true;
                }
                if (mHotseat != null) {
                    mHotseatImportanceForAccessibility = mHotseat.getImportantForAccessibility();
                    mHotseat.setImportantForAccessibility(
                            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                    mHotseatImportanceStored = true;
                }
            }

            @Override
            public void onSearchOverlayClosed() {
                if (mWorkspaceImportanceStored && mWorkspace != null) {
                    mWorkspace.setImportantForAccessibility(mWorkspaceImportanceForAccessibility);
                }
                if (mHotseatImportanceStored && mHotseat != null) {
                    mHotseat.setImportantForAccessibility(mHotseatImportanceForAccessibility);
                }
                mWorkspaceImportanceStored = false;
                mHotseatImportanceStored = false;
            }
        });
        return true;
    }

    @Override
    public void onLauncherProviderChange() {
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onLauncherProviderChange();
        }
    }

    /**
     * Updates the bounds of all the overlays to match the new fixed bounds.
     */
    public void updateOverlayBounds(Rect newBounds) {
        mAppsView.setSearchBarBounds(newBounds);
        mWidgetsView.setSearchBarBounds(newBounds);
    }

    /** To be overridden by subclasses to hint to Launcher that we have custom content */
    protected boolean hasCustomContentToLeft() {
        if (mLauncherCallbacks != null) {
            return mLauncherCallbacks.hasCustomContentToLeft();
        }
        return false;
    }

    /**
     * To be overridden by subclasses to populate the custom content container and call
     * {@link #addToCustomContentPage}. This will only be invoked if
     * {@link #hasCustomContentToLeft()} is {@code true}.
     */
    protected void populateCustomContentContainer() {
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.populateCustomContentContainer();
        }
    }

    /**
     * Invoked by subclasses to signal a change to the {@link #addCustomContentToLeft} value to
     * ensure the custom content page is added or removed if necessary.
     */
    protected void invalidateHasCustomContentToLeft() {
        if (mWorkspace == null || mWorkspace.getScreenOrder().isEmpty()) {
            // Not bound yet, wait for bindScreens to be called.
            return;
        }

        if (!mWorkspace.hasCustomContent() && hasCustomContentToLeft()) {
            // Create the custom content page and call the subclass to populate it.
            mWorkspace.createCustomContentContainer();
            populateCustomContentContainer();
        } else if (mWorkspace.hasCustomContent() && !hasCustomContentToLeft()) {
            mWorkspace.removeCustomContentPage();
        }
    }

    public Stats getStats() {
        return mStats;
    }

    public LayoutInflater getInflater() {
        return mInflater;
    }

    public boolean isDraggingEnabled() {
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        return !isWorkspaceLoading();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static int generateViewId() {
        if (Utilities.ATLEAST_JB_MR1) {
            return View.generateViewId();
        } else {
            // View.generateViewId() is not available. The following fallback logic is a copy
            // of its implementation.
            for (;;) {
                final int result = sNextGeneratedId.get();
                // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
                int newValue = result + 1;
                if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
                if (sNextGeneratedId.compareAndSet(result, newValue)) {
                    return result;
                }
            }
        }
    }

    public int getViewIdForItem(ItemInfo info) {
        // This cast is safe given the > 2B range for int.
        int itemId = (int) info.id;
        if (mItemIdToViewId.containsKey(itemId)) {
            return mItemIdToViewId.get(itemId);
        }
        int viewId = generateViewId();
        mItemIdToViewId.put(itemId, viewId);
        return viewId;
    }

    /**
     * Returns whether we should delay spring loaded mode -- for shortcuts and widgets that have
     * a configuration step, this allows the proper animations to run after other transitions.
     */
    private long completeAdd(PendingAddArguments args) {
        long screenId = args.screenId;
        if (args.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            // When the screen id represents an actual screen (as opposed to a rank) we make sure
            // that the drop page actually exists.
            screenId = ensurePendingDropLayoutExists(args.screenId);
        }

        switch (args.requestCode) {
            case REQUEST_CREATE_SHORTCUT:
                completeAddShortcut(args.intent, args.container, screenId, args.cellX,
                        args.cellY);
                break;
            case REQUEST_CREATE_APPWIDGET:
                completeAddAppWidget(args.appWidgetId, args.container, screenId, null, null);
                break;
            case REQUEST_RECONFIGURE_APPWIDGET:
                completeRestoreAppWidget(args.appWidgetId);
                break;
        }
        // Before adding this resetAddInfo(), after a shortcut was added to a workspace screen,
        // if you turned the screen off and then back while in All Apps, Launcher would not
        // return to the workspace. Clearing mAddInfo.container here fixes this issue
        resetAddInfo();
        return screenId;
    }

    private void handleActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        // Reset the startActivity waiting flag
        setWaitingForResult(false);
        final int pendingAddWidgetId = mPendingAddWidgetId;
        mPendingAddWidgetId = -1;

        Runnable exitSpringLoaded = new Runnable() {
            @Override
            public void run() {
                exitSpringLoadedDragModeDelayed((resultCode != RESULT_CANCELED),
                        EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
            }
        };

        if (requestCode == REQUEST_BIND_APPWIDGET) {
            // This is called only if the user did not previously have permissions to bind widgets
            final int appWidgetId = data != null ?
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
            if (resultCode == RESULT_CANCELED) {
                completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId);
                mWorkspace.removeExtraEmptyScreenDelayed(true, exitSpringLoaded,
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
            } else if (resultCode == RESULT_OK) {
                addAppWidgetImpl(appWidgetId, mPendingAddInfo, null,
                        mPendingAddWidgetInfo, ON_ACTIVITY_RESULT_ANIMATION_DELAY);

                // When the user has granted permission to bind widgets, we should check to see if
                // we can inflate the default search bar widget.
                getOrCreateQsbBar();
                showWorkspace(false);
            }
            return;
        } else if (requestCode == REQUEST_PICK_WALLPAPER) {
            if (resultCode == RESULT_OK && mWorkspace.isInOverviewMode()) {
                showWorkspace(false);
            }
            return;
        } else if (requestCode == REQUEST_OPEN_PROTECTED_FOLDER) {
            mHiddenFolderAuth = resultCode == RESULT_OK;
            if (mHiddenFolderIcon != null && mHiddenFolderAuth) {
                openFolder(mHiddenFolderIcon, null);
            } else {
                mHiddenFolderAuth = false;
            }
            return;
        } else if (requestCode == REQUEST_PROTECT_FOLDER) {
            mHiddenFolderAuth = resultCode == RESULT_OK;
            if (mHiddenFolderIcon != null && mHiddenFolderAuth) {
                mHiddenFolderIcon.getFolder().saveHiddenFolderState(true);
                mHiddenFolderLockStateChanged = true;
            } else {
                mHiddenFolderAuth = false;
            }
        } else if (requestCode == REQUEST_UNPROTECT_FOLDER) {
            mHiddenFolderAuth = resultCode == RESULT_OK;
            if (mHiddenFolderIcon != null && mHiddenFolderAuth) {
                mHiddenFolderIcon.getFolder().saveHiddenFolderState(false);
                mHiddenFolderLockStateChanged = true;
            } else {
                mHiddenFolderAuth = false;
            }
        }

        boolean isWidgetDrop = (requestCode == REQUEST_PICK_APPWIDGET ||
                requestCode == REQUEST_CREATE_APPWIDGET);

        final boolean workspaceLocked = isWorkspaceLocked();
        // We have special handling for widgets
        if (isWidgetDrop) {
            final int appWidgetId;
            int widgetId = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    : -1;
            if (widgetId < 0) {
                appWidgetId = pendingAddWidgetId;
            } else {
                appWidgetId = widgetId;
            }

            final int result;
            if (appWidgetId < 0 || resultCode == RESULT_CANCELED) {
                Log.e(TAG, "Error: appWidgetId (EXTRA_APPWIDGET_ID) was not " +
                        "returned from the widget configuration activity.");
                result = RESULT_CANCELED;
                completeTwoStageWidgetDrop(result, appWidgetId);
                final Runnable onComplete = new Runnable() {
                    @Override
                    public void run() {
                        exitSpringLoadedDragModeDelayed(false, 0, null);
                    }
                };
                if (workspaceLocked) {
                    // No need to remove the empty screen if we're mid-binding, as the
                    // the bind will not add the empty screen.
                    mWorkspace.postDelayed(onComplete, ON_ACTIVITY_RESULT_ANIMATION_DELAY);
                } else {
                    mWorkspace.removeExtraEmptyScreenDelayed(true, onComplete,
                            ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
                }
            } else {
                if (!workspaceLocked) {
                    if (mPendingAddInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                        // When the screen id represents an actual screen (as opposed to a rank)
                        // we make sure that the drop page actually exists.
                        mPendingAddInfo.screenId =
                                ensurePendingDropLayoutExists(mPendingAddInfo.screenId);
                    }
                    final CellLayout dropLayout = mWorkspace.getScreenWithId(mPendingAddInfo.screenId);

                    dropLayout.setDropPending(true);
                    final Runnable onComplete = new Runnable() {
                        @Override
                        public void run() {
                            completeTwoStageWidgetDrop(resultCode, appWidgetId);
                            dropLayout.setDropPending(false);
                        }
                    };
                    mWorkspace.removeExtraEmptyScreenDelayed(true, onComplete,
                            ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
                } else {
                    PendingAddArguments args = preparePendingAddArgs(requestCode, data, appWidgetId,
                            mPendingAddInfo);
                    sPendingAddItem = args;
                }
            }
            return;
        }

        if (requestCode == REQUEST_RECONFIGURE_APPWIDGET) {
            if (resultCode == RESULT_OK) {
                // Update the widget view.
                PendingAddArguments args = preparePendingAddArgs(requestCode, data,
                        pendingAddWidgetId, mPendingAddInfo);
                if (workspaceLocked) {
                    sPendingAddItem = args;
                } else {
                    completeAdd(args);
                }
            }
            // Leave the widget in the pending state if the user canceled the configure.
            return;
        }

        // The pattern used here is that a user PICKs a specific application,
        // which, depending on the target, might need to CREATE the actual target.

        // For example, the user would PICK_SHORTCUT for "Music playlist", and we
        // launch over to the Music app to actually CREATE_SHORTCUT.
        if (resultCode == RESULT_OK && mPendingAddInfo.container != ItemInfo.NO_ID) {
            final PendingAddArguments args = preparePendingAddArgs(requestCode, data, -1,
                    mPendingAddInfo);
            if (isWorkspaceLocked()) {
                sPendingAddItem = args;
            } else {
                completeAdd(args);
                mWorkspace.removeExtraEmptyScreenDelayed(true, exitSpringLoaded,
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
            }
        } else if (resultCode == RESULT_CANCELED) {
            mWorkspace.removeExtraEmptyScreenDelayed(true, exitSpringLoaded,
                    ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
        }
        mDragLayer.clearAnimatedView();

    }

    @Override
    protected void onActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        handleActivityResult(requestCode, resultCode, data);
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onActivityResult(requestCode, resultCode, data);
        }
    }

    /** @Override for MNC */
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_CALL_PHONE && sPendingAddItem != null
                && sPendingAddItem.requestCode == REQUEST_PERMISSION_CALL_PHONE) {
            View v = null;
            CellLayout layout = getCellLayout(sPendingAddItem.container, sPendingAddItem.screenId);
            if (layout != null) {
                v = layout.getChildAt(sPendingAddItem.cellX, sPendingAddItem.cellY);
            }
            Intent intent = sPendingAddItem.intent;
            sPendingAddItem = null;
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivity(v, intent, null);
            } else {
                // TODO: Show a snack bar with link to settings
                Toast.makeText(this, getString(R.string.msg_no_phone_permission,
                        getString(R.string.app_name)), Toast.LENGTH_SHORT).show();
            }
        }
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onRequestPermissionsResult(requestCode, permissions,
                    grantResults);
        }
    }

    private PendingAddArguments preparePendingAddArgs(int requestCode, Intent data, int
            appWidgetId, ItemInfo info) {
        PendingAddArguments args = new PendingAddArguments();
        args.requestCode = requestCode;
        args.intent = data;
        args.container = info.container;
        args.screenId = info.screenId;
        args.cellX = info.cellX;
        args.cellY = info.cellY;
        args.appWidgetId = appWidgetId;
        return args;
    }

    /**
     * Check to see if a given screen id exists. If not, create it at the end, return the new id.
     *
     * @param screenId the screen id to check
     * @return the new screen, or screenId if it exists
     */
    private long ensurePendingDropLayoutExists(long screenId) {
        CellLayout dropLayout =
                (CellLayout) mWorkspace.getScreenWithId(screenId);
        if (dropLayout == null) {
            // it's possible that the add screen was removed because it was
            // empty and a re-bind occurred
            mWorkspace.addExtraEmptyScreen();
            return mWorkspace.commitExtraEmptyScreen();
        } else {
            return screenId;
        }
    }

    @Thunk void completeTwoStageWidgetDrop(final int resultCode, final int appWidgetId) {
        CellLayout cellLayout =
                (CellLayout) mWorkspace.getScreenWithId(mPendingAddInfo.screenId);
        Runnable onCompleteRunnable = null;
        int animationType = 0;

        AppWidgetHostView boundWidget = null;
        if (resultCode == RESULT_OK) {
            animationType = Workspace.COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION;
            final AppWidgetHostView layout = mAppWidgetHost.createView(this, appWidgetId,
                    mPendingAddWidgetInfo);
            boundWidget = layout;
            onCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    completeAddAppWidget(appWidgetId, mPendingAddInfo.container,
                            mPendingAddInfo.screenId, layout, null);
                    exitSpringLoadedDragModeDelayed((resultCode != RESULT_CANCELED),
                            EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
                }
            };
        } else if (resultCode == RESULT_CANCELED) {
            mAppWidgetHost.deleteAppWidgetId(appWidgetId);
            animationType = Workspace.CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION;
        }
        if (mDragLayer.getAnimatedView() != null) {
            mWorkspace.animateWidgetDrop(mPendingAddInfo, cellLayout,
                    (DragView) mDragLayer.getAnimatedView(), onCompleteRunnable,
                    animationType, boundWidget, true);
        } else if (onCompleteRunnable != null) {
            // The animated view may be null in the case of a rotation during widget configuration
            onCompleteRunnable.run();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirstFrameAnimatorHelper.setIsVisible(false);

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onStop();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirstFrameAnimatorHelper.setIsVisible(true);

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onStart();
        }
    }

    @Override
    protected void onResume() {
        long startTime = 0;
        if (DEBUG_RESUME_TIME) {
            startTime = System.currentTimeMillis();
            Log.v(TAG, "Launcher.onResume()");
        }

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.preOnResume();
        }

        super.onResume();

        // Restore the previous launcher state
        if (mOnResumeState == State.WORKSPACE) {
            showWorkspace(false);
        } else if (mOnResumeState == State.APPS) {
            boolean launchedFromApp = (mWaitingForResume != null);
            // Don't update the predicted apps if the user is returning to launcher in the apps
            // view after launching an app, as they may be depending on the UI to be static to
            // switch to another app, otherwise, if it was
            showAppsView(false /* animated */, false /* resetListToTop */,
                    !launchedFromApp /* updatePredictedApps */, false /* focusSearchBar */);
            mWorkspace.setVisibility(View.INVISIBLE);
        } else if (mOnResumeState == State.WIDGETS) {
            showWidgetsView(false, false);
        }
        mOnResumeState = State.NONE;

        // Background was set to gradient in onPause(), restore to transparent if in all apps.
        setWorkspaceBackground(mState == State.WORKSPACE ? WORKSPACE_BACKGROUND_GRADIENT
                : WORKSPACE_BACKGROUND_TRANSPARENT);

        mPaused = false;
        if (mRestoring || mOnResumeNeedsLoad) {
            setWorkspaceLoading(true);

            // If we're starting binding all over again, clear any bind calls we'd postponed in
            // the past (see waitUntilResume) -- we don't need them since we're starting binding
            // from scratch again
            mBindOnResumeCallbacks.clear();

            mModel.startLoader(PagedView.INVALID_RESTORE_PAGE);
            mRestoring = false;
            mOnResumeNeedsLoad = false;
        }
        if (mBindOnResumeCallbacks.size() > 0) {
            // We might have postponed some bind calls until onResume (see waitUntilResume) --
            // execute them here
            long startTimeCallbacks = 0;
            if (DEBUG_RESUME_TIME) {
                startTimeCallbacks = System.currentTimeMillis();
            }

            for (int i = 0; i < mBindOnResumeCallbacks.size(); i++) {
                mBindOnResumeCallbacks.get(i).run();
            }
            mBindOnResumeCallbacks.clear();
            if (DEBUG_RESUME_TIME) {
                Log.d(TAG, "Time spent processing callbacks in onResume: " +
                    (System.currentTimeMillis() - startTimeCallbacks));
            }
        }
        if (mOnResumeCallbacks.size() > 0) {
            for (int i = 0; i < mOnResumeCallbacks.size(); i++) {
                mOnResumeCallbacks.get(i).run();
            }
            mOnResumeCallbacks.clear();
        }

        // Reset the pressed state of icons that were locked in the press state while activities
        // were launching
        if (mWaitingForResume != null) {
            // Resets the previous workspace icon press state
            mWaitingForResume.setStayPressed(false);
        }

        // It is possible that widgets can receive updates while launcher is not in the foreground.
        // Consequently, the widgets will be inflated in the orientation of the foreground activity
        // (framework issue). On resuming, we ensure that any widgets are inflated for the current
        // orientation.
        getWorkspace().reinflateWidgetsIfNecessary();
        reinflateQSBIfNecessary();

        if (DEBUG_RESUME_TIME) {
            Log.d(TAG, "Time spent in onResume: " + (System.currentTimeMillis() - startTime));
        }

        if (mWorkspace.getCustomContentCallbacks() != null) {
            // If we are resuming and the custom content is the current page, we call onShow().
            // It is also poassible that onShow will instead be called slightly after first layout
            // if PagedView#setRestorePage was set to the custom content page in onCreate().
            if (mWorkspace.isOnOrMovingToCustomContent()) {
                mWorkspace.getCustomContentCallbacks().onShow(true);
            }
        }
        updateInteraction(Workspace.State.NORMAL, mWorkspace.getState());
        mWorkspace.onResume();

        if (!isWorkspaceLoading()) {
            // Process any items that were added while Launcher was away.
            InstallShortcutReceiver.disableAndFlushInstallQueue(this);
        }

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onResume();
        }

        // Close out fragments
        Fragment gridFragment = getFragmentManager().findFragmentByTag(
                DynamicGridSizeFragment.DYNAMIC_GRID_SIZE_FRAGMENT);
        if (gridFragment != null) {
            mDynamicGridSizeFragment.setSize();
            unlockScreenOrientation(true);
        }
    }

    @Override
    protected void onPause() {
        // Ensure that items added to Launcher are queued until Launcher returns
        InstallShortcutReceiver.enableInstallQueue();

        super.onPause();
        mPaused = true;
        mDragController.cancelDrag();
        mDragController.resetLastGestureUpTime();

        // We call onHide() aggressively. The custom content callbacks should be able to
        // debounce excess onHide calls.
        if (mWorkspace.getCustomContentCallbacks() != null) {
            mWorkspace.getCustomContentCallbacks().onHide();
        }

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onPause();
        }
    }

    public interface CustomContentCallbacks {
        // Custom content is completely shown. {@code fromResume} indicates whether this was caused
        // by a onResume or by scrolling otherwise.
        public void onShow(boolean fromResume);

        // Custom content is completely hidden
        public void onHide();

        // Custom content scroll progress changed. From 0 (not showing) to 1 (fully showing).
        public void onScrollProgressChanged(float progress);

        // Indicates whether the user is allowed to scroll away from the custom content.
        boolean isScrollingAllowed();
    }

    public interface LauncherOverlay {

        /**
         * Touch interaction leading to overscroll has begun
         */
        public void onScrollInteractionBegin();

        /**
         * Touch interaction related to overscroll has ended
         */
        public void onScrollInteractionEnd();

        /**
         * Scroll progress, between 0 and 100, when the user scrolls beyond the leftmost
         * screen (or in the case of RTL, the rightmost screen).
         */
        public void onScrollChange(int progress, boolean rtl);

        /**
         * Screen has stopped scrolling
         */
        public void onScrollSettled();

        /**
         * This method can be called by the Launcher in order to force the LauncherOverlay
         * to exit fully immersive mode.
         */
        public void forceExitFullImmersion();
    }

    public interface LauncherSearchCallbacks {
        /**
         * Called when the search overlay is shown.
         */
        public void onSearchOverlayOpened();

        /**
         * Called when the search overlay is dismissed.
         */
        public void onSearchOverlayClosed();
    }

    public interface LauncherOverlayCallbacks {
        /**
         * This method indicates whether a call to {@link #enterFullImmersion()} will succeed,
         * however it doesn't modify any state within the launcher.
         */
        public boolean canEnterFullImmersion();

        /**
         * Should be called to tell Launcher that the LauncherOverlay will take over interaction,
         * eg. by occupying the full screen and handling all touch events.
         *
         * @return true if Launcher allows the LauncherOverlay to become fully immersive. In this
         *          case, Launcher will modify any necessary state and assumes the overlay is
         *          handling all interaction. If false, the LauncherOverlay should cancel any
         *
         */
        public boolean enterFullImmersion();

        /**
         * Must be called when exiting fully immersive mode. Indicates to Launcher that it has
         * full control over UI and state.
         */
        public void exitFullImmersion();
    }

    class LauncherOverlayCallbacksImpl implements LauncherOverlayCallbacks {

        @Override
        public boolean canEnterFullImmersion() {
            return mState == State.WORKSPACE;
        }

        @Override
        public boolean enterFullImmersion() {
            if (mState == State.WORKSPACE) {
                // When fully immersed, disregard any touches which fall through.
                mDragLayer.setBlockTouch(true);
                return true;
            }
            return false;
        }

        @Override
        public void exitFullImmersion() {
            mDragLayer.setBlockTouch(false);
        }
    }

    protected boolean hasSettings() {
        if (mLauncherCallbacks != null) {
            return mLauncherCallbacks.hasSettings();
        } else {
            // On devices with a locked orientation, we will at least have the allow rotation
            // setting.
            return !Utilities.isRotationAllowedForDevice(this);
        }
    }

    public void addToCustomContentPage(View customContent,
            CustomContentCallbacks callbacks, String description) {
        mWorkspace.addToCustomContentPage(customContent, callbacks, description);
    }

    // The custom content needs to offset its content to account for the QSB
    public int getTopOffsetForCustomContent() {
        return mWorkspace.getPaddingTop();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Flag the loader to stop early before switching
        if (mModel.isCurrentCallbacks(this)) {
            mModel.stopLoader();
        }
        //TODO(hyunyoungs): stop the widgets loader when there is a rotation.

        return Boolean.TRUE;
    }

    // We can't hide the IME if it was forced open.  So don't bother
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mHasFocus = hasFocus;

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onWindowFocusChanged(hasFocus);
        }
    }

    private boolean acceptFilter() {
        final InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        return !inputManager.isFullscreenMode();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final int uniChar = event.getUnicodeChar();
        final boolean handled = super.onKeyDown(keyCode, event);
        final boolean isKeyNotWhitespace = uniChar > 0 && !Character.isWhitespace(uniChar);
        if (!handled && acceptFilter() && isKeyNotWhitespace) {
            boolean gotKey = TextKeyListener.getInstance().onKeyDown(mWorkspace, mDefaultKeySsb,
                    keyCode, event);
            if (gotKey && mDefaultKeySsb != null && mDefaultKeySsb.length() > 0) {
                // something usable has been typed - start a search
                // the typed text will be retrieved and cleared by
                // showSearchDialog()
                // If there are multiple keystrokes before the search dialog takes focus,
                // onSearchRequested() will be called for every keystroke,
                // but it is idempotent, so it's fine.
                return onSearchRequested();
            }
        }

        // Eat the long press event so the keyboard doesn't come up.
        if (keyCode == KeyEvent.KEYCODE_MENU && event.isLongPress()) {
            return true;
        }

        return handled;
    }

    private String getTypedText() {
        return mDefaultKeySsb.toString();
    }

    private void clearTypedText() {
        mDefaultKeySsb.clear();
        mDefaultKeySsb.clearSpans();
        Selection.setSelection(mDefaultKeySsb, 0);
    }

    /**
     * Given the integer (ordinal) value of a State enum instance, convert it to a variable of type
     * State
     */
    private static State intToState(int stateOrdinal) {
        State state = State.WORKSPACE;
        final State[] stateValues = State.values();
        for (int i = 0; i < stateValues.length; i++) {
            if (stateValues[i].ordinal() == stateOrdinal) {
                state = stateValues[i];
                break;
            }
        }
        return state;
    }

    /**
     * Restores the previous state, if it exists.
     *
     * @param savedState The previous state.
     */
    @SuppressWarnings("unchecked")
    private void restoreState(Bundle savedState) {
        if (savedState == null) {
            return;
        }

        State state = intToState(savedState.getInt(RUNTIME_STATE, State.WORKSPACE.ordinal()));
        if (state == State.APPS || state == State.WIDGETS) {
            mOnResumeState = state;
        }

        int currentScreen = savedState.getInt(RUNTIME_STATE_CURRENT_SCREEN,
                PagedView.INVALID_RESTORE_PAGE);
        if (currentScreen != PagedView.INVALID_RESTORE_PAGE) {
            mWorkspace.setRestorePage(currentScreen);
        }

        final long pendingAddContainer = savedState.getLong(RUNTIME_STATE_PENDING_ADD_CONTAINER, -1);
        final long pendingAddScreen = savedState.getLong(RUNTIME_STATE_PENDING_ADD_SCREEN, -1);

        if (pendingAddContainer != ItemInfo.NO_ID && pendingAddScreen > -1) {
            mPendingAddInfo.container = pendingAddContainer;
            mPendingAddInfo.screenId = pendingAddScreen;
            mPendingAddInfo.cellX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_X);
            mPendingAddInfo.cellY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_Y);
            mPendingAddInfo.spanX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_X);
            mPendingAddInfo.spanY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y);
            AppWidgetProviderInfo info = savedState.getParcelable(
                    RUNTIME_STATE_PENDING_ADD_WIDGET_INFO);
            mPendingAddWidgetInfo = info == null ?
                    null : LauncherAppWidgetProviderInfo.fromProviderInfo(this, info);

            mPendingAddWidgetId = savedState.getInt(RUNTIME_STATE_PENDING_ADD_WIDGET_ID);
            setWaitingForResult(true);
            mRestoring = true;
        }

        mItemIdToViewId = (HashMap<Integer, Integer>)
                savedState.getSerializable(RUNTIME_STATE_VIEW_IDS);
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    private void setupViews() {
        final DragController dragController = mDragController;

        mLauncherView = findViewById(R.id.launcher);
        mFocusHandler = (FocusIndicatorView) findViewById(R.id.focus_indicator);
        mDragLayer = (DragLayer) findViewById(R.id.drag_layer);
        mWorkspace = (Workspace) mDragLayer.findViewById(R.id.workspace);
        mWorkspace.setPageSwitchListener(this);
        mPageIndicators = mDragLayer.findViewById(R.id.page_indicator);

        mLauncherView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mWorkspaceBackgroundDrawable = getResources().getDrawable(R.drawable.workspace_bg);

        // Setup the drag layer
        mDragLayer.setup(this, dragController);

        // Setup the hotseat
        mHotseat = (Hotseat) findViewById(R.id.hotseat);
        if (mHotseat != null) {
            mHotseat.setOnLongClickListener(this);
        }

        // Setup the overview panel
        mOverviewPanel = (VerticalSlidingPanel) findViewById(R.id.overview_panel);
        mOverviewSettingsPanel = new OverviewSettingsPanel(this);
        mOverviewSettingsPanel.initializeAdapter();

        mDarkPanel = mOverviewPanel.findViewById(R.id.dark_panel);

        mWidgetsButton = findViewById(R.id.widget_button);
        mWidgetsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (!mWorkspace.isSwitchingState()) {
                    onClickAddWidgetButton(arg0);
                }
            }
        });
        mWidgetsButton.setOnTouchListener(getHapticFeedbackTouchListener());

        View wallpaperButton = findViewById(R.id.wallpaper_button);
        wallpaperButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (!mWorkspace.isSwitchingState()) {
                    onClickWallpaperPicker(arg0);
                }
            }
        });
        wallpaperButton.setOnTouchListener(getHapticFeedbackTouchListener());

        View settingsButton = findViewById(R.id.settings_button);
        if (hasSettings()) {
            settingsButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    if (!mWorkspace.isSwitchingState()) {
                        onClickSettingsButton(arg0);
                    }
                }
            });
            settingsButton.setOnTouchListener(getHapticFeedbackTouchListener());
        } else {
            settingsButton.setVisibility(View.GONE);
        }

        View defaultScreenButton = findViewById(R.id.default_screen_button);
        defaultScreenButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (!mWorkspace.isSwitchingState()) {
                    onClickDefaultScreenButton(arg0);
                }
            }
        });
        defaultScreenButton.setOnTouchListener(getHapticFeedbackTouchListener());

        mOverviewPanel.setPanelSlideListener(new SettingsPanelSlideListener());
        mOverviewPanel.setEnableDragViewTouchEvents(true);

        View settingsPaneHeader = mOverviewPanel.findViewById(R.id.settings_pane_header);
        if (settingsPaneHeader != null) {
            mOverviewPanel.setDragView(settingsPaneHeader);
            settingsPaneHeader.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mOverviewPanel.isExpanded()) {
                        mOverviewPanel.collapsePane();
                    } else {
                        mOverviewPanel.expandPane();
                    }
                }
            });
        }

        mOverviewPanel.setAlpha(0f);

        // Setup the workspace
        mWorkspace.setHapticFeedbackEnabled(false);
        mWorkspace.setOnLongClickListener(this);
        mWorkspace.setup(dragController);
        dragController.addDragListener(mWorkspace);

        // Get the search/delete bar
        mSearchDropTargetBar = (SearchDropTargetBar)
                mDragLayer.findViewById(R.id.search_drop_target_bar);

        // Setup Apps and Widgets
        mAppsView = (AllAppsContainerView) findViewById(R.id.apps_view);
        mWidgetsView = (WidgetsContainerView) findViewById(R.id.widgets_view);
        setupSearchBar(this);

        // Setup the drag controller (drop targets have to be added in reverse order in priority)
        dragController.setDragScoller(mWorkspace);
        dragController.setScrollView(mDragLayer);
        dragController.setMoveTarget(mWorkspace);
        dragController.addDropTarget(mWorkspace);
        if (mSearchDropTargetBar != null) {
            mSearchDropTargetBar.setup(this, dragController);
            mSearchDropTargetBar.setQsbSearchBar(getOrCreateQsbBar());
        }

        if (getResources().getBoolean(R.bool.debug_memory_enabled)) {
            Log.v(TAG, "adding WeightWatcher");
            mWeightWatcher = new WeightWatcher(this);
            mWeightWatcher.setAlpha(0.5f);
            ((FrameLayout) mLauncherView).addView(mWeightWatcher,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM)
            );

            boolean show = shouldShowWeightWatcher();
            mWeightWatcher.setVisibility(show ? View.VISIBLE : View.GONE);
        }

        sRemoteFolderManager.onSetupViews();
    }

    /**
     * Sets the all apps button. This method is called from {@link Hotseat}.
     */
    public void setAllAppsButton(View allAppsButton) {
        mAllAppsButton = allAppsButton;
    }

    public View getAllAppsButton() {
        return mAllAppsButton;
    }

    public View getWidgetsButton() {
        return mWidgetsButton;
    }

    /**
     * Creates a view representing a shortcut.
     *
     * @param info The data structure describing the shortcut.
     */
    View createShortcut(ShortcutInfo info) {
        return createShortcut((ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentPage()), info);
    }

    /**
     * Creates a view representing a shortcut inflated from the specified resource.
     *
     * @param parent The group the shortcut belongs to.
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from layoutResId.
     */
    public View createShortcut(ViewGroup parent, ShortcutInfo info) {
        BubbleTextView favorite = (BubbleTextView) mInflater.inflate(R.layout.app_icon,
                parent, false);
        favorite.applyFromShortcutInfo(info, mIconCache);
        favorite.setTextVisibility(!mHideIconLabels);
        favorite.setCompoundDrawablePadding(mDeviceProfile.iconDrawablePaddingPx);
        favorite.setOnClickListener(this);
        favorite.setOnFocusChangeListener(mFocusHandler);
        return favorite;
    }

    /**
     * Add a shortcut to the workspace.
     *
     * @param data The intent describing the shortcut.
     */
    private void completeAddShortcut(Intent data, long container, long screenId, int cellX,
            int cellY) {
        int[] cellXY = mTmpAddItemCellCoordinates;
        int[] touchXY = mPendingAddInfo.dropPos;
        CellLayout layout = getCellLayout(container, screenId);

        ShortcutInfo info = InstallShortcutReceiver.fromShortcutIntent(this, data);
        if (info == null) {
            return;
        }
        final View view = createShortcut(info);

        boolean foundCellSpan = false;
        // First we check if we already know the exact location where we want to add this item.
        if (cellX >= 0 && cellY >= 0) {
            cellXY[0] = cellX;
            cellXY[1] = cellY;
            foundCellSpan = true;

            // If appropriate, either create a folder or add to an existing folder
            if (mWorkspace.createUserFolderIfNecessary(view, container, layout, cellXY, 0,
                    true, null,null)) {
                return;
            }
            DragObject dragObject = new DragObject();
            dragObject.dragInfo = info;
            if (mWorkspace.addToExistingFolderIfNecessary(view, layout, cellXY, 0, dragObject,
                    true)) {
                return;
            }
        } else if (touchXY != null) {
            // when dragging and dropping, just find the closest free spot
            int[] result = layout.findNearestVacantArea(touchXY[0], touchXY[1], 1, 1, cellXY);
            foundCellSpan = (result != null);
        } else {
            foundCellSpan = layout.findCellForSpan(cellXY, 1, 1);
        }

        if (!foundCellSpan) {
            showOutOfSpaceMessage(isHotseatLayout(layout));
            return;
        }

        LauncherModel.addItemToDatabase(this, info, container, screenId, cellXY[0], cellXY[1]);

        if (!mRestoring) {
            mWorkspace.addInScreen(view, container, screenId, cellXY[0], cellXY[1], 1, 1,
                    isWorkspaceLocked());
        }
    }

    /**
     * Add a widget to the workspace.
     *
     * @param appWidgetId The app widget id
     */
    @Thunk void completeAddAppWidget(int appWidgetId, long container, long screenId,
            AppWidgetHostView hostView, LauncherAppWidgetProviderInfo appWidgetInfo) {

        ItemInfo info = mPendingAddInfo;
        if (appWidgetInfo == null) {
            appWidgetInfo = LauncherAppWidgetProviderInfo.fromProviderInfo(this,
                    mAppWidgetManager.getAppWidgetInfo(appWidgetId));
        }

        if (appWidgetInfo.isCustomWidget) {
            appWidgetId = LauncherAppWidgetInfo.CUSTOM_WIDGET_ID;
        }

        LauncherAppWidgetInfo launcherInfo;
        launcherInfo = new LauncherAppWidgetInfo(appWidgetId, appWidgetInfo.provider);
        launcherInfo.spanX = info.spanX;
        launcherInfo.spanY = info.spanY;
        launcherInfo.minSpanX = info.minSpanX;
        launcherInfo.minSpanY = info.minSpanY;
        launcherInfo.user = mAppWidgetManager.getUser(appWidgetInfo);

        LauncherModel.addItemToDatabase(this, launcherInfo,
                container, screenId, info.cellX, info.cellY);

        if (!mRestoring) {
            if (hostView == null) {
                // Perform actual inflation because we're live
                launcherInfo.hostView = mAppWidgetHost.createView(this, appWidgetId,
                        appWidgetInfo);
            } else {
                // The AppWidgetHostView has already been inflated and instantiated
                launcherInfo.hostView = hostView;
            }
            launcherInfo.hostView.setTag(launcherInfo);
            launcherInfo.hostView.setVisibility(View.VISIBLE);
            launcherInfo.notifyWidgetSizeChanged(this);

            mWorkspace.addInScreen(launcherInfo.hostView, container, screenId, info.cellX,
                    info.cellY, launcherInfo.spanX, launcherInfo.spanY, isWorkspaceLocked());

            addWidgetToAutoAdvanceIfNeeded(launcherInfo.hostView, appWidgetInfo);
        }
        resetAddInfo();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mUserPresent = false;
                mDragLayer.clearAllResizeFrames();
                updateAutoAdvanceState();

                // Reset AllApps to its initial state only if we are not in the middle of
                // processing a multi-step drop
                if (mAppsView != null && mWidgetsView != null &&
                        mPendingAddInfo.container == ItemInfo.NO_ID) {
                    showWorkspace(false);
                }
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                mUserPresent = true;
                updateAutoAdvanceState();
            } else if (ENABLE_DEBUG_INTENTS && DebugIntents.DELETE_DATABASE.equals(action)) {
                mModel.resetLoadedState(false, true);
                mModel.startLoader(PagedView.INVALID_RESTORE_PAGE,
                        LauncherModel.LOADER_FLAG_CLEAR_WORKSPACE);
            } else if (ENABLE_DEBUG_INTENTS && DebugIntents.MIGRATE_DATABASE.equals(action)) {
                mModel.resetLoadedState(false, true);
                mModel.startLoader(PagedView.INVALID_RESTORE_PAGE,
                        LauncherModel.LOADER_FLAG_CLEAR_WORKSPACE
                                | LauncherModel.LOADER_FLAG_MIGRATE_SHORTCUTS);
            }
        }
    };

    /**
     * Initializes the device profile based off of the launcher app state and screen orientation
     * @param app The launcher app state
     */
    public void initializeDeviceProfile(LauncherAppState app) {
        // Load configuration-specific DeviceProfile
        mDeviceProfile = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE ?
                app.getInvariantDeviceProfile().landscapeProfile
                : app.getInvariantDeviceProfile().portraitProfile;

        mHideIconLabels = SettingsProvider.getBoolean(this,
                SettingsProvider.SETTINGS_UI_HOMESCREEN_HIDE_ICON_LABELS,
                R.bool.preferences_interface_homescreen_hide_icon_labels_default);
        mDefaultScreenId = SettingsProvider.getLongCustomDefault(this,
                SettingsProvider.SETTINGS_UI_HOMESCREEN_DEFAULT_SCREEN_ID,
                R.integer.preferences_interface_homescreen_id_default);

        mModel = app.setLauncher(this);
        mIconCache = app.getIconCache();
        mIconCache.flush();
    }

    /**
     * Re-initializes the device profile and layout and reloads the workspace and app drawer as
     * appropriate
     * @param resizeGrid Indicates whether the grid should be resized
     * @param reloadAppDrawer Indicates whether the app drawer should be reloaded
     */
    public void reloadLauncher(boolean resizeGrid, boolean reloadAppDrawer) {
        reloadLauncher(mWorkspace.getCurrentPage(), resizeGrid, reloadAppDrawer);
    }

    /**
     * Re-initializes the device profile and layout and reloads the workspace and app drawer as
     * appropriate
     * @param page The page to bind to
     * @param resizeGrid Indicates whether the grid should be resized
     * @param reloadAppDrawer Indicates whether the app drawer should be reloaded
     */
    private void reloadLauncher(int page, boolean resizeGrid, boolean reloadAppDrawer)
    {
        // Re-initialize device profile
        LauncherAppState app = LauncherAppState.getInstance();
        app.initInvariantDeviceProfile();
        initializeDeviceProfile(app);

        mDeviceProfile.layout(this);

        // Reload
        mModel.resetLoadedState(true, true);
        int flag = resizeGrid ? LauncherModel.LOADER_FLAG_RESIZE_GRID :
                LauncherModel.LOADER_FLAG_NONE;
        mModel.startLoader(page, flag);

        mWorkspace.updateCustomContentVisibility();

        mSearchDropTargetBar.setupQsb(this);

        if (reloadAppDrawer) {
            reloadAppDrawer();
        }
    }

    public void reloadAppDrawer() {
        List<AppInfo> addedApps = mAppsView.getApps();
        mDragLayer.removeView(mAppsView);
        mAppsView = (AllAppsContainerView)LayoutInflater
                .from(this).inflate(R.layout.all_apps, mDragLayer, false);
        mDragLayer.addView(mAppsView, mDragLayer.getChildCount() - 1);
        mAppsView.setVisibility(View.INVISIBLE);
        setupSearchBar(this);
        mAppsView.addApps(addedApps);
        tryAndUpdatePredictedApps();
        mAppsView.onReloadAppDrawer();
        sRemoteFolderManager.onReloadAppDrawer();
    }

    public void reloadWidgetView() {
        WidgetsModel model = mWidgetsView.getWidgets();
        mDragLayer.removeView(mWidgetsView);
        mWidgetsView = (WidgetsContainerView)LayoutInflater
                .from(this).inflate(R.layout.widgets_view, mDragLayer, false);
        mDragLayer.addView(mWidgetsView, mDragLayer.getChildCount() - 2);
        mWidgetsView.setVisibility(View.INVISIBLE);
        if (model != null) {
            mWidgetsView.addWidgets(model);
        }
        mWidgetsView.reset();
    }

    /**
     * Replaces currently added fragments in the launcher layout with a
     * {@link DynamicGridSizeFragment}.
     */
    public void onClickDynamicGridSizeButton() {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        lockScreenOrientation();
        mDynamicGridSizeFragment = new DynamicGridSizeFragment();
        fragmentTransaction.replace(R.id.launcher, mDynamicGridSizeFragment,
                DynamicGridSizeFragment.DYNAMIC_GRID_SIZE_FRAGMENT);
        fragmentTransaction.commit();
    }

    /**
     * If the new grid size is different from the current grid size, the launcher will be reloaded
     * and the overview settings panel updated with the new grid size value.
     * @param size The new grid size to set the workspace to.
     */
    public void setDynamicGridSize(InvariantDeviceProfile.GridSize size) {
        int gridSize = SettingsProvider.getIntCustomDefault(this,
                SettingsProvider.SETTINGS_UI_DYNAMIC_GRID_SIZE, 0);
        boolean customValuesChanged = false;
        if (gridSize == size.getValue() && size == InvariantDeviceProfile.GridSize.Custom) {
            int tempRows = SettingsProvider.getIntCustomDefault(this,
                    SettingsProvider.SETTINGS_UI_HOMESCREEN_ROWS, mDeviceProfile.inv.numRows);
            int tempColumns = SettingsProvider.getIntCustomDefault(this,
                    SettingsProvider.SETTINGS_UI_HOMESCREEN_COLUMNS, mDeviceProfile.inv.numColumns);
            if (tempColumns != mDeviceProfile.inv.numColumns ||
                    tempRows != mDeviceProfile.inv.numRows) {
                customValuesChanged = true;
            }
        }

        if (gridSize != size.getValue() || customValuesChanged) {
            SettingsProvider.putInt(this,
                    SettingsProvider.SETTINGS_UI_DYNAMIC_GRID_SIZE, size.getValue());
            reloadLauncher(false, true);
        }

        // Must be called after reload and before settings invalidation.
        sRemoteFolderManager.onGridSizeChanged();

        mOverviewSettingsPanel.notifyDataSetInvalidated();

        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        Configuration config = getResources().getConfiguration();
        if(config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            fragmentTransaction
                    .setCustomAnimations(0, R.anim.exit_out_left);
        } else {
            fragmentTransaction
                    .setCustomAnimations(0, R.anim.exit_out_right);
        }
        fragmentTransaction
                .remove(mDynamicGridSizeFragment).commit();
        unlockScreenOrientation(true);
        mDarkPanel.setVisibility(View.VISIBLE);
        ObjectAnimator anim = ObjectAnimator.ofFloat(
                mDarkPanel, "alpha", 0.3f, 0.0f);
        anim.start();
        anim.addListener(mAnimatorListener);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Listen for broadcasts related to user-presence
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        // For handling managed profiles
        if (ENABLE_DEBUG_INTENTS) {
            filter.addAction(DebugIntents.DELETE_DATABASE);
            filter.addAction(DebugIntents.MIGRATE_DATABASE);
        }
        registerReceiver(mReceiver, filter);
        FirstFrameAnimatorHelper.initializeDrawListener(getWindow().getDecorView());
        setupTransparentSystemBarsForLollipop();
        mAttached = true;
        mVisible = true;
    }

    /**
     * Sets up transparent navigation and status bars in Lollipop.
     * This method is a no-op for other platform versions.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setupTransparentSystemBarsForLollipop() {
        if (Utilities.ATLEAST_LOLLIPOP) {
            Window window = getWindow();
            window.getAttributes().systemUiVisibility |=
                    (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;

        if (mAttached) {
            unregisterReceiver(mReceiver);
            mAttached = false;
        }
        updateAutoAdvanceState();
    }

    public void onWindowVisibilityChanged(int visibility) {
        mVisible = visibility == View.VISIBLE;
        updateAutoAdvanceState();
        // The following code used to be in onResume, but it turns out onResume is called when
        // you're in All Apps and click home to go to the workspace. onWindowVisibilityChanged
        // is a more appropriate event to handle
        if (mVisible) {
            if (!mWorkspaceLoading) {
                final ViewTreeObserver observer = mWorkspace.getViewTreeObserver();
                // We want to let Launcher draw itself at least once before we force it to build
                // layers on all the workspace pages, so that transitioning to Launcher from other
                // apps is nice and speedy.
                observer.addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
                    private boolean mStarted = false;
                    public void onDraw() {
                        if (mStarted) return;
                        mStarted = true;
                        // We delay the layer building a bit in order to give
                        // other message processing a time to run.  In particular
                        // this avoids a delay in hiding the IME if it was
                        // currently shown, because doing that may involve
                        // some communication back with the app.
                        mWorkspace.postDelayed(mBuildLayersRunnable, 500);
                        final ViewTreeObserver.OnDrawListener listener = this;
                        mWorkspace.post(new Runnable() {
                                public void run() {
                                    if (mWorkspace != null &&
                                            mWorkspace.getViewTreeObserver() != null) {
                                        mWorkspace.getViewTreeObserver().
                                                removeOnDrawListener(listener);
                                    }
                                }
                            });
                        return;
                    }
                });
            }
            clearTypedText();
        }
    }

    @Thunk void sendAdvanceMessage(long delay) {
        mHandler.removeMessages(ADVANCE_MSG);
        Message msg = mHandler.obtainMessage(ADVANCE_MSG);
        mHandler.sendMessageDelayed(msg, delay);
        mAutoAdvanceSentTime = System.currentTimeMillis();
    }

    @Thunk void updateAutoAdvanceState() {
        boolean autoAdvanceRunning = mVisible && mUserPresent && !mWidgetsToAdvance.isEmpty();
        if (autoAdvanceRunning != mAutoAdvanceRunning) {
            mAutoAdvanceRunning = autoAdvanceRunning;
            if (autoAdvanceRunning) {
                long delay = mAutoAdvanceTimeLeft == -1 ? mAdvanceInterval : mAutoAdvanceTimeLeft;
                sendAdvanceMessage(delay);
            } else {
                if (!mWidgetsToAdvance.isEmpty()) {
                    mAutoAdvanceTimeLeft = Math.max(0, mAdvanceInterval -
                            (System.currentTimeMillis() - mAutoAdvanceSentTime));
                }
                mHandler.removeMessages(ADVANCE_MSG);
                mHandler.removeMessages(0); // Remove messages sent using postDelayed()
            }
        }
    }

    @Thunk final Handler mHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == ADVANCE_MSG) {
                int i = 0;
                for (View key: mWidgetsToAdvance.keySet()) {
                    final View v = key.findViewById(mWidgetsToAdvance.get(key).autoAdvanceViewId);
                    final int delay = mAdvanceStagger * i;
                    if (v instanceof Advanceable) {
                        mHandler.postDelayed(new Runnable() {
                           public void run() {
                               ((Advanceable) v).advance();
                           }
                       }, delay);
                    }
                    i++;
                }
                sendAdvanceMessage(mAdvanceInterval);
            }
            return true;
        }
    });

    void addWidgetToAutoAdvanceIfNeeded(View hostView, AppWidgetProviderInfo appWidgetInfo) {
        if (appWidgetInfo == null || appWidgetInfo.autoAdvanceViewId == -1) return;
        View v = hostView.findViewById(appWidgetInfo.autoAdvanceViewId);
        if (v instanceof Advanceable) {
            mWidgetsToAdvance.put(hostView, appWidgetInfo);
            ((Advanceable) v).fyiWillBeAdvancedByHostKThx();
            updateAutoAdvanceState();
        }
    }

    void removeWidgetToAutoAdvance(View hostView) {
        if (mWidgetsToAdvance.containsKey(hostView)) {
            mWidgetsToAdvance.remove(hostView);
            updateAutoAdvanceState();
        }
    }

    public void removeAppWidget(LauncherAppWidgetInfo launcherInfo) {
        removeWidgetToAutoAdvance(launcherInfo.hostView);
        launcherInfo.hostView = null;
        AppWidgetProviderInfo info = mAppWidgetManager.getAppWidgetInfo(launcherInfo.appWidgetId);
        String packageName = info.providerInfo.packageName;
        LauncherApplication.getLauncherStats().sendWidgetRemoveEvent(packageName);
    }

    public void showOutOfSpaceMessage(boolean isHotseatLayout) {
        int strId = (isHotseatLayout ? R.string.hotseat_out_of_space : R.string.out_of_space);
        Toast.makeText(this, getString(strId), Toast.LENGTH_SHORT).show();
    }

    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    public AllAppsContainerView getAppsView() {
        return mAppsView;
    }

    public WidgetsContainerView getWidgetsView() {
        return mWidgetsView;
    }

    public Workspace getWorkspace() {
        return mWorkspace;
    }

    public RemoteFolderManager getRemoteFolderManager() {
        return sRemoteFolderManager;
    }

    public Hotseat getHotseat() {
        return mHotseat;
    }

    public View getPageIndicator() {
        return mPageIndicators;
    }

    public ViewGroup getOverviewPanel() {
        return mOverviewPanel;
    }

    public View getDarkPanel() {
        return mDarkPanel;
    }

    public SearchDropTargetBar getSearchDropTargetBar() {
        return mSearchDropTargetBar;
    }

    public LauncherAppWidgetHost getAppWidgetHost() {
        return mAppWidgetHost;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    protected SharedPreferences getSharedPrefs() {
        return mSharedPrefs;
    }

    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    public void closeSystemDialogs() {
        getWindow().closeAllPanels();

        // Whatever we were doing is hereby canceled.
        setWaitingForResult(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        long startTime = 0;
        if (DEBUG_RESUME_TIME) {
            startTime = System.currentTimeMillis();
        }
        super.onNewIntent(intent);

        // Close the menu
        Folder openFolder = mWorkspace.getOpenFolder();
        boolean alreadyOnHome = mHasFocus && ((intent.getFlags() &
                Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        boolean isActionMain = Intent.ACTION_MAIN.equals(intent.getAction());
        if (isActionMain) {
            // also will cancel mWaitingForResult.
            closeSystemDialogs();

            if (mWorkspace == null) {
                // Can be cases where mWorkspace is null, this prevents a NPE
                return;
            }
            // In all these cases, only animate if we're already on home
            mWorkspace.exitWidgetResizeMode();

            closeFolder();
            exitSpringLoadedDragMode();

            // If we are already on home, then just animate back to the workspace,
            // otherwise, just wait until onResume to set the state back to Workspace
            if (alreadyOnHome) {
                showWorkspace(true);
            } else {
                mOnResumeState = State.WORKSPACE;
            }

            final View v = getWindow().peekDecorView();
            if (v != null && v.getWindowToken() != null) {
                InputMethodManager imm = (InputMethodManager)getSystemService(
                        INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }

            // Reset the apps view
            if (!alreadyOnHome && mAppsView != null) {
                mAppsView.scrollToTop();
            }

            // Reset the widgets view
            if (!alreadyOnHome && mWidgetsView != null) {
                mWidgetsView.scrollToTop();
            }

            if (mLauncherCallbacks != null) {
                mLauncherCallbacks.onHomeIntent();
            }
        }

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onNewIntent(intent);
        }

        // Defer moving to the default screen until after we callback to the LauncherCallbacks
        // as slow logic in the callbacks eat into the time the scroller expects for the snapToPage
        // animation.
        if (isActionMain) {
            boolean moveToDefaultScreen = mLauncherCallbacks != null ?
                    mLauncherCallbacks.shouldMoveToDefaultScreenOnHomeIntent() : true;
            if (alreadyOnHome && mState == State.WORKSPACE && !mWorkspace.isTouchActive() &&
                    openFolder == null && moveToDefaultScreen) {
                mWorkspace.post(new Runnable() {
                    @Override
                    public void run() {
                        mWorkspace.moveToDefaultScreen(true);
                    }
                });
            }
        }

        if (DEBUG_RESUME_TIME) {
            Log.d(TAG, "Time spent in onNewIntent: " + (System.currentTimeMillis() - startTime));
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        for (int page: mSynchronouslyBoundPages) {
            mWorkspace.restoreInstanceStateForChild(page);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mWorkspace.getChildCount() > 0) {
            outState.putInt(RUNTIME_STATE_CURRENT_SCREEN,
                    mWorkspace.getCurrentPageOffsetFromCustomContent());
        }
        super.onSaveInstanceState(outState);

        outState.putInt(RUNTIME_STATE, mState.ordinal());
        // We close any open folder since it will not be re-opened, and we need to make sure
        // this state is reflected.
        if (mHiddenFolderIcon == null) {
            closeFolder();
        }

        if (mPendingAddInfo.container != ItemInfo.NO_ID && mPendingAddInfo.screenId > -1 &&
                mWaitingForResult) {
            outState.putLong(RUNTIME_STATE_PENDING_ADD_CONTAINER, mPendingAddInfo.container);
            outState.putLong(RUNTIME_STATE_PENDING_ADD_SCREEN, mPendingAddInfo.screenId);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_X, mPendingAddInfo.cellX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_Y, mPendingAddInfo.cellY);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_X, mPendingAddInfo.spanX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y, mPendingAddInfo.spanY);
            outState.putParcelable(RUNTIME_STATE_PENDING_ADD_WIDGET_INFO, mPendingAddWidgetInfo);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_WIDGET_ID, mPendingAddWidgetId);
        }

        // Save the current widgets tray?
        // TODO(hyunyoungs)
        outState.putSerializable(RUNTIME_STATE_VIEW_IDS, mItemIdToViewId);

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Remove all pending runnables
        mHandler.removeMessages(ADVANCE_MSG);
        mHandler.removeMessages(0);
        mWorkspace.removeCallbacks(mBuildLayersRunnable);

        // Stop callbacks from LauncherModel
        LauncherAppState app = (LauncherAppState.getInstance());

        // It's possible to receive onDestroy after a new Launcher activity has
        // been created. In this case, don't interfere with the new Launcher.
        if (mModel.isCurrentCallbacks(this)) {
            mModel.stopLoader();
            app.setLauncher(null);
        }

        try {
            mAppWidgetHost.stopListening();
        } catch (NullPointerException ex) {
            Log.w(TAG, "problem while stopping AppWidgetHost during Launcher destruction", ex);
        }
        mAppWidgetHost = null;

        mWidgetsToAdvance.clear();

        TextKeyListener.getInstance().release();

        unregisterReceiver(mCloseSystemDialogsReceiver);
        unregisterReceiver(mConnectionReceiver);
        unregisterReceiver(mDeviceUnlockedReceiver);

        mDragLayer.clearAllResizeFrames();
        ((ViewGroup) mWorkspace.getParent()).removeAllViews();
        mWorkspace.removeAllWorkspaceScreens();
        mWorkspace = null;
        mDragController = null;

        LauncherAnimUtils.onDestroyActivity();

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onDestroy();
        }

        unregisterReceiver(protectedAppsChangedReceiver);
        unregisterReceiver(searchBarVisibilityChangedReceiver);
    }

    public DragController getDragController() {
        return mDragController;
    }

    public void validateLockForHiddenFolders(FolderIcon info, int action) {
        mHiddenFolderIcon = info;
        // Validate Lock Pattern
        Intent lockPatternActivity = new Intent();
        lockPatternActivity.setClassName(
                "com.android.settings",
                "com.android.settings.applications.LockPatternActivity");
        startActivityForResult(lockPatternActivity, action);
        mHiddenFolderAuth = false;
    }

    public void notifyFolderNameChanged() {
        // Reload
        mModel.resetLoadedState(true, true);
        mModel.startLoader(mWorkspace.getCurrentPage(), LauncherModel.LOADER_FLAG_NONE);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        onStartForResult(requestCode);
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void startIntentSenderForResult (IntentSender intent, int requestCode,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) {
        onStartForResult(requestCode);
        try {
            super.startIntentSenderForResult(intent, requestCode,
                fillInIntent, flagsMask, flagsValues, extraFlags, options);
        } catch (IntentSender.SendIntentException e) {
            throw new ActivityNotFoundException();
        }
    }

    private void onStartForResult(int requestCode) {
        if (requestCode >= 0) {
            setWaitingForResult(true);
        }
    }

    /**
     * Indicates that we want global search for this activity by setting the globalSearch
     * argument for {@link #startSearch} to true.
     */
    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {

        if (initialQuery == null) {
            // Use any text typed in the launcher as the initial query
            initialQuery = getTypedText();
        }
        if (appSearchData == null) {
            appSearchData = new Bundle();
            appSearchData.putString("source", "launcher-search");
        }
        Rect sourceBounds = new Rect();
        if (mSearchDropTargetBar != null) {
            sourceBounds = mSearchDropTargetBar.getSearchBarBounds();
        }

        boolean clearTextImmediately = startSearch(initialQuery, selectInitialQuery,
                appSearchData, sourceBounds);
        if (clearTextImmediately) {
            clearTypedText();
        }

        // We need to show the workspace after starting the search
        showWorkspace(true);
    }

    /**
     * Start a text search.
     *
     * @return {@code true} if the search will start immediately, so any further keypresses
     * will be handled directly by the search UI. {@code false} if {@link Launcher} should continue
     * to buffer keypresses.
     */
    public boolean startSearch(String initialQuery,
            boolean selectInitialQuery, Bundle appSearchData, Rect sourceBounds) {
        if (mLauncherCallbacks != null && mLauncherCallbacks.providesSearch()) {
            return mLauncherCallbacks.startSearch(initialQuery, selectInitialQuery, appSearchData,
                    sourceBounds);
        }

        startGlobalSearch(initialQuery, selectInitialQuery,
                appSearchData, sourceBounds);
        return false;
    }

    /**
     * Starts the global search activity. This code is a copied from SearchManager
     */
    private void startGlobalSearch(String initialQuery,
            boolean selectInitialQuery, Bundle appSearchData, Rect sourceBounds) {
        final SearchManager searchManager =
            (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        ComponentName globalSearchActivity = searchManager.getGlobalSearchActivity();
        if (globalSearchActivity == null) {
            Log.w(TAG, "No global search activity found.");
            return;
        }
        Intent intent = new Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(globalSearchActivity);
        // Make sure that we have a Bundle to put source in
        if (appSearchData == null) {
            appSearchData = new Bundle();
        } else {
            appSearchData = new Bundle(appSearchData);
        }
        // Set source to package name of app that starts global search if not set already.
        if (!appSearchData.containsKey("source")) {
            appSearchData.putString("source", getPackageName());
        }
        intent.putExtra(SearchManager.APP_DATA, appSearchData);
        if (!TextUtils.isEmpty(initialQuery)) {
            intent.putExtra(SearchManager.QUERY, initialQuery);
        }
        if (selectInitialQuery) {
            intent.putExtra(SearchManager.EXTRA_SELECT_QUERY, selectInitialQuery);
        }
        intent.setSourceBounds(sourceBounds);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Global search activity not found: " + globalSearchActivity);
        }
    }

    public void startSearchFromAllApps(View v, Intent searchIntent, String searchQuery) {
        if (mLauncherCallbacks != null && mLauncherCallbacks.startSearchFromAllApps(searchQuery)) {
            return;
        }

        // If not handled, then just start the provided search intent
        startActivitySafely(v, searchIntent, null);
    }

    public boolean isOnCustomContent() {
        return mWorkspace.isOnOrMovingToCustomContent();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (!isOnCustomContent()) {
            // Close any open folders
            closeFolder(false);
            // Stop resizing any widgets
            mWorkspace.exitWidgetResizeMode();
            if (!mWorkspace.isInOverviewMode()) {
                // Show the overview mode
                showOverviewMode(true);
            } else {
                showWorkspace(true);
            }
        }
        if (mLauncherCallbacks != null) {
            return mLauncherCallbacks.onPrepareOptionsMenu(menu);
        }

        return false;
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, true);
        // Use a custom animation for launching search
        return true;
    }

    public boolean isWorkspaceLocked() {
        return mWorkspaceLoading || mWaitingForResult;
    }

    public boolean isWorkspaceLoading() {
        return mWorkspaceLoading;
    }

    private void setWorkspaceLoading(boolean value) {
        boolean isLocked = isWorkspaceLocked();
        mWorkspaceLoading = value;
        if (isLocked != isWorkspaceLocked()) {
            onWorkspaceLockedChanged();
        }
    }

    private void setWaitingForResult(boolean value) {
        boolean isLocked = isWorkspaceLocked();
        mWaitingForResult = value;
        if (isLocked != isWorkspaceLocked()) {
            onWorkspaceLockedChanged();
        }
    }

    protected void onWorkspaceLockedChanged() {
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onWorkspaceLockedChanged();
        }
    }

    private void resetAddInfo() {
        mPendingAddInfo.container = ItemInfo.NO_ID;
        mPendingAddInfo.screenId = -1;
        mPendingAddInfo.cellX = mPendingAddInfo.cellY = -1;
        mPendingAddInfo.spanX = mPendingAddInfo.spanY = -1;
        mPendingAddInfo.minSpanX = mPendingAddInfo.minSpanY = 1;
        mPendingAddInfo.dropPos = null;
    }

    void addAppWidgetImpl(final int appWidgetId, final ItemInfo info, final
            AppWidgetHostView boundWidget, final LauncherAppWidgetProviderInfo appWidgetInfo) {
        addAppWidgetImpl(appWidgetId, info, boundWidget, appWidgetInfo, 0);
    }

    void addAppWidgetImpl(final int appWidgetId, final ItemInfo info,
            final AppWidgetHostView boundWidget, final LauncherAppWidgetProviderInfo appWidgetInfo,
            int delay) {
        if (appWidgetInfo.configure != null) {
            mPendingAddWidgetInfo = appWidgetInfo;
            mPendingAddWidgetId = appWidgetId;

            // Launch over to configure widget, if needed
            mAppWidgetManager.startConfigActivity(appWidgetInfo, appWidgetId, this,
                    mAppWidgetHost, REQUEST_CREATE_APPWIDGET);

        } else {
            // Otherwise just add it
            Runnable onComplete = new Runnable() {
                @Override
                public void run() {
                    // Exit spring loaded mode if necessary after adding the widget
                    exitSpringLoadedDragModeDelayed(true, EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT,
                            null);
                }
            };
            completeAddAppWidget(appWidgetId, info.container, info.screenId, boundWidget,
                    appWidgetInfo);
            mWorkspace.removeExtraEmptyScreenDelayed(true, onComplete, delay, false);
            String packageName = appWidgetInfo.providerInfo.packageName;
            LauncherApplication.getLauncherStats().sendWidgetAddEvent(packageName);
        }
    }

    protected void moveToCustomContentScreen(boolean animate) {
        // Close any folders that may be open.
        closeFolder();
        mWorkspace.moveToCustomContentScreen(animate);
    }

    public void addPendingItem(PendingAddItemInfo info, long container, long screenId,
            int[] cell, int spanX, int spanY) {
        switch (info.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                int span[] = new int[2];
                span[0] = spanX;
                span[1] = spanY;
                addAppWidgetFromDrop((PendingAddWidgetInfo) info,
                        container, screenId, cell, span);
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                processShortcutFromDrop(info.componentName, container, screenId, cell);
                break;
            default:
                throw new IllegalStateException("Unknown item type: " + info.itemType);
            }
    }

    /**
     * Process a shortcut drop.
     *
     * @param componentName The name of the component
     * @param screenId The ID of the screen where it should be added
     * @param cell The cell it should be added to, optional
     */
    private void processShortcutFromDrop(ComponentName componentName, long container, long screenId,
            int[] cell) {
        resetAddInfo();
        mPendingAddInfo.container = container;
        mPendingAddInfo.screenId = screenId;
        mPendingAddInfo.dropPos = null;

        if (cell != null) {
            mPendingAddInfo.cellX = cell[0];
            mPendingAddInfo.cellY = cell[1];
        }

        Intent createShortcutIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        createShortcutIntent.setComponent(componentName);
        Utilities.startActivityForResultSafely(this, createShortcutIntent, REQUEST_CREATE_SHORTCUT);
    }

    /**
     * Process a widget drop.
     *
     * @param info The PendingAppWidgetInfo of the widget being added.
     * @param screenId The ID of the screen where it should be added
     * @param cell The cell it should be added to, optional
     */
    private void addAppWidgetFromDrop(PendingAddWidgetInfo info, long container, long screenId,
            int[] cell, int[] span) {
        resetAddInfo();
        mPendingAddInfo.container = info.container = container;
        mPendingAddInfo.screenId = info.screenId = screenId;
        mPendingAddInfo.dropPos = null;
        mPendingAddInfo.minSpanX = info.minSpanX;
        mPendingAddInfo.minSpanY = info.minSpanY;

        if (cell != null) {
            mPendingAddInfo.cellX = cell[0];
            mPendingAddInfo.cellY = cell[1];
        }
        if (span != null) {
            mPendingAddInfo.spanX = span[0];
            mPendingAddInfo.spanY = span[1];
        }

        AppWidgetHostView hostView = info.boundWidget;
        int appWidgetId;
        if (hostView != null) {
            appWidgetId = hostView.getAppWidgetId();
            addAppWidgetImpl(appWidgetId, info, hostView, info.info);

            // Clear the boundWidget so that it doesn't get destroyed.
            info.boundWidget = null;
        } else {
            // In this case, we either need to start an activity to get permission to bind
            // the widget, or we need to start an activity to configure the widget, or both.
            appWidgetId = getAppWidgetHost().allocateAppWidgetId();
            Bundle options = info.bindOptions;

            boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
                    appWidgetId, info.info, options);
            if (success) {
                addAppWidgetImpl(appWidgetId, info, null, info.info);
            } else {
                mPendingAddWidgetInfo = info.info;
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.componentName);
                mAppWidgetManager.getUser(mPendingAddWidgetInfo)
                    .addToIntent(intent, AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE);
                // TODO: we need to make sure that this accounts for the options bundle.
                // intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
                startActivityForResult(intent, REQUEST_BIND_APPWIDGET);
            }
        }
    }

    FolderIcon addFolder(CellLayout layout, long container, final long screenId, int cellX,
                         int cellY) {
        FolderInfo folderInfo = new FolderInfo();
        folderInfo.title = getText(R.string.folder_name);
        return addFolder(layout, container, screenId, cellX, cellY, folderInfo);
    }

    FolderIcon addFolder(CellLayout layout, long container, final long screenId, int cellX,
            int cellY, FolderInfo folderInfo) {
        // Update the model
        LauncherModel.addItemToDatabase(Launcher.this, folderInfo, container, screenId,
                cellX, cellY);
        sFolders.put(folderInfo.id, folderInfo);

        // Create the view
        FolderIcon newFolder =
            FolderIcon.fromXml(R.layout.folder_icon, this, layout, folderInfo, mIconCache);
        if (container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            newFolder.setTextVisible(!mHideIconLabels);
        }
        mWorkspace.addInScreen(newFolder, container, screenId, cellX, cellY, 1, 1,
                isWorkspaceLocked());
        // Force measure the new folder icon
        CellLayout parent = mWorkspace.getParentCellLayoutForView(newFolder);
        parent.getShortcutsAndWidgets().measureChild(newFolder);
        return newFolder;
    }

    void removeFolder(FolderInfo folder) {
        sFolders.remove(folder.id);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    if (Utilities.isPropertyEnabled(DUMP_STATE_PROPERTY)) {
                        dumpState();
                        return true;
                    }
                    break;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (mLauncherCallbacks != null && mLauncherCallbacks.handleBackPressed()) {
            return;
        }

        if (mDragController.isDragging()) {
            mDragController.cancelDrag();
            return;
        }

        if (isAppsViewVisible()) {
            showWorkspace(true);
        } else if (isWidgetsViewVisible())  {
            showOverviewMode(true);
        } else if (mWorkspace.isInOverviewMode()) {
            Fragment gridFragment = getFragmentManager().findFragmentByTag(
                    DynamicGridSizeFragment.DYNAMIC_GRID_SIZE_FRAGMENT);
            if (gridFragment != null) {
                mDynamicGridSizeFragment.setSize();
                unlockScreenOrientation(true);
            }
            else {
                showWorkspace(true);
                // Background was set to gradient in onPause(), restore to black if in all apps.
                setWorkspaceBackground(mState == State.WORKSPACE ? WORKSPACE_BACKGROUND_GRADIENT
                        : WORKSPACE_BACKGROUND_TRANSPARENT);
            }
        } else if (mWorkspace.getOpenFolder() != null) {
            Folder openFolder = mWorkspace.getOpenFolder();
            if (openFolder.isEditingName()) {
                openFolder.dismissEditingName();
            } else {
                closeFolder();
            }
        } else {
            mWorkspace.exitWidgetResizeMode();

            // Back button is a no-op here, but give at least some feedback for the button press
            mWorkspace.showOutlinesTemporarily();
        }
    }


    /**
     * Re-listen when widget host is reset.
     */
    @Override
    public void onAppWidgetHostReset() {
        if (mAppWidgetHost != null) {
            mAppWidgetHost.startListening();
        }
    }

    /**
     * Launches the intent referred by the clicked shortcut.
     *
     * @param v The view representing the clicked shortcut.
     */
    public void onClick(View v) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
        if (v.getWindowToken() == null) {
            return;
        }

        if (!mWorkspace.isFinishedSwitchingState()) {
            return;
        }

        if (v instanceof Workspace) {
            if (mWorkspace.isInOverviewMode()) {
                showWorkspace(true);
            }
            return;
        }

        if (v instanceof CellLayout) {
            if (mWorkspace.isInOverviewMode()) {
                showWorkspace(mWorkspace.indexOfChild(v), true);
            }
        }

        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            onClickAppShortcut(v);
        } else if (tag instanceof FolderInfo) {
            if (v instanceof FolderIcon) {
                onClickFolderIcon(v);
            }
        } else if (v == mAllAppsButton) {
            onClickAllAppsButton(v);
        } else if (tag instanceof AppInfo) {
            AppInfo info = (AppInfo) tag;
            startAppShortcutOrInfoActivity(v);
            LauncherApplication.getLauncherStats().sendAppLaunchEvent(
                    LauncherStats.ORIGIN_APPDRAWER, info.componentName.getPackageName());
            String packageName = info.getIntent().getComponent().getPackageName();
            if (LauncherStats.SETTINGS_PACKAGE_NAME.equals(packageName)) {
                LauncherApplication.getLauncherStats()
                        .sendSettingsOpenedEvent(LauncherStats.ORIGIN_APPDRAWER);
            }
        } else if (tag instanceof LauncherAppWidgetInfo) {
            if (v instanceof PendingAppWidgetHostView) {
                onClickPendingWidget((PendingAppWidgetHostView) v);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    /**
     * Event handler for the app widget view which has not fully restored.
     */
    public void onClickPendingWidget(final PendingAppWidgetHostView v) {
        if (mIsSafeModeEnabled) {
            Toast.makeText(this, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show();
            return;
        }

        final LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) v.getTag();
        if (v.isReadyForClickSetup()) {
            int widgetId = info.appWidgetId;
            AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(widgetId);
            if (appWidgetInfo != null) {
                mPendingAddWidgetInfo = LauncherAppWidgetProviderInfo.fromProviderInfo(
                        this, appWidgetInfo);
                mPendingAddInfo.copyFrom(info);
                mPendingAddWidgetId = widgetId;

                AppWidgetManagerCompat.getInstance(this).startConfigActivity(appWidgetInfo,
                        info.appWidgetId, this, mAppWidgetHost, REQUEST_RECONFIGURE_APPWIDGET);
            }
        } else if (info.installProgress < 0) {
            // The install has not been queued
            final String packageName = info.providerName.getPackageName();
            showBrokenAppInstallDialog(packageName,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        startActivitySafely(v, LauncherModel.getMarketIntent(packageName), info);
                    }
                });
        } else {
            // Download has started.
            final String packageName = info.providerName.getPackageName();
            startActivitySafely(v, LauncherModel.getMarketIntent(packageName), info);
        }
    }

    /**
     * Event handler for the "grid" button that appears on the home screen, which
     * enters all apps mode.
     *
     * @param v The view that was clicked.
     */
    protected void onClickAllAppsButton(View v) {
        if (LOGD) Log.d(TAG, "onClickAllAppsButton");
        if (!isAppsViewVisible()) {
            showAppsView(true /* animated */, false /* resetListToTop */,
                    true /* updatePredictedApps */, false /* focusSearchBar */);

            if (mLauncherCallbacks != null) {
                mLauncherCallbacks.onClickAllAppsButton(v);
            }
        }
    }

    protected void onLongClickAllAppsButton(View v) {
        if (LOGD) Log.d(TAG, "onLongClickAllAppsButton");
        if (!isAppsViewVisible()) {
            showAppsView(true /* animated */, false /* resetListToTop */,
                    true /* updatePredictedApps */, true /* focusSearchBar */);
        }
    }

    private void showBrokenAppInstallDialog(final String packageName,
            DialogInterface.OnClickListener onSearchClickListener) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.abandoned_promises_title)
            .setMessage(R.string.abandoned_promise_explanation)
            .setPositiveButton(R.string.abandoned_search, onSearchClickListener)
            .setNeutralButton(R.string.abandoned_clean_this,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        final UserHandleCompat user = UserHandleCompat.myUserHandle();
                        mWorkspace.removeAbandonedPromise(packageName, user);
                    }
                })
            .create().show();
        return;
    }

    /**
     * Event handler for an app shortcut click.
     *
     * @param v The view that was clicked. Must be a tagged with a {@link ShortcutInfo}.
     */
    protected void onClickAppShortcut(final View v) {
        if (LOGD) Log.d(TAG, "onClickAppShortcut");
        Object tag = v.getTag();
        if (!(tag instanceof ShortcutInfo)) {
            throw new IllegalArgumentException("Input must be a Shortcut");
        }

        // Open shortcut
        final ShortcutInfo shortcut = (ShortcutInfo) tag;

        if (shortcut.isDisabled != 0) {
            int error = R.string.activity_not_available;
            if ((shortcut.isDisabled & ShortcutInfo.FLAG_DISABLED_SAFEMODE) != 0) {
                error = R.string.safemode_shortcut_error;
            }
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            return;
        }

        final Intent intent = shortcut.intent;

        // Check for special shortcuts
        if (intent.getComponent() != null) {
            final String shortcutClass = intent.getComponent().getClassName();

            if (shortcutClass.equals(MemoryDumpActivity.class.getName())) {
                MemoryDumpActivity.startDump(this);
                return;
            } else if (shortcutClass.equals(ToggleWeightWatcher.class.getName())) {
                toggleShowWeightWatcher();
                return;
            }
        }

        // Check for abandoned promise
        if ((v instanceof BubbleTextView)
                && shortcut.isPromise()
                && !shortcut.hasStatusFlag(ShortcutInfo.FLAG_INSTALL_SESSION_ACTIVE)) {
            showBrokenAppInstallDialog(
                    shortcut.getTargetComponent().getPackageName(),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            startAppShortcutOrInfoActivity(v);
                        }
                    });
            return;
        }

        // Start activities
        startAppShortcutOrInfoActivity(v);
        ComponentName componentName = intent.getComponent();
        if (componentName != null) {
            String packageName = intent.getComponent().getPackageName();
            LauncherApplication.getLauncherStats()
                    .sendAppLaunchEvent(LauncherStats.ORIGIN_HOMESCREEN,
                            packageName);
            if (LauncherStats.SETTINGS_PACKAGE_NAME.equals(packageName)) {
                LauncherApplication.getLauncherStats().sendSettingsOpenedEvent(
                        LauncherStats.ORIGIN_HOMESCREEN);
            }
        }

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onClickAppShortcut(v);
        }
    }

    @Thunk void startAppShortcutOrInfoActivity(View v) {
        Object tag = v.getTag();
        final ShortcutInfo shortcut;
        final Intent intent;
        if (tag instanceof ShortcutInfo) {
            shortcut = (ShortcutInfo) tag;
            intent = shortcut.intent;
            int[] pos = new int[2];
            v.getLocationOnScreen(pos);
            intent.setSourceBounds(new Rect(pos[0], pos[1],
                    pos[0] + v.getWidth(), pos[1] + v.getHeight()));

        } else if (tag instanceof AppInfo) {
            shortcut = null;
            intent = ((AppInfo) tag).intent;
        } else {
            throw new IllegalArgumentException("Input must be a Shortcut or AppInfo");
        }

        boolean success = startActivitySafely(v, intent, tag);
        mStats.recordLaunch(v, intent, shortcut);

        if (success && v instanceof BubbleTextView) {
            mWaitingForResume = (BubbleTextView) v;
            mWaitingForResume.setStayPressed(true);
        }
    }

    /**
     * Event handler for a folder icon click.
     *
     * @param v The view that was clicked. Must be an instance of {@link FolderIcon}.
     */
    protected void onClickFolderIcon(View v) {
        if (LOGD) Log.d(TAG, "onClickFolder");
        if (!(v instanceof FolderIcon)){
            throw new IllegalArgumentException("Input must be a FolderIcon");
        }

        // TODO(sunnygoyal): Re-evaluate this code.
        FolderIcon folderIcon = (FolderIcon) v;
        final FolderInfo info = folderIcon.getFolderInfo();
        Folder openFolder = mWorkspace.getFolderForTag(info);

        int[] folderTouchXY = new int[2];
        v.getLocationOnScreen(folderTouchXY);
        int[] folderTouchXYOffset = {folderTouchXY[0] + v.getWidth() / 2,
                folderTouchXY[1] + v.getHeight() / 2};

        // If the folder info reports that the associated folder is open, then verify that
        // it is actually opened. There have been a few instances where this gets out of sync.
        if (info.opened && openFolder == null) {
            Log.d(TAG, "Folder info marked as open, but associated folder is not open. Screen: "
                    + info.screenId + " (" + info.cellX + ", " + info.cellY + ")");
            info.opened = false;
        }

        if (!info.opened && !folderIcon.getFolder().isDestroyed()) {
            // Close any open folder
            closeFolder();
            // Open the requested folder
            openFolder(folderIcon, folderTouchXYOffset);
        } else {
            // Find the open folder...
            int folderScreen;
            if (openFolder != null) {
                folderScreen = mWorkspace.getPageForView(openFolder);
                // .. and close it
                closeFolder(openFolder);
                if (folderScreen != mWorkspace.getCurrentPage()) {
                    // Close any folder open on the current screen
                    closeFolder();
                    // Pull the folder onto this screen
                    openFolder(folderIcon, folderTouchXYOffset);
                }
            }
        }

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onClickFolderIcon(v);
        }
    }

    /**
     * Event handler for the (Add) Widgets button that appears after a long press
     * on the home screen.
     */
    protected void onClickAddWidgetButton(View view) {
        if (LOGD) Log.d(TAG, "onClickAddWidgetButton");
        if (mIsSafeModeEnabled) {
            Toast.makeText(this, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show();
        } else {
            showWidgetsView(true /* animated */, true /* resetPageToZero */);
            if (mLauncherCallbacks != null) {
                mLauncherCallbacks.onClickAddWidgetButton(view);
            }
        }
    }

    /**
     * Event handler for the wallpaper picker button that appears after a long press
     * on the home screen.
     */
    protected void onClickWallpaperPicker(View v) {
        if (LOGD) Log.d(TAG, "onClickWallpaperPicker");
        startActivityForResult(new Intent(Intent.ACTION_SET_WALLPAPER).setPackage(getPackageName()),
                REQUEST_PICK_WALLPAPER);

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onClickWallpaperPicker(v);
        }
    }

    /**
     * Event handler for a click on the settings button that appears after a long press
     * on the home screen.
     */
    protected void onClickSettingsButton(View v) {
        if (LOGD) Log.d(TAG, "onClickSettingsButton");
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onClickSettingsButton(v);
        } else {
            if (mOverviewPanel.isExpanded()) {
                mOverviewPanel.collapsePane();
            } else {
                mOverviewPanel.expandPane();
            }
        }
    }

    protected void onClickDefaultScreenButton(View v) {
        if (LOGD) Log.d(TAG, "onClickDefaultScreenButton");

        if (!mWorkspace.isInOverviewMode()) return;

        mDefaultScreenId = mWorkspace.getScreenIdForPageIndex(mWorkspace.getPageNearestToCenterOfScreen());
        updateDefaultScreenButton();
        SettingsProvider.get(this).edit()
                .putLong(SettingsProvider.SETTINGS_UI_HOMESCREEN_DEFAULT_SCREEN_ID,
                        mDefaultScreenId)
                .commit();
    }

    protected void updateDefaultScreenButton() {
        if (mOverviewPanel != null) {
            View defaultPageButton = mOverviewPanel.findViewById(R.id.default_screen_button);
            defaultPageButton.setActivated(
                    mWorkspace.getScreenIdForPageIndex(mWorkspace.getPageNearestToCenterOfScreen())
                            == mDefaultScreenId);
        }
    }

    public View.OnTouchListener getHapticFeedbackTouchListener() {
        if (mHapticFeedbackTouchListener == null) {
            mHapticFeedbackTouchListener = new View.OnTouchListener() {
                @SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                    return false;
                }
            };
        }
        return mHapticFeedbackTouchListener;
    }

    public void onDragStarted(View view) {
        if (isOnCustomContent()) {
            // Custom content screen doesn't participate in drag and drop. If on custom
            // content screen, move to default.
            moveWorkspaceToDefaultScreen();
        }

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onDragStarted(view);
        }
    }

    /**
     * Called when the user stops interacting with the launcher.
     * This implies that the user is now on the homescreen and is not doing housekeeping.
     */
    protected void onInteractionEnd() {
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onInteractionEnd();
        }
    }

    /**
     * Called when the user starts interacting with the launcher.
     * The possible interactions are:
     *  - open all apps
     *  - reorder an app shortcut, or a widget
     *  - open the overview mode.
     * This is a good time to stop doing things that only make sense
     * when the user is on the homescreen and not doing housekeeping.
     */
    protected void onInteractionBegin() {
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onInteractionBegin();
        }
    }

    /** Updates the interaction state. */
    public void updateInteraction(Workspace.State fromState, Workspace.State toState) {
        // Only update the interacting state if we are transitioning to/from a view with an
        // overlay
        boolean fromStateWithOverlay = fromState != Workspace.State.NORMAL;
        boolean toStateWithOverlay = toState != Workspace.State.NORMAL;
        if (toStateWithOverlay) {
            onInteractionBegin();
        } else if (fromStateWithOverlay) {
            onInteractionEnd();
        }
    }

    void startApplicationDetailsActivity(ComponentName componentName, UserHandleCompat user) {
        try {
            LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(this);
            launcherApps.showAppDetailsForProfile(componentName, user);
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have permission to launch settings");
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch settings");
        }
    }

    // returns true if the activity was started
    boolean startApplicationUninstallActivity(ComponentName componentName, int flags,
            UserHandleCompat user) {
        if ((flags & AppInfo.DOWNLOADED_FLAG) == 0) {
            // System applications cannot be installed. For now, show a toast explaining that.
            // We may give them the option of disabling apps this way.
            int messageId = R.string.uninstall_system_app_text;
            Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
            return false;
        } else {
            String packageName = componentName.getPackageName();
            String className = componentName.getClassName();
            Intent intent = new Intent(
                    Intent.ACTION_DELETE, Uri.fromParts("package", packageName, className));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            if (user != null) {
                user.addToIntent(intent, Intent.EXTRA_USER);
            }
            startActivity(intent);
            return true;
        }
    }

    private boolean startActivity(View v, Intent intent, Object tag) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Only launch using the new animation if the shortcut has not opted out (this is a
            // private contract between launcher and may be ignored in the future).
            boolean useLaunchAnimation = (v != null) &&
                    !intent.hasExtra(INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION);
            LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(this);
            UserManagerCompat userManager = UserManagerCompat.getInstance(this);

            UserHandleCompat user = null;
            if (intent.hasExtra(AppInfo.EXTRA_PROFILE)) {
                long serialNumber = intent.getLongExtra(AppInfo.EXTRA_PROFILE, -1);
                user = userManager.getUserForSerialNumber(serialNumber);
            }

            Bundle optsBundle = null;
            if (useLaunchAnimation) {
                ActivityOptions opts = null;
                if (Utilities.ATLEAST_MARSHMALLOW) {
                    int left = 0, top = 0;
                    int width = v.getMeasuredWidth(), height = v.getMeasuredHeight();
                    if (v instanceof TextView) {
                        // Launch from center of icon, not entire view
                        Drawable icon = Workspace.getTextViewIcon((TextView) v);
                        if (icon != null) {
                            Rect bounds = icon.getBounds();
                            left = (width - bounds.width()) / 2;
                            top = v.getPaddingTop();
                            width = bounds.width();
                            height = bounds.height();
                        }
                    }
                    opts = ActivityOptions.makeClipRevealAnimation(v, left, top, width, height);
                } else if (!Utilities.ATLEAST_LOLLIPOP) {
                    // Below L, we use a scale up animation
                    opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0,
                                    v.getMeasuredWidth(), v.getMeasuredHeight());
                } else if (Utilities.ATLEAST_LOLLIPOP_MR1) {
                    // On L devices, we use the device default slide-up transition.
                    // On L MR1 devices, we a custom version of the slide-up transition which
                    // doesn't have the delay present in the device default.
                    opts = ActivityOptions.makeCustomAnimation(this,
                            R.anim.task_open_enter, R.anim.no_anim);
                }
                optsBundle = opts != null ? opts.toBundle() : null;
            }

            if (user == null || user.equals(UserHandleCompat.myUserHandle())) {
                // Could be launching some bookkeeping activity
                startActivity(intent, optsBundle);
            } else {
                // TODO Component can be null when shortcuts are supported for secondary user
                launcherApps.startActivityForProfile(intent.getComponent(), user,
                        intent.getSourceBounds(), optsBundle);
            }
            return true;
        } catch (SecurityException | NullPointerException e) {
            if (Utilities.ATLEAST_MARSHMALLOW && tag instanceof ItemInfo) {
                // Due to legacy reasons, direct call shortcuts require Launchers to have the
                // corresponding permission. Show the appropriate permission prompt if that
                // is the case.
                if (intent.getComponent() == null
                        && Intent.ACTION_CALL.equals(intent.getAction())
                        && checkSelfPermission(Manifest.permission.CALL_PHONE) !=
                            PackageManager.PERMISSION_GRANTED) {
                    // TODO: Rename sPendingAddItem to a generic name.
                    sPendingAddItem = preparePendingAddArgs(REQUEST_PERMISSION_CALL_PHONE, intent,
                            0, (ItemInfo) tag);
                    requestPermissions(new String[]{Manifest.permission.CALL_PHONE},
                            REQUEST_PERMISSION_CALL_PHONE);
                    return false;
                }
            }
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity. "
                    + "tag="+ tag + " intent=" + intent, e);
        }
        return false;
    }

    public boolean startActivitySafely(View v, Intent intent, Object tag) {
        boolean success = false;
        if (mIsSafeModeEnabled && !Utilities.isSystemApp(this, intent)) {
            Toast.makeText(this, R.string.safemode_shortcut_error, Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            success = startActivity(v, intent, tag);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + tag + " intent=" + intent, e);
        }
        return success;
    }

    /**
     * This method draws the FolderIcon to an ImageView and then adds and positions that ImageView
     * in the DragLayer in the exact absolute location of the original FolderIcon.
     */
    private void copyFolderIconToImage(FolderIcon fi) {
        final int width = fi.getMeasuredWidth();
        final int height = fi.getMeasuredHeight();

        // Lazy load ImageView, Bitmap and Canvas
        if (mFolderIconImageView == null) {
            mFolderIconImageView = new ImageView(this);
        }
        if (mFolderIconBitmap == null || mFolderIconBitmap.getWidth() != width ||
                mFolderIconBitmap.getHeight() != height) {
            mFolderIconBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mFolderIconCanvas = new Canvas(mFolderIconBitmap);
        }

        DragLayer.LayoutParams lp;
        if (mFolderIconImageView.getLayoutParams() instanceof DragLayer.LayoutParams) {
            lp = (DragLayer.LayoutParams) mFolderIconImageView.getLayoutParams();
        } else {
            lp = new DragLayer.LayoutParams(width, height);
        }

        // The layout from which the folder is being opened may be scaled, adjust the starting
        // view size by this scale factor.
        float scale = mDragLayer.getDescendantRectRelativeToSelf(fi, mRectForFolderAnimation);
        lp.customPosition = true;
        lp.x = mRectForFolderAnimation.left;
        lp.y = mRectForFolderAnimation.top;
        lp.width = (int) (scale * width);
        lp.height = (int) (scale * height);

        mFolderIconCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        fi.draw(mFolderIconCanvas);
        mFolderIconImageView.setImageBitmap(mFolderIconBitmap);
        if (fi.getFolder() != null) {
            mFolderIconImageView.setPivotX(fi.getFolder().getPivotXForIconAnimation());
            mFolderIconImageView.setPivotY(fi.getFolder().getPivotYForIconAnimation());
        }
        // Just in case this image view is still in the drag layer from a previous animation,
        // we remove it and re-add it.
        if (mDragLayer.indexOfChild(mFolderIconImageView) != -1) {
            mDragLayer.removeView(mFolderIconImageView);
        }
        mDragLayer.addView(mFolderIconImageView, lp);
        if (fi.getFolder() != null) {
            fi.getFolder().bringToFront();
        }
    }

    private void growAndFadeOutFolderIcon(FolderIcon fi) {
        if (fi == null) return;
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0);
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.5f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.5f);

        FolderInfo info = (FolderInfo) fi.getTag();
        if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            CellLayout cl = (CellLayout) fi.getParent().getParent();
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) fi.getLayoutParams();
            cl.setFolderLeaveBehindCell(lp.cellX, lp.cellY);
        }

        // Push an ImageView copy of the FolderIcon into the DragLayer and hide the original
        copyFolderIconToImage(fi);
        fi.setVisibility(View.INVISIBLE);

        ObjectAnimator oa = LauncherAnimUtils.ofPropertyValuesHolder(mFolderIconImageView, alpha,
                scaleX, scaleY);
        if (Utilities.ATLEAST_LOLLIPOP) {
            oa.setInterpolator(new LogDecelerateInterpolator(100, 0));
        }
        oa.setDuration(getResources().getInteger(R.integer.config_folderExpandDuration));
        oa.start();
    }

    private void shrinkAndFadeInFolderIcon(final FolderIcon fi) {
        if (fi == null) return;
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 1.0f);
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.0f);

        final CellLayout cl = (CellLayout) fi.getParent().getParent();

        // We remove and re-draw the FolderIcon in-case it has changed
        mDragLayer.removeView(mFolderIconImageView);
        copyFolderIconToImage(fi);
        ObjectAnimator oa = LauncherAnimUtils.ofPropertyValuesHolder(mFolderIconImageView, alpha,
                scaleX, scaleY);
        oa.setDuration(getResources().getInteger(R.integer.config_folderExpandDuration));
        oa.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (cl != null) {
                    cl.clearFolderLeaveBehind();
                    // Remove the ImageView copy of the FolderIcon and make the original visible.
                    mDragLayer.removeView(mFolderIconImageView);
                    fi.setVisibility(View.VISIBLE);
                }
            }
        });
        oa.start();
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     *
     * @param folderIcon The FolderIcon describing the folder to open.
     */
    public void openFolder(FolderIcon folderIcon, int[] folderTouch) {
        Folder folder = folderIcon.getFolder();

        if (folder.getState() == Folder.STATE_ANIMATING) {
            return;
        }

        Folder openFolder = mWorkspace != null ? mWorkspace.getOpenFolder() : null;
        if (openFolder != null && openFolder != folder) {
            // Close any open folder before opening a folder.
            closeFolder();
        }

        FolderInfo info = folder.mInfo;

        if (info.hidden && !mHiddenFolderAuth) {
            folder.startHiddenFolderManager(REQUEST_OPEN_PROTECTED_FOLDER);
            return;
        }

        info.opened = true;

        // While the folder is open, the position of the icon cannot change.
        ((CellLayout.LayoutParams) folderIcon.getLayoutParams()).canReorder = false;

        // Just verify that the folder hasn't already been added to the DragLayer.
        // There was a one-off crash where the folder had a parent already.
        if (folder.getParent() == null) {
            mDragLayer.addView(folder);
            mDragController.addDropTarget((DropTarget) folder);
        } else {
            Log.w(TAG, "Opening folder (" + folder + ") which already has a parent (" +
                    folder.getParent() + ").");
            return;
        }
        folder.animateOpen();
        /*growAndFadeOutFolderIcon(folderIcon);*/

        // Notify the accessibility manager that this folder "window" has appeared and occluded
        // the workspace items
        folder.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        getDragLayer().sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    public void closeFolder() {
        closeFolder(true);
    }

    public void closeFolder(boolean animate) {
        Folder folder = mWorkspace != null ? mWorkspace.getOpenFolder() : null;
        if (folder != null) {
            if (folder.isEditingName()) {
                folder.dismissEditingName();
            }
            closeFolder(folder, animate);
        }
    }

    public void closeFolder(Folder folder) {
        closeFolder(folder, true);
    }

    public void closeFolder(Folder folder, boolean animate) {
        final FolderInfo info = folder.getInfo();
        info.opened = false;
        if (info.hidden) {
            mHiddenFolderAuth = false;
        }

        ViewGroup parent = (ViewGroup) folder.getParent().getParent();
        if (parent != null) {
            FolderIcon fi = (FolderIcon) mWorkspace.getViewForTag(folder.mInfo);
            /*shrinkAndFadeInFolderIcon(fi);*/
            if (fi != null) {
                ((CellLayout.LayoutParams) fi.getLayoutParams()).canReorder = true;
            }
        }
        folder.animateClosed(mHiddenFolderLockStateChanged ? false : animate);

        // Notify the accessibility manager that this folder "window" has disappeard and no
        // longer occludeds the workspace items
        getDragLayer().sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        mHiddenFolderIcon = null;
        mHiddenFolderLockStateChanged = false;
    }

    public boolean onLongClick(View v) {
        if (!isDraggingEnabled()) return false;
        if (isWorkspaceLocked()) return false;
        if (mState != State.WORKSPACE) return false;

        if (v == mAllAppsButton) {
            onLongClickAllAppsButton(v);
            return true;
        }

        if (v instanceof Workspace) {
            if (!mWorkspace.isInOverviewMode()) {
                if (!mWorkspace.isTouchActive()) {
                    showOverviewMode(true);
                    mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        CellLayout.CellInfo longClickCellInfo = null;
        View itemUnderLongClick = null;
        if (v.getTag() instanceof ItemInfo) {
            ItemInfo info = (ItemInfo) v.getTag();
            longClickCellInfo = new CellLayout.CellInfo(v, info);
            itemUnderLongClick = longClickCellInfo.cell;
            resetAddInfo();
        }

        // The hotseat touch handling does not go through Workspace, and we always allow long press
        // on hotseat items.
        final boolean inHotseat = isHotseatLayout(v);
        if (!mDragController.isDragging()) {
            if (itemUnderLongClick == null) {
                // User long pressed on empty space
                mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                if (mWorkspace.isInOverviewMode()) {
                    mWorkspace.startReordering(v);
                } else {
                    showOverviewMode(true);
                }
            } else {
                final boolean isAllAppsButton = inHotseat && isAllAppsButtonRank(
                        mHotseat.getOrderInHotseat(
                                longClickCellInfo.cellX,
                                longClickCellInfo.cellY));
                if (!(itemUnderLongClick instanceof Folder || isAllAppsButton)) {
                    // User long pressed on an item
                    mWorkspace.startDrag(longClickCellInfo);
                }
            }
        }
        return true;
    }

    boolean isHotseatLayout(View layout) {
        return mHotseat != null && layout != null &&
                (layout instanceof CellLayout) && (layout == mHotseat.getLayout());
    }

    /**
     * Returns the CellLayout of the specified container at the specified screen.
     */
    public CellLayout getCellLayout(long container, long screenId) {
        if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            if (mHotseat != null) {
                return mHotseat.getLayout();
            } else {
                return null;
            }
        } else {
            return mWorkspace.getScreenWithId(screenId);
        }
    }

    /**
     * For overridden classes.
     */
    public boolean isAllAppsVisible() {
        return isAppsViewVisible();
    }

    public boolean isAppsViewVisible() {
        return (mState == State.APPS) || (mOnResumeState == State.APPS);
    }

    public boolean isWidgetsViewVisible() {
        return (mState == State.WIDGETS) || (mOnResumeState == State.WIDGETS);
    }

    private void setWorkspaceBackground(int background) {
        switch (background) {
            case WORKSPACE_BACKGROUND_TRANSPARENT:
                getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                break;
            case WORKSPACE_BACKGROUND_BLACK:
                getWindow().setBackgroundDrawable(null);
                break;
            default:
                getWindow().setBackgroundDrawable(mWorkspaceBackgroundDrawable);
        }
    }

    protected void changeWallpaperVisiblity(boolean visible) {
        int wpflags = visible ? WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER : 0;
        int curflags = getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        if (wpflags != curflags) {
            getWindow().setFlags(wpflags, WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        }
        setWorkspaceBackground(visible ? WORKSPACE_BACKGROUND_GRADIENT : WORKSPACE_BACKGROUND_BLACK);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // The widget preview db can result in holding onto over
            // 3MB of memory for caching which isn't necessary.
            SQLiteDatabase.releaseMemory();

            // This clears all widget bitmaps from the widget tray
            // TODO(hyunyoungs)
        }
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onTrimMemory(level);
        }
    }

    public void showWorkspace(boolean animated) {
        showWorkspace(WorkspaceStateTransitionAnimation.SCROLL_TO_CURRENT_PAGE, animated, null);
    }

    public void showWorkspace(boolean animated, Runnable onCompleteRunnable) {
        showWorkspace(WorkspaceStateTransitionAnimation.SCROLL_TO_CURRENT_PAGE, animated,
                onCompleteRunnable);
    }

    protected void showWorkspace(int snapToPage, boolean animated) {
        showWorkspace(snapToPage, animated, null);
    }

    void showWorkspace(int snapToPage, boolean animated, Runnable onCompleteRunnable) {
        boolean changed = mState != State.WORKSPACE ||
                mWorkspace.getState() != Workspace.State.NORMAL;
        if (changed) {
            // Close overview mode if open
            if (mWorkspace.isInOverviewMode()) {
                mWorkspace.exitOverviewMode();
            }
            mWorkspace.setVisibility(View.VISIBLE);
            mStateTransitionAnimation.startAnimationToWorkspace(mState, mWorkspace.getState(),
                    Workspace.State.NORMAL, snapToPage, animated, onCompleteRunnable);

            // Set focus to the AppsCustomize button
            if (mAllAppsButton != null) {
                mAllAppsButton.requestFocus();
            }
        }

        // Change the state *after* we've called all the transition code
        mState = State.WORKSPACE;
        setWorkspaceBackground(mState == State.WORKSPACE ? WORKSPACE_BACKGROUND_GRADIENT
                : WORKSPACE_BACKGROUND_TRANSPARENT);

        // Resume the auto-advance of widgets
        mUserPresent = true;
        updateAutoAdvanceState();

        if (changed) {
            // Send an accessibility event to announce the context change
            getWindow().getDecorView()
                    .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
    }

    void showOverviewMode(boolean animated) {
        mWorkspace.setVisibility(View.VISIBLE);
        mStateTransitionAnimation.startAnimationToWorkspace(mState, mWorkspace.getState(),
                Workspace.State.OVERVIEW,
                WorkspaceStateTransitionAnimation.SCROLL_TO_CURRENT_PAGE, animated,
                null /* onCompleteRunnable */);
        mState = State.WORKSPACE;
    }

    /**
     * Shows the apps view.
     */
    void showAppsView(boolean animated, boolean resetListToTop, boolean updatePredictedApps,
            boolean focusSearchBar) {
        if (resetListToTop) {
            mAppsView.scrollToTop();
        }
        if (updatePredictedApps) {
            tryAndUpdatePredictedApps();
        }
        showAppsOrWidgets(State.APPS, animated, focusSearchBar);

        sRemoteFolderManager.onAppDrawerOpened();
    }

    /**
     * Shows the widgets view.
     */
    void showWidgetsView(boolean animated, boolean resetPageToZero) {
        if (LOGD) Log.d(TAG, "showWidgetsView:" + animated + " resetPageToZero:" + resetPageToZero);
        if (resetPageToZero) {
            mWidgetsView.scrollToTop();
        }
        showAppsOrWidgets(State.WIDGETS, animated, false);

        mWidgetsView.post(new Runnable() {
            @Override
            public void run() {
                mWidgetsView.requestFocus();
            }
        });
    }

    /**
     * Sets up the transition to show the apps/widgets view.
     *
     * @return whether the current from and to state allowed this operation
     */
    // TODO: calling method should use the return value so that when {@code false} is returned
    // the workspace transition doesn't fall into invalid state.
    private boolean showAppsOrWidgets(State toState, boolean animated, boolean focusSearchBar) {
        if (mState != State.WORKSPACE &&  mState != State.APPS_SPRING_LOADED &&
                mState != State.WIDGETS_SPRING_LOADED) {
            return false;
        }
        if (toState != State.APPS && toState != State.WIDGETS) {
            return false;
        }

        if (toState == State.APPS) {
            mStateTransitionAnimation.startAnimationToAllApps(mWorkspace.getState(), animated,
                    focusSearchBar);
        } else {
            mStateTransitionAnimation.startAnimationToWidgets(mWorkspace.getState(), animated);
        }

        // Change the state *after* we've called all the transition code
        mState = toState;

        // Pause the auto-advance of widgets until we are out of AllApps
        mUserPresent = false;
        updateAutoAdvanceState();
        closeFolder();

        // Send an accessibility event to announce the context change
        getWindow().getDecorView()
                .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        return true;
    }

    /**
     * Updates the workspace and interaction state on state change, and return the animation to this
     * new state.
     */
    public Animator startWorkspaceStateChangeAnimation(Workspace.State toState, int toPage,
            boolean animated, HashMap<View, Integer> layerViews) {
        Workspace.State fromState = mWorkspace.getState();
        Animator anim = mWorkspace.setStateWithAnimation(toState, toPage, animated, layerViews);
        updateInteraction(fromState, toState);
        return anim;
    }

    public void enterSpringLoadedDragMode() {
        if (LOGD) Log.d(TAG, String.format("enterSpringLoadedDragMode [mState=%s", mState.name()));
        if (mState == State.WORKSPACE || mState == State.APPS_SPRING_LOADED ||
                mState == State.WIDGETS_SPRING_LOADED) {
            return;
        }

        mStateTransitionAnimation.startAnimationToWorkspace(mState, mWorkspace.getState(),
                Workspace.State.SPRING_LOADED,
                WorkspaceStateTransitionAnimation.SCROLL_TO_CURRENT_PAGE, true /* animated */,
                null /* onCompleteRunnable */);
        mState = isAppsViewVisible() ? State.APPS_SPRING_LOADED : State.WIDGETS_SPRING_LOADED;
    }

    public void exitSpringLoadedDragModeDelayed(final boolean successfulDrop, int delay,
            final Runnable onCompleteRunnable) {
        if (mState != State.APPS_SPRING_LOADED && mState != State.WIDGETS_SPRING_LOADED) return;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (successfulDrop) {
                    // TODO(hyunyoungs): verify if this hack is still needed, if not, delete.
                    //
                    // Before we show workspace, hide all apps again because
                    // exitSpringLoadedDragMode made it visible. This is a bit hacky; we should
                    // clean up our state transition functions
                    mWidgetsView.setVisibility(View.GONE);
                    showWorkspace(true, onCompleteRunnable);
                } else {
                    exitSpringLoadedDragMode();
                }
            }
        }, delay);
    }

    void exitSpringLoadedDragMode() {
        if (mState == State.APPS_SPRING_LOADED) {
            showAppsView(true /* animated */, false /* resetListToTop */,
                    false /* updatePredictedApps */, false /* focusSearchBar */);
        } else if (mState == State.WIDGETS_SPRING_LOADED) {
            showWidgetsView(true, false);
        }
    }

    /**
     * Updates the set of predicted apps if it hasn't been updated since the last time Launcher was
     * resumed.
     */
    private void tryAndUpdatePredictedApps() {
        boolean mRemoteDrawerEnabled = SettingsProvider.getBoolean(this,
                SettingsProvider.SETTINGS_UI_DRAWER_REMOTE_APPS,
                R.bool.preferences_interface_drawer_remote_apps_default);
        if (!mRemoteDrawerEnabled) {
            if (mLauncherCallbacks != null) {
                List<ComponentKey> apps = mLauncherCallbacks.getPredictedApps();
                if (apps != null) {
                    mAppsView.setPredictedAppComponents(apps);
                }
            }
        }
    }

    void lockAllApps() {
        // TODO
    }

    void unlockAllApps() {
        // TODO
    }

    protected void disableVoiceButtonProxy(boolean disable) {
        // NO-OP
    }

    public View getOrCreateQsbBar() {
        if (mLauncherCallbacks != null && mLauncherCallbacks.providesSearch()) {
            return mLauncherCallbacks.getQsbBar();
        }

        if (mQsb == null) {
            AppWidgetProviderInfo searchProvider = Utilities.getSearchWidgetProvider(this);
            if (searchProvider == null) {
                return null;
            }

            Bundle opts = new Bundle();
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                    AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX);

            SharedPreferences sp = getSharedPreferences(
                    LauncherAppState.getSharedPreferencesKey(), MODE_PRIVATE);
            int widgetId = sp.getInt(QSB_WIDGET_ID, -1);
            AppWidgetProviderInfo widgetInfo = mAppWidgetManager.getAppWidgetInfo(widgetId);
            if (!searchProvider.provider.flattenToString().equals(
                    sp.getString(QSB_WIDGET_PROVIDER, null))
                    || (widgetInfo == null)
                    || !widgetInfo.provider.equals(searchProvider.provider)) {
                // A valid widget is not already bound.
                if (widgetId > -1) {
                    mAppWidgetHost.deleteAppWidgetId(widgetId);
                    widgetId = -1;
                }

                // Try to bind a new widget
                widgetId = mAppWidgetHost.allocateAppWidgetId();

                if (!AppWidgetManagerCompat.getInstance(this)
                        .bindAppWidgetIdIfAllowed(widgetId, searchProvider, opts)) {
                    mAppWidgetHost.deleteAppWidgetId(widgetId);
                    widgetId = -1;
                }

                sp.edit()
                    .putInt(QSB_WIDGET_ID, widgetId)
                    .putString(QSB_WIDGET_PROVIDER, searchProvider.provider.flattenToString())
                    .commit();
            }

            mAppWidgetHost.setQsbWidgetId(widgetId);
            if (widgetId != -1) {
                mQsb = mAppWidgetHost.createView(this, widgetId, searchProvider);
                mQsb.setId(R.id.qsb_widget);
                mQsb.updateAppWidgetOptions(opts);
                mQsb.setPadding(0, 0, 0, 0);
                mSearchDropTargetBar.addView(mQsb);
                mSearchDropTargetBar.setQsbSearchBar(mQsb);
            }
        }
        return mQsb;
    }

    private void reinflateQSBIfNecessary() {
        if (mQsb instanceof LauncherAppWidgetHostView &&
                ((LauncherAppWidgetHostView) mQsb).isReinflateRequired()) {
            mSearchDropTargetBar.removeView(mQsb);
            mQsb = null;
            mSearchDropTargetBar.setQsbSearchBar(getOrCreateQsbBar());
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        final boolean result = super.dispatchPopulateAccessibilityEvent(event);
        final List<CharSequence> text = event.getText();
        text.clear();
        // Populate event with a fake title based on the current state.
        if (mState == State.APPS) {
            text.add(getString(R.string.all_apps_button_label));
        } else if (mState == State.WIDGETS) {
            text.add(getString(R.string.widget_button_text));
        } else if (mWorkspace != null) {
            text.add(mWorkspace.getCurrentPageDescription());
        } else {
            text.add(getString(R.string.all_apps_home_button_label));
        }
        return result;
    }

    /**
     * Receives notifications when system dialogs are to be closed.
     */
    @Thunk class CloseSystemDialogsIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            closeSystemDialogs();
        }
    }

    /**
     * If the activity is currently paused, signal that we need to run the passed Runnable
     * in onResume.
     *
     * This needs to be called from incoming places where resources might have been loaded
     * while the activity is paused. That is because the Configuration (e.g., rotation)  might be
     * wrong when we're not running, and if the activity comes back to what the configuration was
     * when we were paused, activity is not restarted.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @return {@code true} if we are currently paused. The caller might be able to skip some work
     */
    @Thunk boolean waitUntilResume(Runnable run, boolean deletePreviousRunnables) {
        if (mPaused) {
            if (LOGD) Log.d(TAG, "Deferring update until onResume");
            if (deletePreviousRunnables) {
                while (mBindOnResumeCallbacks.remove(run)) {
                }
            }
            mBindOnResumeCallbacks.add(run);
            return true;
        } else {
            return false;
        }
    }

    private boolean waitUntilResume(Runnable run) {
        return waitUntilResume(run, false);
    }

    public void addOnResumeCallback(Runnable run) {
        mOnResumeCallbacks.add(run);
    }

    /**
     * If the activity is currently paused, signal that we need to re-run the loader
     * in onResume.
     *
     * This needs to be called from incoming places where resources might have been loaded
     * while we are paused.  That is becaues the Configuration might be wrong
     * when we're not running, and if it comes back to what it was when we
     * were paused, we are not restarted.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @return true if we are currently paused.  The caller might be able to
     * skip some work in that case since we will come back again.
     */
    public boolean setLoadOnResume() {
        if (mPaused) {
            if (LOGD) Log.d(TAG, "setLoadOnResume");
            mOnResumeNeedsLoad = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public int getCurrentWorkspaceScreen() {
        if (mWorkspace != null) {
            return mWorkspace.getCurrentPage();
        } else {
            return SCREEN_COUNT / 2;
        }
    }

    /**
     * Refreshes the shortcuts shown on the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void startBinding() {
        setWorkspaceLoading(true);

        // If we're starting binding all over again, clear any bind calls we'd postponed in
        // the past (see waitUntilResume) -- we don't need them since we're starting binding
        // from scratch again
        mBindOnResumeCallbacks.clear();

        // Clear the workspace because it's going to be rebound
        mWorkspace.clearDropTargets();
        mWorkspace.removeAllWorkspaceScreens();

        mWidgetsToAdvance.clear();
        if (mHotseat != null) {
            mHotseat.resetLayout();
        }
    }

    @Override
    public void bindScreens(ArrayList<Long> orderedScreenIds) {
        bindAddScreens(orderedScreenIds);

        // If there are no screens, we need to have an empty screen
        if (orderedScreenIds.size() == 0) {
            mWorkspace.addExtraEmptyScreen();
        }

        // Create the custom content page (this call updates mDefaultScreen which calls
        // setCurrentPage() so ensure that all pages are added before calling this).
        if (hasCustomContentToLeft()) {
            mWorkspace.createCustomContentContainer();
            populateCustomContentContainer();
        }

        LauncherModel.saveWidgetCount(this);
        LauncherModel.savePageCount(this);

    }

    @Override
    public void bindAddScreens(ArrayList<Long> orderedScreenIds) {
        int count = orderedScreenIds.size();
        for (int i = 0; i < count; i++) {
            mWorkspace.insertNewWorkspaceScreenBeforeEmptyScreen(orderedScreenIds.get(i));
        }
    }

    private boolean shouldShowWeightWatcher() {
        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = getSharedPreferences(spKey, Context.MODE_PRIVATE);
        boolean show = sp.getBoolean(SHOW_WEIGHT_WATCHER, SHOW_WEIGHT_WATCHER_DEFAULT);

        return show;
    }

    private void toggleShowWeightWatcher() {
        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = getSharedPreferences(spKey, Context.MODE_PRIVATE);
        boolean show = sp.getBoolean(SHOW_WEIGHT_WATCHER, true);

        show = !show;

        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(SHOW_WEIGHT_WATCHER, show);
        editor.commit();

        if (mWeightWatcher != null) {
            mWeightWatcher.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public void bindAppsAdded(final ArrayList<Long> newScreens,
                              final ArrayList<ItemInfo> addNotAnimated,
                              final ArrayList<ItemInfo> addAnimated,
                              final ArrayList<AppInfo> addedApps) {
        Runnable r = new Runnable() {
            public void run() {
                bindAppsAdded(newScreens, addNotAnimated, addAnimated, addedApps);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        // Add the new screens
        if (newScreens != null) {
            bindAddScreens(newScreens);
        }

        // We add the items without animation on non-visible pages, and with
        // animations on the new page (which we will try and snap to).
        if (addNotAnimated != null && !addNotAnimated.isEmpty()) {
            bindItems(addNotAnimated, 0,
                    addNotAnimated.size(), false);
        }
        if (addAnimated != null && !addAnimated.isEmpty()) {
            bindItems(addAnimated, 0,
                    addAnimated.size(), true);
        }

        // Remove the extra empty screen
        mWorkspace.removeExtraEmptyScreen(false, false);

        if (addedApps != null && mAppsView != null) {
            mAppsView.addApps(addedApps);
            sRemoteFolderManager.onBindAddApps(addedApps);
        }
    }

    /**
     * Bind the items start-end from the list.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindItems(final ArrayList<ItemInfo> shortcuts, final int start, final int end,
                          final boolean forceAnimateIcons) {
        Runnable r = new Runnable() {
            public void run() {
                bindItems(shortcuts, start, end, forceAnimateIcons);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        // Get the list of added shortcuts and intersect them with the set of shortcuts here
        final AnimatorSet anim = LauncherAnimUtils.createAnimatorSet();
        final Collection<Animator> bounceAnims = new ArrayList<Animator>();
        final boolean animateIcons = forceAnimateIcons && canRunNewAppsAnimation();
        final Workspace workspace = mWorkspace;
        long newShortcutsScreenId = -1;
        for (int i = start; i < end; i++) {
            final ItemInfo item = shortcuts.get(i);

            // Short circuit if we are loading dock items for a configuration which has no dock
            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
                    mHotseat == null) {
                continue;
            }

            final View view;
            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    ShortcutInfo info = (ShortcutInfo) item;
                    view = createShortcut(info);

                    /*
                     * TODO: FIX collision case
                     */
                    if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                        CellLayout cl = mWorkspace.getScreenWithId(item.screenId);
                        if (cl != null && cl.isOccupied(item.cellX, item.cellY)) {
                            View v = cl.getChildAt(item.cellX, item.cellY);
                            Object tag = v.getTag();
                            String desc = "Collision while binding workspace item: " + item
                                    + ". Collides with " + tag;
                            if (LauncherAppState.isDogfoodBuild()) {
                                throw (new RuntimeException(desc));
                            } else {
                                Log.d(TAG, desc);
                            }
                        }
                    }
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                    view = FolderIcon.fromXml(R.layout.folder_icon, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentPage()),
                            (FolderInfo) item, mIconCache);
                    if (view == null) {
                        continue;
                    }
                    ((FolderIcon) view).setTextVisible(!mHideIconLabels);
                    break;
                default:
                    throw new RuntimeException("Invalid Item Type");
            }

            workspace.addInScreenFromBind(view, item.container, item.screenId, item.cellX,
                    item.cellY, 1, 1);
            if (animateIcons) {
                // Animate all the applications up now
                view.setAlpha(0f);
                view.setScaleX(0f);
                view.setScaleY(0f);
                bounceAnims.add(createNewAppBounceAnimation(view, i));
                newShortcutsScreenId = item.screenId;
            }
        }

        if (animateIcons) {
            // Animate to the correct page
            if (newShortcutsScreenId > -1) {
                long currentScreenId = mWorkspace.getScreenIdForPageIndex(mWorkspace.getNextPage());
                final int newScreenIndex = mWorkspace.getPageIndexForScreenId(newShortcutsScreenId);
                final Runnable startBounceAnimRunnable = new Runnable() {
                    public void run() {
                        anim.playTogether(bounceAnims);
                        anim.start();
                    }
                };
                if (newShortcutsScreenId != currentScreenId) {
                    // We post the animation slightly delayed to prevent slowdowns
                    // when we are loading right after we return to launcher.
                    mWorkspace.postDelayed(new Runnable() {
                        public void run() {
                            if (mWorkspace != null) {
                                mWorkspace.snapToPage(newScreenIndex);
                                mWorkspace.postDelayed(startBounceAnimRunnable,
                                        NEW_APPS_ANIMATION_DELAY);
                            }
                        }
                    }, NEW_APPS_PAGE_MOVE_DELAY);
                } else {
                    mWorkspace.postDelayed(startBounceAnimRunnable, NEW_APPS_ANIMATION_DELAY);
                }
            }
        }
        workspace.requestLayout();
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindFolders(final LongArrayMap<FolderInfo> folders) {
        Runnable r = new Runnable() {
            public void run() {
                bindFolders(folders);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }
        sFolders = folders.clone();
    }

    /**
     * Add the views for a widget to the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppWidget(final LauncherAppWidgetInfo item) {
        Runnable r = new Runnable() {
            public void run() {
                bindAppWidget(item);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        final long start = DEBUG_WIDGETS ? SystemClock.uptimeMillis() : 0;
        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bindAppWidget: " + item);
        }
        final Workspace workspace = mWorkspace;

        LauncherAppWidgetProviderInfo appWidgetInfo =
                LauncherModel.getProviderInfo(this, item.providerName, item.user);

        if (!mIsSafeModeEnabled
                && ((item.restoreStatus & LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY) == 0)
                && (item.restoreStatus != LauncherAppWidgetInfo.RESTORE_COMPLETED)) {

            if (appWidgetInfo == null) {
                if (DEBUG_WIDGETS) {
                    Log.d(TAG, "Removing restored widget: id=" + item.appWidgetId
                            + " belongs to component " + item.providerName
                            + ", as the povider is null");
                }
                LauncherModel.deleteItemFromDatabase(this, item);
                return;
            }

            // If we do not have a valid id, try to bind an id.
            if ((item.restoreStatus & LauncherAppWidgetInfo.FLAG_ID_NOT_VALID) != 0) {
                // Note: This assumes that the id remap broadcast is received before this step.
                // If that is not the case, the id remap will be ignored and user may see the
                // click to setup view.
                PendingAddWidgetInfo pendingInfo = new PendingAddWidgetInfo(this, appWidgetInfo, null);
                pendingInfo.spanX = item.spanX;
                pendingInfo.spanY = item.spanY;
                pendingInfo.minSpanX = item.minSpanX;
                pendingInfo.minSpanY = item.minSpanY;
                Bundle options = null;
                        WidgetHostViewLoader.getDefaultOptionsForWidget(this, pendingInfo);

                int newWidgetId = mAppWidgetHost.allocateAppWidgetId();
                boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
                        newWidgetId, appWidgetInfo, options);

                // TODO consider showing a permission dialog when the widget is clicked.
                if (!success) {
                    mAppWidgetHost.deleteAppWidgetId(newWidgetId);
                    if (DEBUG_WIDGETS) {
                        Log.d(TAG, "Removing restored widget: id=" + item.appWidgetId
                                + " belongs to component " + item.providerName
                                + ", as the launcher is unable to bing a new widget id");
                    }
                    LauncherModel.deleteItemFromDatabase(this, item);
                    return;
                }

                item.appWidgetId = newWidgetId;

                // If the widget has a configure activity, it is still needs to set it up, otherwise
                // the widget is ready to go.
                item.restoreStatus = (appWidgetInfo.configure == null)
                        ? LauncherAppWidgetInfo.RESTORE_COMPLETED
                        : LauncherAppWidgetInfo.FLAG_UI_NOT_READY;

                LauncherModel.updateItemInDatabase(this, item);
            } else if (((item.restoreStatus & LauncherAppWidgetInfo.FLAG_UI_NOT_READY) != 0)
                    && (appWidgetInfo.configure == null)) {
                // If the ID is already valid, verify if we need to configure or not.
                item.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED;
                LauncherModel.updateItemInDatabase(this, item);
            }
        }

        if (!mIsSafeModeEnabled && item.restoreStatus == LauncherAppWidgetInfo.RESTORE_COMPLETED) {
            final int appWidgetId = item.appWidgetId;
            if (DEBUG_WIDGETS) {
                Log.d(TAG, "bindAppWidget: id=" + item.appWidgetId + " belongs to component "
                        + appWidgetInfo.provider);
            }

            item.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);
            item.minSpanX = appWidgetInfo.minSpanX;
            item.minSpanY = appWidgetInfo.minSpanY;
        } else {
            appWidgetInfo = null;
            PendingAppWidgetHostView view = new PendingAppWidgetHostView(this, item,
                    mIsSafeModeEnabled);
            view.updateIcon(mIconCache);
            item.hostView = view;
            item.hostView.updateAppWidget(null);
            item.hostView.setOnClickListener(this);
        }

        item.hostView.setTag(item);
        item.onBindAppWidget(this);

        workspace.addInScreen(item.hostView, item.container, item.screenId, item.cellX,
                item.cellY, item.spanX, item.spanY, false);
        if (!item.isCustomWidget()) {
            addWidgetToAutoAdvanceIfNeeded(item.hostView, appWidgetInfo);
        }

        workspace.requestLayout();

        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bound widget id="+item.appWidgetId+" in "
                    + (SystemClock.uptimeMillis()-start) + "ms");
        }
    }

    /**
     * Restores a pending widget.
     *
     * @param appWidgetId The app widget id
     * @param cellInfo The position on screen where to create the widget.
     */
    private void completeRestoreAppWidget(final int appWidgetId) {
        LauncherAppWidgetHostView view = mWorkspace.getWidgetForAppWidgetId(appWidgetId);
        if ((view == null) || !(view instanceof PendingAppWidgetHostView)) {
            Log.e(TAG, "Widget update called, when the widget no longer exists.");
            return;
        }

        LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) view.getTag();
        info.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED;

        mWorkspace.reinflateWidgetsIfNecessary();
        LauncherModel.updateItemInDatabase(this, info);
    }

    public void onPageBoundSynchronously(int page) {
        mSynchronouslyBoundPages.add(page);
    }

    /**
     * Callback saying that there aren't any more items to bind.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void finishBindingItems() {
        Runnable r = new Runnable() {
            public void run() {
                finishBindingItems();
            }
        };
        if (waitUntilResume(r)) {
            return;
        }
        if (mSavedState != null) {
            if (!mWorkspace.hasFocus()) {
                mWorkspace.getChildAt(mWorkspace.getCurrentPage()).requestFocus();
            }
            mSavedState = null;
        }

        mWorkspace.restoreInstanceStateForRemainingPages();

        setWorkspaceLoading(false);
        sendLoadingCompleteBroadcastIfNecessary();

        // If we received the result of any pending adds while the loader was running (e.g. the
        // widget configuration forced an orientation change), process them now.
        if (sPendingAddItem != null) {
            final long screenId = completeAdd(sPendingAddItem);

            // TODO: this moves the user to the page where the pending item was added. Ideally,
            // the screen would be guaranteed to exist after bind, and the page would be set through
            // the workspace restore process.
            mWorkspace.post(new Runnable() {
                @Override
                public void run() {
                    mWorkspace.snapToScreenId(screenId);
                }
            });
            sPendingAddItem = null;
        }

        InstallShortcutReceiver.disableAndFlushInstallQueue(this);

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.finishBindingItems(false);
        }

        mWorkspace.stripEmptyScreens();

        sRemoteFolderManager.bindFinished();
    }

    private void sendLoadingCompleteBroadcastIfNecessary() {
        if (!mSharedPrefs.getBoolean(FIRST_LOAD_COMPLETE, false)) {
            String permission =
                    getResources().getString(R.string.receive_first_load_broadcast_permission);
            Intent intent = new Intent(ACTION_FIRST_LOAD_COMPLETE);
            sendBroadcast(intent, permission);
            SharedPreferences.Editor editor = mSharedPrefs.edit();
            editor.putBoolean(FIRST_LOAD_COMPLETE, true);
            editor.apply();
        }
    }

    public boolean isAllAppsButtonRank(int rank) {
        if (mHotseat != null) {
            return mHotseat.isAllAppsButtonRank(rank);
        }
        return false;
    }

    private boolean canRunNewAppsAnimation() {
        long diff = System.currentTimeMillis() - mDragController.getLastGestureUpTime();
        return diff > (NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS * 1000)
                && (mClings == null || !mClings.isVisible());
    }

    private ValueAnimator createNewAppBounceAnimation(View v, int i) {
        ValueAnimator bounceAnim = LauncherAnimUtils.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat("alpha", 1f),
                PropertyValuesHolder.ofFloat("scaleX", 1f),
                PropertyValuesHolder.ofFloat("scaleY", 1f));
        bounceAnim.setDuration(InstallShortcutReceiver.NEW_SHORTCUT_BOUNCE_DURATION);
        bounceAnim.setStartDelay(i * InstallShortcutReceiver.NEW_SHORTCUT_STAGGER_DELAY);
        bounceAnim.setInterpolator(new OvershootInterpolator(BOUNCE_ANIMATION_TENSION));
        return bounceAnim;
    }

    public boolean useVerticalBarLayout() {
        return mDeviceProfile.isVerticalBarLayout();
    }

    protected Rect getSearchBarBounds() {
        return mDeviceProfile.getSearchBarBounds(Utilities.isRtl(getResources()));
    }

    public void bindSearchProviderChanged() {
        if (mSearchDropTargetBar == null) {
            return;
        }
        if (mQsb != null) {
            mSearchDropTargetBar.removeView(mQsb);
            mQsb = null;
        }
        mSearchDropTargetBar.setQsbSearchBar(getOrCreateQsbBar());
    }

    /**
     * A runnable that we can dequeue and re-enqueue when all applications are bound (to prevent
     * multiple calls to bind the same list.)
     */
    @Thunk ArrayList<AppInfo> mTmpAppsList;
    private Runnable mBindAllApplicationsRunnable = new Runnable() {
        public void run() {
            bindAllApplications(mTmpAppsList);
            mTmpAppsList = null;
        }
    };

    /**
     * Add the icons for all apps.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAllApplications(final ArrayList<AppInfo> apps) {
        if (waitUntilResume(mBindAllApplicationsRunnable, true)) {
            mTmpAppsList = apps;
            return;
        }

        if (mAppsView != null) {
            mAppsView.setApps(apps);
        }
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.bindAllApplications(apps);
        }
    }

    /**
     * A package was updated.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsUpdated(final ArrayList<AppInfo> apps) {
        Runnable r = new Runnable() {
            public void run() {
                bindAppsUpdated(apps);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if (mAppsView != null) {
            mAppsView.updateApps(apps);
        }
    }

    @Override
    public void bindWidgetsRestored(final ArrayList<LauncherAppWidgetInfo> widgets) {
        Runnable r = new Runnable() {
            public void run() {
                bindWidgetsRestored(widgets);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }
        mWorkspace.widgetsRestored(widgets);
    }

    /**
     * Some shortcuts were updated in the background.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindShortcutsChanged(final ArrayList<ShortcutInfo> updated,
            final ArrayList<ShortcutInfo> removed, final UserHandleCompat user) {
        Runnable r = new Runnable() {
            public void run() {
                bindShortcutsChanged(updated, removed, user);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if (!updated.isEmpty()) {
            mWorkspace.updateShortcuts(updated);
        }

        if (!removed.isEmpty()) {
            HashSet<ComponentName> removedComponents = new HashSet<ComponentName>();
            for (ShortcutInfo si : removed) {
                removedComponents.add(si.getTargetComponent());
            }
            mWorkspace.removeItemsByComponentName(removedComponents, user);
            // Notify the drag controller
            mDragController.onAppsRemoved(new ArrayList<String>(), removedComponents);
        }
    }

    /**
     * Update the state of a package, typically related to install state.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindRestoreItemsChange(final HashSet<ItemInfo> updates) {
        Runnable r = new Runnable() {
            public void run() {
                bindRestoreItemsChange(updates);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        mWorkspace.updateRestoreItems(updates);
    }

    /**
     * A package was uninstalled.  We take both the super set of packageNames
     * in addition to specific applications to remove, the reason being that
     * this can be called when a package is updated as well.  In that scenario,
     * we only remove specific components from the workspace, where as
     * package-removal should clear all items by package name.
     *
     * @param reason if non-zero, the icons are not permanently removed, rather marked as disabled.
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindComponentsRemoved(final ArrayList<String> packageNames,
            final ArrayList<AppInfo> appInfos, final UserHandleCompat user, final int reason) {
        Runnable r = new Runnable() {
            public void run() {
                bindComponentsRemoved(packageNames, appInfos, user, reason);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if (reason == 0) {
            HashSet<ComponentName> removedComponents = new HashSet<ComponentName>();
            for (AppInfo info : appInfos) {
                removedComponents.add(info.componentName);
            }
            if (packageNames != null && !packageNames.isEmpty()) {
                mWorkspace.removeItemsByPackageName(packageNames, user);
            }
            if (!removedComponents.isEmpty()) {
                mWorkspace.removeItemsByComponentName(removedComponents, user);
            }
            // Notify the drag controller
            mDragController.onAppsRemoved(packageNames, removedComponents);

        } else {
            mWorkspace.disableShortcutsByPackageName(packageNames, user, reason);
        }

        // Update AllApps
        if (mAppsView != null) {
            mAppsView.removeApps(appInfos);
        }
    }

    private Runnable mBindPackagesUpdatedRunnable = new Runnable() {
            public void run() {
                bindAllPackages(mWidgetsModel);
            }
        };

    @Override
    public void bindAllPackages(final WidgetsModel model) {
        if (waitUntilResume(mBindPackagesUpdatedRunnable, true)) {
            mWidgetsModel = model;
            return;
        }

        if (mWidgetsView != null && model != null) {
            mWidgetsView.addWidgets(model);
            mWidgetsModel = null;
        }
    }

    private int mapConfigurationOriActivityInfoOri(int configOri) {
        final Display d = getWindowManager().getDefaultDisplay();
        int naturalOri = Configuration.ORIENTATION_LANDSCAPE;
        switch (d.getRotation()) {
        case Surface.ROTATION_0:
        case Surface.ROTATION_180:
            // We are currently in the same basic orientation as the natural orientation
            naturalOri = configOri;
            break;
        case Surface.ROTATION_90:
        case Surface.ROTATION_270:
            // We are currently in the other basic orientation to the natural orientation
            naturalOri = (configOri == Configuration.ORIENTATION_LANDSCAPE) ?
                    Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE;
            break;
        }

        int[] oriMap = {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        };
        // Since the map starts at portrait, we need to offset if this device's natural orientation
        // is landscape.
        int indexOffset = 0;
        if (naturalOri == Configuration.ORIENTATION_LANDSCAPE) {
            indexOffset = 1;
        }
        return oriMap[(d.getRotation() + indexOffset) % 4];
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void lockScreenOrientation() {
        if (mRotationEnabled) {
            if (Utilities.ATLEAST_JB_MR2) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            } else {
                setRequestedOrientation(mapConfigurationOriActivityInfoOri(getResources()
                        .getConfiguration().orientation));
            }
        }
    }

    public void unlockScreenOrientation(boolean immediate) {
        if (mRotationEnabled) {
            if (immediate) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            } else {
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    }
                }, mRestoreScreenOrientationDelay);
            }
        }
    }

    protected boolean isLauncherPreinstalled() {
        if (mLauncherCallbacks != null) {
            return mLauncherCallbacks.isLauncherPreinstalled();
        }
        PackageManager pm = getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(getComponentName().getPackageName(), 0);
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                return true;
            } else {
                return false;
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * This method indicates whether or not we should suggest default wallpaper dimensions
     * when our wallpaper cropper was not yet used to set a wallpaper.
     */
    protected boolean overrideWallpaperDimensions() {
        if (mLauncherCallbacks != null) {
            return mLauncherCallbacks.overrideWallpaperDimensions();
        }
        return true;
    }

    /**
     * To be overridden by subclasses to indicate that there is an activity to launch
     * before showing the standard launcher experience.
     */
    protected boolean hasFirstRunActivity() {
        if (mLauncherCallbacks != null) {
            return mLauncherCallbacks.hasFirstRunActivity();
        }
        return false;
    }

    /**
     * To be overridden by subclasses to launch any first run activity
     */
    protected Intent getFirstRunActivity() {
        if (mLauncherCallbacks != null) {
            return mLauncherCallbacks.getFirstRunActivity();
        }
        return null;
    }

    private boolean shouldRunFirstRunActivity() {
        return !ActivityManager.isRunningInTestHarness() &&
                !mSharedPrefs.getBoolean(FIRST_RUN_ACTIVITY_DISPLAYED, false);
    }

    protected boolean hasRunFirstRunActivity() {
        return mSharedPrefs.getBoolean(FIRST_RUN_ACTIVITY_DISPLAYED, false);
    }

    public boolean showFirstRunActivity() {
        if (shouldRunFirstRunActivity() &&
                hasFirstRunActivity()) {
            Intent firstRunIntent = getFirstRunActivity();
            if (firstRunIntent != null) {
                startActivity(firstRunIntent);
                markFirstRunActivityShown();
                return true;
            }
        }
        return false;
    }

    private void markFirstRunActivityShown() {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putBoolean(FIRST_RUN_ACTIVITY_DISPLAYED, true);
        editor.apply();
    }

    /**
     * To be overridden by subclasses to indicate that there is an in-activity full-screen intro
     * screen that must be displayed and dismissed.
     */
    protected boolean hasDismissableIntroScreen() {
        if (mLauncherCallbacks != null) {
            return mLauncherCallbacks.hasDismissableIntroScreen();
        }
        return false;
    }

    /**
     * Full screen intro screen to be shown and dismissed before the launcher can be used.
     */
    protected View getIntroScreen() {
        if (mLauncherCallbacks != null) {
            return mLauncherCallbacks.getIntroScreen();
        }
        return null;
    }

    /**
     * To be overriden by subclasses to indicate whether the in-activity intro screen has been
     * dismissed. This method is ignored if #hasDismissableIntroScreen returns false.
     */
    private boolean shouldShowIntroScreen() {
        return hasDismissableIntroScreen() &&
                !mSharedPrefs.getBoolean(INTRO_SCREEN_DISMISSED, false);
    }

    protected void showIntroScreen() {
        View introScreen = getIntroScreen();
        changeWallpaperVisiblity(false);
        if (introScreen != null) {
            mDragLayer.showOverlayView(introScreen);
        }
        if (mLauncherOverlayContainer != null) {
            mLauncherOverlayContainer.setVisibility(View.INVISIBLE);
        }
    }

    public void dismissIntroScreen() {
        markIntroScreenDismissed();
        if (showFirstRunActivity()) {
            // We delay hiding the intro view until the first run activity is showing. This
            // avoids a blip.
            mWorkspace.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mDragLayer.dismissOverlayView();
                    if (mLauncherOverlayContainer != null) {
                        mLauncherOverlayContainer.setVisibility(View.VISIBLE);
                    }
                    showFirstRunClings();
                }
            }, ACTIVITY_START_DELAY);
        } else {
            mDragLayer.dismissOverlayView();
            if (mLauncherOverlayContainer != null) {
                mLauncherOverlayContainer.setVisibility(View.VISIBLE);
            }
            showFirstRunClings();
        }
        changeWallpaperVisiblity(true);
    }

    private void markIntroScreenDismissed() {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putBoolean(INTRO_SCREEN_DISMISSED, true);
        editor.apply();
    }

    @Thunk void showFirstRunClings() {
        // The two first run cling paths are mutually exclusive, if the launcher is preinstalled
        // on the device, then we always show the first run cling experience (or if there is no
        // launcher2). Otherwise, we prompt the user upon started for migration
        LauncherClings launcherClings = new LauncherClings(this);
        if (launcherClings.shouldShowFirstRunOrMigrationClings()) {
            mClings = launcherClings;
            if (mModel.canMigrateFromOldLauncherDb(this)) {
                launcherClings.showMigrationCling();
            } else {
                launcherClings.showLongPressCling(true);
            }
        }
    }

    void showWorkspaceSearchAndHotseat() {
        if (mWorkspace != null) mWorkspace.setAlpha(1f);
        if (mHotseat != null) mHotseat.setAlpha(1f);
        if (mPageIndicators != null) mPageIndicators.setAlpha(1f);
        if (mSearchDropTargetBar != null) mSearchDropTargetBar.animateToState(
                SearchDropTargetBar.State.SEARCH_BAR, 0);
    }

    void hideWorkspaceSearchAndHotseat() {
        if (mWorkspace != null) mWorkspace.setAlpha(0f);
        if (mHotseat != null) mHotseat.setAlpha(0f);
        if (mPageIndicators != null) mPageIndicators.setAlpha(0f);
        if (mSearchDropTargetBar != null) mSearchDropTargetBar.animateToState(
                SearchDropTargetBar.State.INVISIBLE, 0);
    }

    // TODO: These method should be a part of LauncherSearchCallback
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ItemInfo createAppDragInfo(Intent appLaunchIntent) {
        // Called from search suggestion
        UserHandleCompat user = null;
        if (Utilities.ATLEAST_LOLLIPOP) {
            UserHandle userHandle = appLaunchIntent.getParcelableExtra(Intent.EXTRA_USER);
            if (userHandle != null) {
                user = UserHandleCompat.fromUser(userHandle);
            }
        }
        return createAppDragInfo(appLaunchIntent, user);
    }

    // TODO: This method should be a part of LauncherSearchCallback
    public ItemInfo createAppDragInfo(Intent intent, UserHandleCompat user) {
        if (user == null) {
            user = UserHandleCompat.myUserHandle();
        }

        // Called from search suggestion, add the profile extra to the intent to ensure that we
        // can launch it correctly
        LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(this);
        LauncherActivityInfoCompat activityInfo = launcherApps.resolveActivity(intent, user);
        if (activityInfo == null) {
            return null;
        }
        return new AppInfo(this, activityInfo, user, mIconCache);
    }

    // TODO: This method should be a part of LauncherSearchCallback
    public ItemInfo createShortcutDragInfo(Intent shortcutIntent, CharSequence caption,
            Bitmap icon) {
        return new ShortcutInfo(shortcutIntent, caption, caption, icon,
                UserHandleCompat.myUserHandle());
    }

    // TODO: This method should be a part of LauncherSearchCallback
    public void startDrag(View dragView, ItemInfo dragInfo, DragSource source) {
        dragView.setTag(dragInfo);
        mWorkspace.onExternalDragStartedWithItem(dragView);
        mWorkspace.beginExternalDragShared(dragView, source);
    }

    protected void moveWorkspaceToDefaultScreen() {
        mWorkspace.moveToDefaultScreen(false);
    }

    @Override
    public void onPageSwitch(View newPage, int newPageIndex) {
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onPageSwitch(newPage, newPageIndex);
        }
    }

    /**
     * Returns a FastBitmapDrawable with the icon, accurately sized.
     */
    public FastBitmapDrawable createIconDrawable(Bitmap icon) {
        FastBitmapDrawable d = new FastBitmapDrawable(icon);
        d.setFilterBitmap(true);
        resizeIconDrawable(d);
        return d;
    }

    /**
     * Resizes an icon drawable to the correct icon size.
     */
    public void resizeIconDrawable(Drawable icon) {
        icon.setBounds(0, 0, mDeviceProfile.iconSizePx, mDeviceProfile.iconSizePx);
    }

    /**
     * Prints out out state for debugging.
     */
    public void dumpState() {
        Log.d(TAG, "BEGIN launcher3 dump state for launcher " + this);
        Log.d(TAG, "mSavedState=" + mSavedState);
        Log.d(TAG, "mWorkspaceLoading=" + mWorkspaceLoading);
        Log.d(TAG, "mRestoring=" + mRestoring);
        Log.d(TAG, "mWaitingForResult=" + mWaitingForResult);
        Log.d(TAG, "mSavedInstanceState=" + mSavedInstanceState);
        Log.d(TAG, "sFolders.size=" + sFolders.size());
        mModel.dumpState();
        // TODO(hyunyoungs): add mWidgetsView.dumpState(); or mWidgetsModel.dumpState();

        Log.d(TAG, "END launcher3 dump state");
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        synchronized (sDumpLogs) {
            writer.println(" ");
            writer.println("Debug logs: ");
            for (int i = 0; i < sDumpLogs.size(); i++) {
                writer.println("  " + sDumpLogs.get(i));
            }
        }
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.dump(prefix, fd, writer, args);
        }
    }

    public static void dumpDebugLogsToConsole() {
        if (DEBUG_DUMP_LOG) {
            synchronized (sDumpLogs) {
                Log.d(TAG, "");
                Log.d(TAG, "*********************");
                Log.d(TAG, "Launcher debug logs: ");
                for (int i = 0; i < sDumpLogs.size(); i++) {
                    Log.d(TAG, "  " + sDumpLogs.get(i));
                }
                Log.d(TAG, "*********************");
                Log.d(TAG, "");
            }
        }
    }

    public static void addDumpLog(String tag, String log, boolean debugLog) {
        addDumpLog(tag, log, null, debugLog);
    }

    public static void addDumpLog(String tag, String log, Exception e, boolean debugLog) {
        if (debugLog) {
            if (e != null) {
                Log.d(tag, log, e);
            } else {
                Log.d(tag, log);
            }
        }
        if (DEBUG_DUMP_LOG) {
            sDateStamp.setTime(System.currentTimeMillis());
            synchronized (sDumpLogs) {
                sDumpLogs.add(sDateFormat.format(sDateStamp) + ": " + tag + ", " + log
                    + (e == null ? "" : (", Exception: " + e)));
            }
        }
    }

    public static CustomAppWidget getCustomAppWidget(String name) {
        return sCustomAppWidgets.get(name);
    }

    public static HashMap<String, CustomAppWidget> getCustomAppWidgets() {
        return sCustomAppWidgets;
    }

    public void dumpLogsToLocalData() {
        if (DEBUG_DUMP_LOG) {
            new AsyncTask<Void, Void, Void>() {
                public Void doInBackground(Void ... args) {
                    boolean success = false;
                    sDateStamp.setTime(sRunStart);
                    String FILENAME = sDateStamp.getMonth() + "-"
                            + sDateStamp.getDay() + "_"
                            + sDateStamp.getHours() + "-"
                            + sDateStamp.getMinutes() + "_"
                            + sDateStamp.getSeconds() + ".txt";

                    FileOutputStream fos = null;
                    File outFile = null;
                    try {
                        outFile = new File(getFilesDir(), FILENAME);
                        outFile.createNewFile();
                        fos = new FileOutputStream(outFile);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (fos != null) {
                        PrintWriter writer = new PrintWriter(fos);

                        writer.println(" ");
                        writer.println("Debug logs: ");
                        synchronized (sDumpLogs) {
                            for (int i = 0; i < sDumpLogs.size(); i++) {
                                writer.println("  " + sDumpLogs.get(i));
                            }
                        }
                        writer.close();
                    }
                    try {
                        if (fos != null) {
                            fos.close();
                            success = true;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
        }
    }

    private void setupSearchBar(Context context) {
        mIsDrawerSearchBarEnabled = SettingsProvider.getBoolean(context,
                SettingsProvider.SETTINGS_UI_DRAWER_SEARCH,
                R.bool.preferences_interface_homescreen_search_default);

        if (mIsDrawerSearchBarEnabled) {
            if (mLauncherCallbacks != null
                    && mLauncherCallbacks.getAllAppsSearchBarController() != null) {
                mAppsView
                        .setSearchBarController(mLauncherCallbacks.getAllAppsSearchBarController());
            } else {
                mAppsView.setSearchBarController(mAppsView.newDefaultAppSearchController());
            }
        } else {
            mAppsView.setSearchBarController(null);
            mAppsView.setHasSearchBar(mIsDrawerSearchBarEnabled);
            mAppsView.setSearchBarContainerViewVisibility(
                    mIsDrawerSearchBarEnabled ? View.VISIBLE : View.GONE);
            mAppsView.updateBackgroundAndPaddings();
        }
    }

    class SettingsPanelSlideListener extends VerticalSlidingPanel.SimplePanelSlideListener {
        ImageView mAnimatedArrow;

        public SettingsPanelSlideListener() {
            super();
            mAnimatedArrow = (ImageView) mOverviewPanel.findViewById(R.id.settings_drag_arrow);
        }

        @Override
        public void onPanelCollapsed(View panel) {
            mAnimatedArrow.setBackgroundResource(R.drawable.transition_arrow_reverse);

            AnimationDrawable frameAnimation = (AnimationDrawable) mAnimatedArrow.getBackground();
            frameAnimation.start();
        }

        @Override
        public void onPanelExpanded(View panel) {
            mAnimatedArrow.setBackgroundResource(R.drawable.transition_arrow);

            AnimationDrawable frameAnimation = (AnimationDrawable) mAnimatedArrow.getBackground();
            frameAnimation.start();

            LauncherApplication.getLauncherStats().sendSettingsOpenedEvent(
                    LauncherStats.ORIGIN_TREB_LONGPRESS);
        }

        @Override
        public void onPanelShown(View panel) {
            mAnimatedArrow.setBackgroundResource(R.drawable.transition_arrow_reverse);

            AnimationDrawable frameAnimation = (AnimationDrawable) mAnimatedArrow.getBackground();
            frameAnimation.start();
        }
    }
}

interface DebugIntents {
    static final String DELETE_DATABASE = "com.android.launcher3.action.DELETE_DATABASE";
    static final String MIGRATE_DATABASE = "com.android.launcher3.action.MIGRATE_DATABASE";
}
