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

import static android.content.pm.ActivityInfo.CONFIG_ORIENTATION;
import static android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_ICON_SURFACE;
import static com.android.launcher3.AbstractFloatingView.TYPE_REBIND_SAFE;
import static com.android.launcher3.AbstractFloatingView.TYPE_SNACKBAR;
import static com.android.launcher3.InstallShortcutReceiver.FLAG_DRAG_AND_DROP;
import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.FLAG_CLOSE_POPUPS;
import static com.android.launcher3.LauncherState.FLAG_MULTI_PAGE;
import static com.android.launcher3.LauncherState.FLAG_NON_INTERACTIVE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.NO_OFFSET;
import static com.android.launcher3.LauncherState.NO_SCALE;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_PEEK;
import static com.android.launcher3.LauncherState.SPRING_LOADED;
import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.dragndrop.DragLayer.ALPHA_INDEX_LAUNCHER_LOAD;
import static com.android.launcher3.logging.LoggerUtils.newContainerTarget;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_BACKGROUND;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ONRESUME;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ONSTOP;
import static com.android.launcher3.logging.StatsLogManager.containerTypeToAtomState;
import static com.android.launcher3.popup.SystemShortcut.APP_INFO;
import static com.android.launcher3.popup.SystemShortcut.INSTALL;
import static com.android.launcher3.popup.SystemShortcut.WIDGETS;
import static com.android.launcher3.states.RotationHelper.REQUEST_LOCK;
import static com.android.launcher3.states.RotationHelper.REQUEST_NONE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Parcelable;
import android.os.Process;
import android.os.StrictMode;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.KeyboardShortcutInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderGridOrganizer;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.keyboard.CustomActionsPopup;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.lineage.LineageUtils;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.model.AppLaunchTracker;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.PromiseAppInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.pm.PinRequestHelper;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.qsb.QsbContainerView;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.states.RotationHelper;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.touch.AllAppsSwipeController;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ActivityResultInfo;
import com.android.launcher3.util.ActivityTracker;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.PendingRequestArgs;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.UiThreadHelper;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.FloatingSurfaceView;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.PendingAppWidgetHostView;
import com.android.launcher3.widget.WidgetAddFlowHandler;
import com.android.launcher3.widget.WidgetHostViewLoader;
import com.android.launcher3.widget.WidgetListRowEntry;
import com.android.launcher3.widget.WidgetManagerHelper;
import com.android.launcher3.widget.WidgetsFullSheet;
import com.android.launcher3.widget.custom.CustomWidgetManager;
import com.android.systemui.plugins.OverlayPlugin;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.shared.LauncherExterns;
import com.android.systemui.plugins.shared.LauncherOverlayManager;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlay;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayCallbacks;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Default launcher application.
 */
public class Launcher extends StatefulActivity<LauncherState> implements LauncherExterns,
        Callbacks, InvariantDeviceProfile.OnIDPChangeListener, PluginListener<OverlayPlugin> {
    public static final String TAG = "Launcher";

    public static final ActivityTracker<Launcher> ACTIVITY_TRACKER = new ActivityTracker<>();

    static final boolean LOGD = false;

    static final boolean DEBUG_STRICT_MODE = false;

    private static final int REQUEST_CREATE_SHORTCUT = 1;
    private static final int REQUEST_CREATE_APPWIDGET = 5;

    private static final int REQUEST_PICK_APPWIDGET = 9;

    private static final int REQUEST_BIND_APPWIDGET = 11;
    public static final int REQUEST_BIND_PENDING_APPWIDGET = 12;
    public static final int REQUEST_RECONFIGURE_APPWIDGET = 13;

    private static final int REQUEST_PERMISSION_CALL_PHONE = 14;

    private static final float BOUNCE_ANIMATION_TENSION = 1.3f;

    /**
     * IntentStarter uses request codes starting with this. This must be greater than all activity
     * request codes used internally.
     */
    protected static final int REQUEST_LAST = 100;

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: int
    private static final String RUNTIME_STATE = "launcher.state";
    // Type: PendingRequestArgs
    private static final String RUNTIME_STATE_PENDING_REQUEST_ARGS = "launcher.request_args";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_REQUEST_CODE = "launcher.request_code";
    // Type: ActivityResultInfo
    private static final String RUNTIME_STATE_PENDING_ACTIVITY_RESULT = "launcher.activity_result";
    // Type: SparseArray<Parcelable>
    private static final String RUNTIME_STATE_WIDGET_PANEL = "launcher.widget_panel";

    public static final String ON_CREATE_EVT = "Launcher.onCreate";
    public static final String ON_START_EVT = "Launcher.onStart";
    public static final String ON_RESUME_EVT = "Launcher.onResume";
    public static final String ON_NEW_INTENT_EVT = "Launcher.onNewIntent";

    private StateManager<LauncherState> mStateManager;

    private static final int ON_ACTIVITY_RESULT_ANIMATION_DELAY = 500;

    // How long to wait before the new-shortcut animation automatically pans the workspace
    @VisibleForTesting public static final int NEW_APPS_PAGE_MOVE_DELAY = 500;
    private static final int NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS = 5;
    @Thunk @VisibleForTesting public static final int NEW_APPS_ANIMATION_DELAY = 500;

    private static final int APPS_VIEW_ALPHA_CHANNEL_INDEX = 1;
    private static final int SCRIM_VIEW_ALPHA_CHANNEL_INDEX = 0;

    private LauncherAppTransitionManager mAppTransitionManager;
    private Configuration mOldConfig;

    @Thunk
    Workspace mWorkspace;
    @Thunk
    DragLayer mDragLayer;
    private DragController mDragController;

    private WidgetManagerHelper mAppWidgetManager;
    private LauncherAppWidgetHost mAppWidgetHost;

    private final int[] mTmpAddItemCellCoordinates = new int[2];

    @Thunk
    Hotseat mHotseat;

    private DropTargetBar mDropTargetBar;

    // Main container view for the all apps screen.
    @Thunk
    AllAppsContainerView mAppsView;
    AllAppsTransitionController mAllAppsController;

    // Scrim view for the all apps and overview state.
    @Thunk
    ScrimView mScrimView;

    // UI and state for the overview panel
    private View mOverviewPanel;

    @Thunk
    boolean mWorkspaceLoading = true;

    private ArrayList<OnResumeCallback> mOnResumeCallbacks = new ArrayList<>();

    // Used to notify when an activity launch has been deferred because launcher is not yet resumed
    // TODO: See if we can remove this later
    private Runnable mOnDeferredActivityLaunchCallback;

    private ViewOnDrawExecutor mPendingExecutor;

    private LauncherModel mModel;
    private ModelWriter mModelWriter;
    private IconCache mIconCache;
    private LauncherAccessibilityDelegate mAccessibilityDelegate;

    private PopupDataProvider mPopupDataProvider;

    private int mSynchronouslyBoundPage = PagedView.INVALID_PAGE;
    private int mPageToBindSynchronously = PagedView.INVALID_PAGE;

    // We only want to get the SharedPreferences once since it does an FS stat each time we get
    // it from the context.
    private SharedPreferences mSharedPrefs;
    private OnboardingPrefs mOnboardingPrefs;

    // Activity result which needs to be processed after workspace has loaded.
    private ActivityResultInfo mPendingActivityResult;
    /**
     * Holds extra information required to handle a result from an external call, like
     * {@link #startActivityForResult(Intent, int)} or {@link #requestPermissions(String[], int)}
     */
    private PendingRequestArgs mPendingRequestArgs;
    // Request id for any pending activity result
    protected int mPendingActivityRequestCode = -1;

    private ViewGroupFocusHelper mFocusHandler;

    private RotationHelper mRotationHelper;

    private float mCurrentAssistantVisibility = 0f;

    protected LauncherOverlayManager mOverlayManager;
    // If true, overlay callbacks are deferred
    private boolean mDeferOverlayCallbacks;
    private final Runnable mDeferredOverlayCallbacks = this::checkIfOverlayStillDeferred;

    private long mLastTouchUpTime = -1;
    private boolean mTouchInProgress;

    private SafeCloseable mUserChangedCallbackCloseable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Object traceToken = TraceHelper.INSTANCE.beginSection(ON_CREATE_EVT,
                TraceHelper.FLAG_UI_EVENT);
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

        super.onCreate(savedInstanceState);

        LauncherAppState app = LauncherAppState.getInstance(this);
        mOldConfig = new Configuration(getResources().getConfiguration());
        mModel = app.getModel();

        mRotationHelper = new RotationHelper(this);
        InvariantDeviceProfile idp = app.getInvariantDeviceProfile();
        initDeviceProfile(idp);
        idp.addOnChangeListener(this);
        mSharedPrefs = Utilities.getPrefs(this);
        mIconCache = app.getIconCache();
        mAccessibilityDelegate = new LauncherAccessibilityDelegate(this);

        mDragController = new DragController(this);
        mAllAppsController = new AllAppsTransitionController(this);
        mStateManager = new StateManager<>(this, NORMAL);

        mOnboardingPrefs = createOnboardingPrefs(mSharedPrefs);

        mAppWidgetManager = new WidgetManagerHelper(this);
        mAppWidgetHost = new LauncherAppWidgetHost(this,
                appWidgetId -> getWorkspace().removeWidget(appWidgetId));
        mAppWidgetHost.startListening();

        inflateRootView(R.layout.launcher);
        setupViews();
        mPopupDataProvider = new PopupDataProvider(this::updateNotificationDots);

        mAppTransitionManager = LauncherAppTransitionManager.newInstance(this);
        mAppTransitionManager.registerRemoteAnimations();

        boolean internalStateHandled = ACTIVITY_TRACKER.handleCreate(this);
        if (internalStateHandled) {
            if (savedInstanceState != null) {
                // InternalStateHandler has already set the appropriate state.
                // We dont need to do anything.
                savedInstanceState.remove(RUNTIME_STATE);
            }
        }
        restoreState(savedInstanceState);
        mStateManager.reapplyState();

        // We only load the page synchronously if the user rotates (or triggers a
        // configuration change) while launcher is in the foreground
        int currentScreen = PagedView.INVALID_PAGE;
        if (savedInstanceState != null) {
            currentScreen = savedInstanceState.getInt(RUNTIME_STATE_CURRENT_SCREEN, currentScreen);
        }
        mPageToBindSynchronously = currentScreen;

        if (!mModel.addCallbacksAndLoad(this)) {
            if (!internalStateHandled) {
                // If we are not binding synchronously, show a fade in animation when
                // the first page bind completes.
                mDragLayer.getAlphaProperty(ALPHA_INDEX_LAUNCHER_LOAD).setValue(0);
            }
        }

        // For handling default keys
        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        setContentView(getRootView());
        getRootView().dispatchInsets();

        // Listen for broadcasts
        registerReceiver(mScreenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        getSystemUiController().updateUiState(SystemUiController.UI_STATE_BASE_WINDOW,
                Themes.getAttrBoolean(this, R.attr.isWorkspaceDarkText));

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onCreate(savedInstanceState);
        }
        mOverlayManager = getDefaultOverlay();
        PluginManagerWrapper.INSTANCE.get(this).addPluginListener(this,
                OverlayPlugin.class, false /* allowedMultiple */);

        mRotationHelper.initialize();

        mStateManager.addStateListener(new StateListener<LauncherState>() {

            @Override
            public void onStateTransitionComplete(LauncherState finalState) {
                float alpha = 1f - mCurrentAssistantVisibility;
                if (finalState == NORMAL) {
                    mAppsView.getAlphaProperty(APPS_VIEW_ALPHA_CHANNEL_INDEX).setValue(alpha);
                } else if (finalState == OVERVIEW || finalState == OVERVIEW_PEEK) {
                    mAppsView.getAlphaProperty(APPS_VIEW_ALPHA_CHANNEL_INDEX).setValue(alpha);
                    mScrimView.getAlphaProperty(SCRIM_VIEW_ALPHA_CHANNEL_INDEX).setValue(alpha);
                } else {
                    mAppsView.getAlphaProperty(APPS_VIEW_ALPHA_CHANNEL_INDEX).setValue(1f);
                    mScrimView.getAlphaProperty(SCRIM_VIEW_ALPHA_CHANNEL_INDEX).setValue(1f);
                }
            }
        });

        TraceHelper.INSTANCE.endSection(traceToken);

        mUserChangedCallbackCloseable = UserCache.INSTANCE.get(this).addUserChangeListener(
                () -> getStateManager().goToState(NORMAL));
    }

    protected LauncherOverlayManager getDefaultOverlay() {
        return new LauncherOverlayManager() { };
    }

    protected OnboardingPrefs createOnboardingPrefs(SharedPreferences sharedPrefs) {
        return new OnboardingPrefs<>(this, sharedPrefs);
    }

    public OnboardingPrefs getOnboardingPrefs() {
        return mOnboardingPrefs;
    }

    @Override
    public void onPluginConnected(OverlayPlugin overlayManager, Context context) {
        switchOverlay(() -> overlayManager.createOverlayManager(this, this));
    }

    @Override
    public void onPluginDisconnected(OverlayPlugin plugin) {
        switchOverlay(this::getDefaultOverlay);
    }

    private void switchOverlay(Supplier<LauncherOverlayManager> overlaySupplier) {
        if (mOverlayManager != null) {
            mOverlayManager.onActivityDestroyed(this);
        }
        mOverlayManager = overlaySupplier.get();
        if (getRootView().isAttachedToWindow()) {
            mOverlayManager.onAttachedToWindow();
        }
        mDeferOverlayCallbacks = true;
        checkIfOverlayStillDeferred();
    }

    @Override
    protected void dispatchDeviceProfileChanged() {
        super.dispatchDeviceProfileChanged();
        mOverlayManager.onDeviceProvideChanged();
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        mRotationHelper.setCurrentTransitionRequest(REQUEST_NONE);
        AbstractFloatingView.closeOpenViews(this, false, TYPE_ICON_SURFACE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        int diff = newConfig.diff(mOldConfig);

        if ((diff & (CONFIG_ORIENTATION | CONFIG_SCREEN_SIZE)) != 0) {
            onIdpChanged(mDeviceProfile.inv);
        }

        mOldConfig.setTo(newConfig);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onIdpChanged(int changeFlags, InvariantDeviceProfile idp) {
        onIdpChanged(idp);
    }

    private void onIdpChanged(InvariantDeviceProfile idp) {
        mUserEventDispatcher = null;

        initDeviceProfile(idp);
        dispatchDeviceProfileChanged();
        reapplyUi();
        mDragLayer.recreateControllers();

        // Calling onSaveInstanceState ensures that static cache used by listWidgets is
        // initialized properly.
        onSaveInstanceState(new Bundle());
        mModel.rebindCallbacks();
    }

    public void onAssistantVisibilityChanged(float visibility) {
        mCurrentAssistantVisibility = visibility;
        float alpha = 1f - visibility;
        LauncherState state = mStateManager.getState();
        if (state == NORMAL) {
            mAppsView.getAlphaProperty(APPS_VIEW_ALPHA_CHANNEL_INDEX).setValue(alpha);
        } else if (state == OVERVIEW || state == OVERVIEW_PEEK) {
            mAppsView.getAlphaProperty(APPS_VIEW_ALPHA_CHANNEL_INDEX).setValue(alpha);
            mScrimView.getAlphaProperty(SCRIM_VIEW_ALPHA_CHANNEL_INDEX).setValue(alpha);
        }
    }

    private void initDeviceProfile(InvariantDeviceProfile idp) {
        // Load configuration-specific DeviceProfile
        mDeviceProfile = idp.getDeviceProfile(this);
        if (isInMultiWindowMode()) {
            mDeviceProfile = mDeviceProfile.getMultiWindowProfile(
                    this, getMultiWindowDisplaySize());
        }

        onDeviceProfileInitiated();
        mModelWriter = mModel.getWriter(getDeviceProfile().isVerticalBarLayout(), true);
    }

    public RotationHelper getRotationHelper() {
        return mRotationHelper;
    }

    public ViewGroupFocusHelper getFocusHandler() {
        return mFocusHandler;
    }

    @Override
    public StateManager<LauncherState> getStateManager() {
        return mStateManager;
    }

    private LauncherCallbacks mLauncherCallbacks;

    /**
     * Call this after onCreate to set or clear overlay.
     */
    @Override
    public void setLauncherOverlay(LauncherOverlay overlay) {
        if (overlay != null) {
            overlay.setOverlayCallbacks(new LauncherOverlayCallbacksImpl());
        }
        mWorkspace.setLauncherOverlay(overlay);
    }

    @Override
    public void runOnOverlayHidden(Runnable runnable) {
        getWorkspace().runOnOverlayHidden(runnable);
    }

    public boolean setLauncherCallbacks(LauncherCallbacks callbacks) {
        mLauncherCallbacks = callbacks;
        return true;
    }

    public boolean isDraggingEnabled() {
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        return !isWorkspaceLoading();
    }

    public PopupDataProvider getPopupDataProvider() {
        return mPopupDataProvider;
    }

    @Override
    public DotInfo getDotInfoForItem(ItemInfo info) {
        return mPopupDataProvider.getDotInfoForItem(info);
    }

    @Override
    public void invalidateParent(ItemInfo info) {
        if (info.container >= 0) {
            View folderIcon = getWorkspace().getHomescreenIconByItemId(info.container);
            if (folderIcon instanceof FolderIcon && folderIcon.getTag() instanceof FolderInfo) {
                if (new FolderGridOrganizer(getDeviceProfile().inv)
                        .setFolderInfo((FolderInfo) folderIcon.getTag())
                        .isItemInPreview(info.rank)) {
                    folderIcon.invalidate();
                }
            }
        }
    }

    /**
     * Returns whether we should delay spring loaded mode -- for shortcuts and widgets that have
     * a configuration step, this allows the proper animations to run after other transitions.
     */
    private int completeAdd(
            int requestCode, Intent intent, int appWidgetId, PendingRequestArgs info) {
        int screenId = info.screenId;
        if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            // When the screen id represents an actual screen (as opposed to a rank) we make sure
            // that the drop page actually exists.
            screenId = ensurePendingDropLayoutExists(info.screenId);
        }

        switch (requestCode) {
            case REQUEST_CREATE_SHORTCUT:
                completeAddShortcut(intent, info.container, screenId, info.cellX, info.cellY, info);
                announceForAccessibility(R.string.item_added_to_workspace);
                break;
            case REQUEST_CREATE_APPWIDGET:
                completeAddAppWidget(appWidgetId, info, null, null);
                break;
            case REQUEST_RECONFIGURE_APPWIDGET:
                completeRestoreAppWidget(appWidgetId, LauncherAppWidgetInfo.RESTORE_COMPLETED);
                break;
            case REQUEST_BIND_PENDING_APPWIDGET: {
                int widgetId = appWidgetId;
                LauncherAppWidgetInfo widgetInfo =
                        completeRestoreAppWidget(widgetId, LauncherAppWidgetInfo.FLAG_UI_NOT_READY);
                if (widgetInfo != null) {
                    // Since the view was just bound, also launch the configure activity if needed
                    LauncherAppWidgetProviderInfo provider = mAppWidgetManager
                            .getLauncherAppWidgetInfo(widgetId);
                    if (provider != null) {
                        new WidgetAddFlowHandler(provider)
                                .startConfigActivity(this, widgetInfo,
                                        REQUEST_RECONFIGURE_APPWIDGET);
                    }
                }
                break;
            }
        }
        return screenId;
    }

    private void handleActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        if (isWorkspaceLoading()) {
            // process the result once the workspace has loaded.
            mPendingActivityResult = new ActivityResultInfo(requestCode, resultCode, data);
            return;
        }
        mPendingActivityResult = null;

        // Reset the startActivity waiting flag
        final PendingRequestArgs requestArgs = mPendingRequestArgs;
        setWaitingForResult(null);
        if (requestArgs == null) {
            return;
        }

        final int pendingAddWidgetId = requestArgs.getWidgetId();

        Runnable exitSpringLoaded = new Runnable() {
            @Override
            public void run() {
                mStateManager.goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
            }
        };

        if (requestCode == REQUEST_BIND_APPWIDGET) {
            // This is called only if the user did not previously have permissions to bind widgets
            final int appWidgetId = data != null ?
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
            if (resultCode == RESULT_CANCELED) {
                completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId, requestArgs);
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false, exitSpringLoaded);
            } else if (resultCode == RESULT_OK) {
                addAppWidgetImpl(
                        appWidgetId, requestArgs, null,
                        requestArgs.getWidgetHandler(),
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY);
            }
            return;
        }

        boolean isWidgetDrop = (requestCode == REQUEST_PICK_APPWIDGET ||
                requestCode == REQUEST_CREATE_APPWIDGET);

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
                completeTwoStageWidgetDrop(result, appWidgetId, requestArgs);
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false,
                        () -> getStateManager().goToState(NORMAL));
            } else {
                if (requestArgs.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                    // When the screen id represents an actual screen (as opposed to a rank)
                    // we make sure that the drop page actually exists.
                    requestArgs.screenId =
                            ensurePendingDropLayoutExists(requestArgs.screenId);
                }
                final CellLayout dropLayout =
                        mWorkspace.getScreenWithId(requestArgs.screenId);

                dropLayout.setDropPending(true);
                final Runnable onComplete = new Runnable() {
                    @Override
                    public void run() {
                        completeTwoStageWidgetDrop(resultCode, appWidgetId, requestArgs);
                        dropLayout.setDropPending(false);
                    }
                };
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false, onComplete);
            }
            return;
        }

        if (requestCode == REQUEST_RECONFIGURE_APPWIDGET
                || requestCode == REQUEST_BIND_PENDING_APPWIDGET) {
            if (resultCode == RESULT_OK) {
                // Update the widget view.
                completeAdd(requestCode, data, pendingAddWidgetId, requestArgs);
            }
            // Leave the widget in the pending state if the user canceled the configure.
            return;
        }

        if (requestCode == REQUEST_CREATE_SHORTCUT) {
            // Handle custom shortcuts created using ACTION_CREATE_SHORTCUT.
            if (resultCode == RESULT_OK && requestArgs.container != ItemInfo.NO_ID) {
                completeAdd(requestCode, data, -1, requestArgs);
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false, exitSpringLoaded);

            } else if (resultCode == RESULT_CANCELED) {
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false, exitSpringLoaded);
            }
        }

        mDragLayer.clearAnimatedView();
    }

    @Override
    public void onActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        mPendingActivityRequestCode = -1;
        handleActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        PendingRequestArgs pendingArgs = mPendingRequestArgs;
        if (requestCode == REQUEST_PERMISSION_CALL_PHONE && pendingArgs != null
                && pendingArgs.getRequestCode() == REQUEST_PERMISSION_CALL_PHONE) {
            setWaitingForResult(null);

            View v = null;
            CellLayout layout = getCellLayout(pendingArgs.container, pendingArgs.screenId);
            if (layout != null) {
                v = layout.getChildAt(pendingArgs.cellX, pendingArgs.cellY);
            }
            Intent intent = pendingArgs.getPendingIntent();

            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivitySafely(v, intent, null, null);
            } else {
                // TODO: Show a snack bar with link to settings
                Toast.makeText(this, getString(R.string.msg_no_phone_permission,
                        getString(R.string.derived_app_name)), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Check to see if a given screen id exists. If not, create it at the end, return the new id.
     *
     * @param screenId the screen id to check
     * @return the new screen, or screenId if it exists
     */
    private int ensurePendingDropLayoutExists(int screenId) {
        CellLayout dropLayout = mWorkspace.getScreenWithId(screenId);
        if (dropLayout == null) {
            // it's possible that the add screen was removed because it was
            // empty and a re-bind occurred
            mWorkspace.addExtraEmptyScreen();
            return mWorkspace.commitExtraEmptyScreen();
        } else {
            return screenId;
        }
    }

    @Thunk
    void completeTwoStageWidgetDrop(
            final int resultCode, final int appWidgetId, final PendingRequestArgs requestArgs) {
        CellLayout cellLayout = mWorkspace.getScreenWithId(requestArgs.screenId);
        Runnable onCompleteRunnable = null;
        int animationType = 0;

        AppWidgetHostView boundWidget = null;
        if (resultCode == RESULT_OK) {
            animationType = Workspace.COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION;
            final AppWidgetHostView layout = mAppWidgetHost.createView(this, appWidgetId,
                    requestArgs.getWidgetHandler().getProviderInfo(this));
            boundWidget = layout;
            onCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    completeAddAppWidget(appWidgetId, requestArgs, layout, null);
                    mStateManager.goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
                }
            };
        } else if (resultCode == RESULT_CANCELED) {
            mAppWidgetHost.deleteAppWidgetId(appWidgetId);
            animationType = Workspace.CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION;
        }
        if (mDragLayer.getAnimatedView() != null) {
            mWorkspace.animateWidgetDrop(requestArgs, cellLayout,
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
        if (mDeferOverlayCallbacks) {
            checkIfOverlayStillDeferred();
        } else {
            mOverlayManager.onActivityStopped(this);
        }

        logStopAndResume(Action.Command.STOP);
        mAppWidgetHost.setListenIfResumed(false);
        NotificationListener.removeNotificationsChangedListener();
    }

    @Override
    protected void onStart() {
        Object traceToken = TraceHelper.INSTANCE.beginSection(ON_START_EVT,
                TraceHelper.FLAG_UI_EVENT);
        super.onStart();
        if (!mDeferOverlayCallbacks) {
            mOverlayManager.onActivityStarted(this);
        }

        mAppWidgetHost.setListenIfResumed(true);
        TraceHelper.INSTANCE.endSection(traceToken);
    }

    @Override
    @CallSuper
    protected void onDeferredResumed() {
        logStopAndResume(Action.Command.RESUME);
        getUserEventDispatcher().startSession();

        AppLaunchTracker.INSTANCE.get(this).onReturnedToHome();

        // Process any items that were added while Launcher was away.
        InstallShortcutReceiver.disableAndFlushInstallQueue(
                InstallShortcutReceiver.FLAG_ACTIVITY_PAUSED, this);

        // Refresh shortcuts if the permission changed.
        mModel.refreshShortcutsIfRequired();

        // Set the notification listener and fetch updated notifications when we resume
        NotificationListener.setNotificationsChangedListener(mPopupDataProvider);

        DiscoveryBounce.showForHomeIfNeeded(this);
    }

    protected void handlePendingActivityRequest() { }

    private void logStopAndResume(int command) {
        if (mPendingExecutor != null) return;
        int pageIndex = mWorkspace.isOverlayShown() ? -1 : mWorkspace.getCurrentPage();
        int containerType = mStateManager.getState().containerType;

        StatsLogManager.EventEnum event;
        StatsLogManager.StatsLogger logger = getStatsLogManager().logger();
        if (command == Action.Command.RESUME) {
            logger.withSrcState(LAUNCHER_STATE_BACKGROUND)
                .withDstState(containerTypeToAtomState(mStateManager.getState().containerType));
            event = LAUNCHER_ONRESUME;
        } else { /* command == Action.Command.STOP */
            logger.withSrcState(containerTypeToAtomState(mStateManager.getState().containerType))
                    .withDstState(LAUNCHER_STATE_BACKGROUND);
            event = LAUNCHER_ONSTOP;
        }

        if (containerType == ContainerType.WORKSPACE && mWorkspace != null) {
            getUserEventDispatcher().logActionCommand(command,
                    containerType, -1, pageIndex);
            logger.withContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setWorkspace(
                            LauncherAtom.WorkspaceContainer.newBuilder()
                                    .setPageIndex(pageIndex)).build());
        } else {
            getUserEventDispatcher().logActionCommand(command, containerType, -1);
        }
        logger.log(event);
    }

    private void scheduleDeferredCheck() {
        mHandler.removeCallbacks(mDeferredOverlayCallbacks);
        postAsyncCallback(mHandler, mDeferredOverlayCallbacks);
    }

    private void checkIfOverlayStillDeferred() {
        if (!mDeferOverlayCallbacks) {
            return;
        }
        if (isStarted() && (!hasBeenResumed()
                || mStateManager.getState().hasFlag(FLAG_NON_INTERACTIVE))) {
            return;
        }
        mDeferOverlayCallbacks = false;

        // Move the client to the correct state. Calling the same method twice is no-op.
        if (isStarted()) {
            mOverlayManager.onActivityStarted(this);
        }
        if (hasBeenResumed()) {
            mOverlayManager.onActivityResumed(this);
        } else {
            mOverlayManager.onActivityPaused(this);
        }
        if (!isStarted()) {
            mOverlayManager.onActivityStopped(this);
        }
    }

    public void deferOverlayCallbacksUntilNextResumeOrStop() {
        mDeferOverlayCallbacks = true;
    }

    public LauncherOverlayManager getOverlayManager() {
        return mOverlayManager;
    }

    @Override
    public void onStateSetStart(LauncherState state) {
        super.onStateSetStart(state);
        if (mDeferOverlayCallbacks) {
            scheduleDeferredCheck();
        }
        addActivityFlags(ACTIVITY_STATE_TRANSITION_ACTIVE);

        if (state.hasFlag(FLAG_CLOSE_POPUPS)) {
            AbstractFloatingView.closeAllOpenViews(this, !state.hasFlag(FLAG_NON_INTERACTIVE));
        }

        if (state == SPRING_LOADED) {
            // Prevent any Un/InstallShortcutReceivers from updating the db while we are
            // not on homescreen
            InstallShortcutReceiver.enableInstallQueue(FLAG_DRAG_AND_DROP);
            getRotationHelper().setCurrentStateRequest(REQUEST_LOCK);

            mWorkspace.showPageIndicatorAtCurrentScroll();
            mWorkspace.setClipChildren(false);
        }
        // When multiple pages are visible, show persistent page indicator
        mWorkspace.getPageIndicator().setShouldAutoHide(!state.hasFlag(FLAG_MULTI_PAGE));
    }

    @Override
    public void onStateSetEnd(LauncherState state) {
        super.onStateSetStart(state);
        getAppWidgetHost().setResumed(state == LauncherState.NORMAL);
        getWorkspace().setClipChildren(!state.hasFlag(FLAG_MULTI_PAGE));

        finishAutoCancelActionMode();
        removeActivityFlags(ACTIVITY_STATE_TRANSITION_ACTIVE);

        // dispatch window state changed
        getWindow().getDecorView().sendAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED);
        AccessibilityManagerCompat.sendStateEventToTest(this, state.ordinal);

        if (state == NORMAL) {
            // Re-enable any Un/InstallShortcutReceiver and now process any queued items
            InstallShortcutReceiver.disableAndFlushInstallQueue(FLAG_DRAG_AND_DROP, this);

            // Clear any rotation locks when going to normal state
            getRotationHelper().setCurrentStateRequest(REQUEST_NONE);
        }
    }

    @Override
    protected void onResume() {
        Object traceToken = TraceHelper.INSTANCE.beginSection(ON_RESUME_EVT,
                TraceHelper.FLAG_UI_EVENT);
        super.onResume();

        if (!mOnResumeCallbacks.isEmpty()) {
            final ArrayList<OnResumeCallback> resumeCallbacks = new ArrayList<>(mOnResumeCallbacks);
            mOnResumeCallbacks.clear();
            for (int i = resumeCallbacks.size() - 1; i >= 0; i--) {
                resumeCallbacks.get(i).onLauncherResume();
            }
            resumeCallbacks.clear();
        }

        if (mDeferOverlayCallbacks) {
            scheduleDeferredCheck();
        } else {
            mOverlayManager.onActivityResumed(this);
        }

        TraceHelper.INSTANCE.endSection(traceToken);
    }

    @Override
    protected void onPause() {
        // Ensure that items added to Launcher are queued until Launcher returns
        InstallShortcutReceiver.enableInstallQueue(InstallShortcutReceiver.FLAG_ACTIVITY_PAUSED);

        super.onPause();
        mDragController.cancelDrag();
        mLastTouchUpTime = -1;
        mDropTargetBar.animateToVisibility(false);

        if (!mDeferOverlayCallbacks) {
            mOverlayManager.onActivityPaused(this);
        }
    }

    public void startActivitySafelyAuth(View v, Intent intent, ItemInfo item,
            String sourceContainer) {
        LineageUtils.showLockScreen(this, getString(R.string.trust_apps_manager_name), () -> {
            startActivitySafely(v, intent, item, sourceContainer);
        });
    }

    class LauncherOverlayCallbacksImpl implements LauncherOverlayCallbacks {

        public void onScrollChanged(float progress) {
            if (mWorkspace != null) {
                mWorkspace.onOverlayScrollChanged(progress);
            }
        }
    }

    /**
     * Restores the previous state, if it exists.
     *
     * @param savedState The previous state.
     */
    private void restoreState(Bundle savedState) {
        if (savedState == null) {
            return;
        }

        int stateOrdinal = savedState.getInt(RUNTIME_STATE, NORMAL.ordinal);
        LauncherState[] stateValues = LauncherState.values();
        LauncherState state = stateValues[stateOrdinal];
        if (!state.shouldDisableRestore()) {
            mStateManager.goToState(state, false /* animated */);
        }

        PendingRequestArgs requestArgs = savedState.getParcelable(
                RUNTIME_STATE_PENDING_REQUEST_ARGS);
        if (requestArgs != null) {
            setWaitingForResult(requestArgs);
        }
        mPendingActivityRequestCode = savedState.getInt(RUNTIME_STATE_PENDING_REQUEST_CODE);

        mPendingActivityResult = savedState.getParcelable(RUNTIME_STATE_PENDING_ACTIVITY_RESULT);

        SparseArray<Parcelable> widgetsState =
                savedState.getSparseParcelableArray(RUNTIME_STATE_WIDGET_PANEL);
        if (widgetsState != null) {
            WidgetsFullSheet.show(this, false).restoreHierarchyState(widgetsState);
        }
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    protected void setupViews() {
        mDragLayer = findViewById(R.id.drag_layer);
        mFocusHandler = mDragLayer.getFocusIndicatorHelper();
        mWorkspace = mDragLayer.findViewById(R.id.workspace);
        mWorkspace.initParentViews(mDragLayer);
        mOverviewPanel = findViewById(R.id.overview_panel);
        mHotseat = findViewById(R.id.hotseat);
        mHotseat.setWorkspace(mWorkspace);

        // Setup the drag layer
        mDragLayer.setup(mDragController, mWorkspace);

        mWorkspace.setup(mDragController);
        // Until the workspace is bound, ensure that we keep the wallpaper offset locked to the
        // default state, otherwise we will update to the wrong offsets in RTL
        mWorkspace.lockWallpaperToDefaultPage();
        mWorkspace.bindAndInitFirstWorkspaceScreen(null /* recycled qsb */);
        mDragController.addDragListener(mWorkspace);

        // Get the search/delete/uninstall bar
        mDropTargetBar = mDragLayer.findViewById(R.id.drop_target_bar);

        // Setup Apps
        mAppsView = findViewById(R.id.apps_view);

        // Setup Scrim
        mScrimView = findViewById(R.id.scrim_view);

        // Setup the drag controller (drop targets have to be added in reverse order in priority)
        mDropTargetBar.setup(mDragController);

        mAllAppsController.setupViews(mAppsView, mScrimView);
    }

    /**
     * Creates a view representing a shortcut.
     *
     * @param info The data structure describing the shortcut.
     */
    View createShortcut(WorkspaceItemInfo info) {
        return createShortcut((ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentPage()), info);
    }

    /**
     * Creates a view representing a shortcut inflated from the specified resource.
     *
     * @param parent The group the shortcut belongs to.
     * @param info   The data structure describing the shortcut.
     * @return A View inflated from layoutResId.
     */
    public View createShortcut(ViewGroup parent, WorkspaceItemInfo info) {
        BubbleTextView favorite = (BubbleTextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.app_icon, parent, false);
        favorite.applyFromWorkspaceItem(info);
        favorite.setOnClickListener(ItemClickHandler.INSTANCE);
        favorite.setOnFocusChangeListener(mFocusHandler);
        return favorite;
    }

    /**
     * Add a shortcut to the workspace or to a Folder.
     *
     * @param data The intent describing the shortcut.
     */
    private void completeAddShortcut(Intent data, int container, int screenId, int cellX,
            int cellY, PendingRequestArgs args) {
        if (args.getRequestCode() != REQUEST_CREATE_SHORTCUT
                || args.getPendingIntent().getComponent() == null) {
            return;
        }

        int[] cellXY = mTmpAddItemCellCoordinates;
        CellLayout layout = getCellLayout(container, screenId);

        WorkspaceItemInfo info = null;
        if (Utilities.ATLEAST_OREO) {
            info = PinRequestHelper.createWorkspaceItemFromPinItemRequest(
                    this, PinRequestHelper.getPinItemRequest(data), 0);
        }

        if (info == null) {
            // Legacy shortcuts are only supported for primary profile.
            info = Process.myUserHandle().equals(args.user)
                    ? InstallShortcutReceiver.fromShortcutIntent(this, data) : null;

            if (info == null) {
                Log.e(TAG, "Unable to parse a valid custom shortcut result");
                return;
            } else if (!new PackageManagerHelper(this).hasPermissionForActivity(
                    info.intent, args.getPendingIntent().getComponent().getPackageName())) {
                // The app is trying to add a shortcut without sufficient permissions
                Log.e(TAG, "Ignoring malicious intent " + info.intent.toUri(0));
                return;
            }
        }

        if (container < 0) {
            // Adding a shortcut to the Workspace.
            final View view = createShortcut(info);
            boolean foundCellSpan = false;
            // First we check if we already know the exact location where we want to add this item.
            if (cellX >= 0 && cellY >= 0) {
                cellXY[0] = cellX;
                cellXY[1] = cellY;
                foundCellSpan = true;

                DragObject dragObject = new DragObject(getApplicationContext());
                dragObject.dragInfo = info;
                // If appropriate, either create a folder or add to an existing folder
                if (mWorkspace.createUserFolderIfNecessary(view, container, layout, cellXY, 0,
                        true, dragObject)) {
                    return;
                }
                if (mWorkspace.addToExistingFolderIfNecessary(view, layout, cellXY, 0, dragObject,
                        true)) {
                    return;
                }
            } else {
                foundCellSpan = layout.findCellForSpan(cellXY, 1, 1);
            }

            if (!foundCellSpan) {
                mWorkspace.onNoCellFound(layout);
                return;
            }

            getModelWriter().addItemToDatabase(info, container, screenId, cellXY[0], cellXY[1]);
            mWorkspace.addInScreen(view, info);
        } else {
            // Adding a shortcut to a Folder.
            FolderIcon folderIcon = findFolderIcon(container);
            if (folderIcon != null) {
                FolderInfo folderInfo = (FolderInfo) folderIcon.getTag();
                folderInfo.add(info, args.rank, false);
            } else {
                Log.e(TAG, "Could not find folder with id " + container + " to add shortcut.");
            }
        }
    }

    public FolderIcon findFolderIcon(final int folderIconId) {
        return (FolderIcon) mWorkspace.getHomescreenIconByItemId(folderIconId);
    }

    /**
     * Add a widget to the workspace.
     *
     * @param appWidgetId The app widget id
     */
    @Thunk
    void completeAddAppWidget(int appWidgetId, ItemInfo itemInfo,
            AppWidgetHostView hostView, LauncherAppWidgetProviderInfo appWidgetInfo) {

        if (appWidgetInfo == null) {
            appWidgetInfo = mAppWidgetManager.getLauncherAppWidgetInfo(appWidgetId);
        }

        LauncherAppWidgetInfo launcherInfo;
        launcherInfo = new LauncherAppWidgetInfo(appWidgetId, appWidgetInfo.provider);
        launcherInfo.spanX = itemInfo.spanX;
        launcherInfo.spanY = itemInfo.spanY;
        launcherInfo.minSpanX = itemInfo.minSpanX;
        launcherInfo.minSpanY = itemInfo.minSpanY;
        launcherInfo.user = appWidgetInfo.getProfile();

        getModelWriter().addItemToDatabase(launcherInfo,
                itemInfo.container, itemInfo.screenId, itemInfo.cellX, itemInfo.cellY);

        if (hostView == null) {
            // Perform actual inflation because we're live
            hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);
        }
        hostView.setVisibility(View.VISIBLE);
        prepareAppWidget(hostView, launcherInfo);
        mWorkspace.addInScreen(hostView, launcherInfo);
        announceForAccessibility(R.string.item_added_to_workspace);
    }

    private void prepareAppWidget(AppWidgetHostView hostView, LauncherAppWidgetInfo item) {
        hostView.setTag(item);
        item.onBindAppWidget(this, hostView);
        hostView.setFocusable(true);
        hostView.setOnFocusChangeListener(mFocusHandler);
    }

    private final BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Reset AllApps to its initial state only if we are not in the middle of
            // processing a multi-step drop
            if (mPendingRequestArgs == null) {
                if (!isInState(NORMAL)) {
                    onUiChangedWhileSleeping();
                }
                mStateManager.goToState(NORMAL);
            }
        }
    };

    private void updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        mWorkspace.updateNotificationDots(updatedDots);
        mAppsView.getAppsStore().updateNotificationDots(updatedDots);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mOverlayManager.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mOverlayManager.onDetachedFromWindow();
        closeContextMenu();
    }

    public AllAppsTransitionController getAllAppsController() {
        return mAllAppsController;
    }

    @Override
    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    public AllAppsContainerView getAppsView() {
        return mAppsView;
    }

    public Workspace getWorkspace() {
        return mWorkspace;
    }

    public Hotseat getHotseat() {
        return mHotseat;
    }

    public <T extends View> T getOverviewPanel() {
        return (T) mOverviewPanel;
    }

    public DropTargetBar getDropTargetBar() {
        return mDropTargetBar;
    }

    public ScrimView getScrimView() {
        return mScrimView;
    }

    public LauncherAppWidgetHost getAppWidgetHost() {
        return mAppWidgetHost;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    public ModelWriter getModelWriter() {
        return mModelWriter;
    }

    @Override
    public SharedPreferences getSharedPrefs() {
        return mSharedPrefs;
    }

    @Override
    public SharedPreferences getDevicePrefs() {
        return Utilities.getDevicePrefs(this);
    }

    public int getOrientation() {
        return mOldConfig.orientation;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (Utilities.IS_RUNNING_IN_TEST_HARNESS) {
            Log.d(TestProtocol.PERMANENT_DIAG_TAG, "Launcher.onNewIntent: " + intent);
        }
        Object traceToken = TraceHelper.INSTANCE.beginSection(ON_NEW_INTENT_EVT);
        super.onNewIntent(intent);

        boolean alreadyOnHome = hasWindowFocus() && ((intent.getFlags() &
                Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

        // Check this condition before handling isActionMain, as this will get reset.
        boolean shouldMoveToDefaultScreen = alreadyOnHome && isInState(NORMAL)
                && AbstractFloatingView.getTopOpenView(this) == null;
        boolean isActionMain = Intent.ACTION_MAIN.equals(intent.getAction());
        boolean internalStateHandled = ACTIVITY_TRACKER.handleNewIntent(this, intent);

        if (isActionMain) {
            if (!internalStateHandled) {
                // In all these cases, only animate if we're already on home
                closeOpenViews(isStarted());

                if (!isInState(NORMAL)) {
                    // Only change state, if not already the same. This prevents cancelling any
                    // animations running as part of resume
                    mStateManager.goToState(NORMAL, mStateManager.shouldAnimateStateChange(),
                            this::handlePendingActivityRequest);
                }

                // Reset the apps view
                if (!alreadyOnHome) {
                    mAppsView.reset(isStarted() /* animate */);
                }

                if (shouldMoveToDefaultScreen && !mWorkspace.isHandlingTouch()) {
                    mWorkspace.post(mWorkspace::moveToDefaultScreen);
                }
            }

            // Handle HOME_INTENT
            UserEventDispatcher ued = getUserEventDispatcher();
            Target target = newContainerTarget(mStateManager.getState().containerType);
            target.pageIndex = mWorkspace.getCurrentPage();
            ued.logActionCommand(Action.Command.HOME_INTENT, target,
                    newContainerTarget(ContainerType.WORKSPACE));
            hideKeyboard();

            if (mLauncherCallbacks != null) {
                mLauncherCallbacks.onHomeIntent(internalStateHandled);
            }
            mOverlayManager.hideOverlay(isStarted() && !isForceInvisible());
            handleGestureContract(intent);
        } else if (Intent.ACTION_ALL_APPS.equals(intent.getAction())) {
            getStateManager().goToState(ALL_APPS, alreadyOnHome);
        }

        TraceHelper.INSTANCE.endSection(traceToken);
    }

    /**
     * Handles gesture nav contract
     */
    protected void handleGestureContract(Intent intent) {
        GestureNavContract gnc = GestureNavContract.fromIntent(intent);
        if (gnc != null) {
            AbstractFloatingView.closeOpenViews(this, false, TYPE_ICON_SURFACE);
            FloatingSurfaceView.show(this, gnc);
        }
    }

    /**
     * Hides the keyboard if visible
     */
    public void hideKeyboard() {
        final View v = getWindow().peekDecorView();
        if (v != null && v.getWindowToken() != null) {
            UiThreadHelper.hideKeyboardAsync(this, v.getWindowToken());
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        mWorkspace.restoreInstanceStateForChild(mSynchronouslyBoundPage);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mWorkspace.getChildCount() > 0) {
            outState.putInt(RUNTIME_STATE_CURRENT_SCREEN, mWorkspace.getNextPage());

        }
        outState.putInt(RUNTIME_STATE, mStateManager.getState().ordinal);


        AbstractFloatingView widgets = AbstractFloatingView
                .getOpenView(this, AbstractFloatingView.TYPE_WIDGETS_FULL_SHEET);
        if (widgets != null) {
            SparseArray<Parcelable> widgetsState = new SparseArray<>();
            widgets.saveHierarchyState(widgetsState);
            outState.putSparseParcelableArray(RUNTIME_STATE_WIDGET_PANEL, widgetsState);
        } else {
            outState.remove(RUNTIME_STATE_WIDGET_PANEL);
        }

        // We close any open folders and shortcut containers that are not safe for rebind,
        // and we need to make sure this state is reflected.
        AbstractFloatingView.closeOpenViews(this, false, TYPE_ALL & ~TYPE_REBIND_SAFE);
        finishAutoCancelActionMode();

        if (mPendingRequestArgs != null) {
            outState.putParcelable(RUNTIME_STATE_PENDING_REQUEST_ARGS, mPendingRequestArgs);
        }
        outState.putInt(RUNTIME_STATE_PENDING_REQUEST_CODE, mPendingActivityRequestCode);

        if (mPendingActivityResult != null) {
            outState.putParcelable(RUNTIME_STATE_PENDING_ACTIVITY_RESULT, mPendingActivityResult);
        }

        super.onSaveInstanceState(outState);
        mOverlayManager.onActivitySaveInstanceState(this, outState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ACTIVITY_TRACKER.onActivityDestroyed(this);

        unregisterReceiver(mScreenOffReceiver);
        mWorkspace.removeFolderListeners();
        PluginManagerWrapper.INSTANCE.get(this).removePluginListener(this);

        mModel.removeCallbacks(this);
        mRotationHelper.destroy();

        try {
            mAppWidgetHost.stopListening();
        } catch (NullPointerException ex) {
            Log.w(TAG, "problem while stopping AppWidgetHost during Launcher destruction", ex);
        }

        TextKeyListener.getInstance().release();
        clearPendingBinds();
        LauncherAppState.getIDP(this).removeOnChangeListener(this);

        mOverlayManager.onActivityDestroyed(this);
        mAppTransitionManager.unregisterRemoteAnimations();
        mUserChangedCallbackCloseable.close();
        mAllAppsController.onActivityDestroyed();
    }

    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    public DragController getDragController() {
        return mDragController;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
        }
        super.startActivityForResult(intent, requestCode, options);
    }

    @Override
    public void startIntentSenderForResult(IntentSender intent, int requestCode,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
        }
        try {
            super.startIntentSenderForResult(intent, requestCode,
                    fillInIntent, flagsMask, flagsValues, extraFlags, options);
        } catch (IntentSender.SendIntentException e) {
            throw new ActivityNotFoundException();
        }
    }

    /**
     * Indicates that we want global search for this activity by setting the globalSearch
     * argument for {@link #startSearch} to true.
     */
    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {
        if (appSearchData == null) {
            appSearchData = new Bundle();
            appSearchData.putString("source", "launcher-search");
        }

        if (mLauncherCallbacks == null ||
                !mLauncherCallbacks.startSearch(initialQuery, selectInitialQuery, appSearchData)) {
            // Starting search from the callbacks failed. Start the default global search.
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, true);
        }

        // We need to show the workspace after starting the search
        mStateManager.goToState(NORMAL);
    }

    public boolean isWorkspaceLocked() {
        return mWorkspaceLoading || mPendingRequestArgs != null;
    }

    public boolean isWorkspaceLoading() {
        return mWorkspaceLoading;
    }

    private void setWorkspaceLoading(boolean value) {
        mWorkspaceLoading = value;
    }

    public void setWaitingForResult(PendingRequestArgs args) {
        mPendingRequestArgs = args;
    }

    void addAppWidgetFromDropImpl(int appWidgetId, ItemInfo info, AppWidgetHostView boundWidget,
            WidgetAddFlowHandler addFlowHandler) {
        if (LOGD) {
            Log.d(TAG, "Adding widget from drop");
        }
        addAppWidgetImpl(appWidgetId, info, boundWidget, addFlowHandler, 0);
    }

    void addAppWidgetImpl(int appWidgetId, ItemInfo info,
            AppWidgetHostView boundWidget, WidgetAddFlowHandler addFlowHandler, int delay) {
        if (!addFlowHandler.startConfigActivity(this, appWidgetId, info,
                REQUEST_CREATE_APPWIDGET)) {
            // If the configuration flow was not started, add the widget

            Runnable onComplete = new Runnable() {
                @Override
                public void run() {
                    // Exit spring loaded mode if necessary after adding the widget
                    mStateManager.goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
                }
            };
            completeAddAppWidget(appWidgetId, info, boundWidget,
                    addFlowHandler.getProviderInfo(this));
            mWorkspace.removeExtraEmptyScreenDelayed(delay, false, onComplete);
        }
    }

    public void addPendingItem(PendingAddItemInfo info, int container, int screenId,
            int[] cell, int spanX, int spanY) {
        info.container = container;
        info.screenId = screenId;
        if (cell != null) {
            info.cellX = cell[0];
            info.cellY = cell[1];
        }
        info.spanX = spanX;
        info.spanY = spanY;

        switch (info.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                addAppWidgetFromDrop((PendingAddWidgetInfo) info);
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                processShortcutFromDrop((PendingAddShortcutInfo) info);
                break;
            default:
                throw new IllegalStateException("Unknown item type: " + info.itemType);
        }
    }

    /**
     * Process a shortcut drop.
     */
    private void processShortcutFromDrop(PendingAddShortcutInfo info) {
        Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(info.componentName);
        setWaitingForResult(PendingRequestArgs.forIntent(REQUEST_CREATE_SHORTCUT, intent, info));
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "start: processShortcutFromDrop");
        if (!info.activityInfo.startConfigActivity(this, REQUEST_CREATE_SHORTCUT)) {
            handleActivityResult(REQUEST_CREATE_SHORTCUT, RESULT_CANCELED, null);
        }
    }

    /**
     * Process a widget drop.
     */
    private void addAppWidgetFromDrop(PendingAddWidgetInfo info) {
        AppWidgetHostView hostView = info.boundWidget;
        final int appWidgetId;
        WidgetAddFlowHandler addFlowHandler = info.getHandler();
        if (hostView != null) {
            // In the case where we've prebound the widget, we remove it from the DragLayer
            if (LOGD) {
                Log.d(TAG, "Removing widget view from drag layer and setting boundWidget to null");
            }
            getDragLayer().removeView(hostView);

            appWidgetId = hostView.getAppWidgetId();
            addAppWidgetFromDropImpl(appWidgetId, info, hostView, addFlowHandler);

            // Clear the boundWidget so that it doesn't get destroyed.
            info.boundWidget = null;
        } else {
            // In this case, we either need to start an activity to get permission to bind
            // the widget, or we need to start an activity to configure the widget, or both.
            if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET) {
                appWidgetId = CustomWidgetManager.INSTANCE.get(this).getWidgetIdForCustomProvider(
                        info.componentName);
            } else {
                appWidgetId = getAppWidgetHost().allocateAppWidgetId();
            }
            Bundle options = info.bindOptions;

            boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
                    appWidgetId, info.info, options);
            if (success) {
                addAppWidgetFromDropImpl(appWidgetId, info, null, addFlowHandler);
            } else {
                addFlowHandler.startBindFlow(this, appWidgetId, info, REQUEST_BIND_APPWIDGET);
            }
        }
    }

    /**
     * Creates and adds new folder to CellLayout
     */
    public FolderIcon addFolder(CellLayout layout, int container, final int screenId, int cellX,
            int cellY) {
        final FolderInfo folderInfo = new FolderInfo();

        // Update the model
        getModelWriter().addItemToDatabase(folderInfo, container, screenId, cellX, cellY);

        // Create the view
        FolderIcon newFolder = FolderIcon.inflateFolderAndIcon(R.layout.folder_icon, this, layout,
                folderInfo);
        mWorkspace.addInScreen(newFolder, folderInfo);
        // Force measure the new folder icon
        CellLayout parent = mWorkspace.getParentCellLayoutForView(newFolder);
        parent.getShortcutsAndWidgets().measureChild(newFolder);
        return newFolder;
    }

    /**
     * Called when a workspace item is converted into a folder
     */
    public void folderCreatedFromItem(Folder folder, WorkspaceItemInfo itemInfo){}

    /**
     * Called when a folder is converted into a workspace item
     */
    public void folderConvertedToItem(Folder folder, WorkspaceItemInfo itemInfo) {}

    /**
     * Unbinds the view for the specified item, and removes the item and all its children.
     *
     * @param v the view being removed.
     * @param itemInfo the {@link ItemInfo} for this view.
     * @param deleteFromDb whether or not to delete this item from the db.
     */
    public boolean removeItem(View v, final ItemInfo itemInfo, boolean deleteFromDb) {
        if (itemInfo instanceof WorkspaceItemInfo) {
            // Remove the shortcut from the folder before removing it from launcher
            View folderIcon = mWorkspace.getHomescreenIconByItemId(itemInfo.container);
            if (folderIcon instanceof FolderIcon) {
                ((FolderInfo) folderIcon.getTag()).remove((WorkspaceItemInfo) itemInfo, true);
            } else {
                mWorkspace.removeWorkspaceItem(v);
            }
            if (deleteFromDb) {
                getModelWriter().deleteItemFromDatabase(itemInfo);
            }
        } else if (itemInfo instanceof FolderInfo) {
            final FolderInfo folderInfo = (FolderInfo) itemInfo;
            if (v instanceof FolderIcon) {
                ((FolderIcon) v).removeListeners();
            }
            mWorkspace.removeWorkspaceItem(v);
            if (deleteFromDb) {
                getModelWriter().deleteFolderAndContentsFromDatabase(folderInfo);
            }
        } else if (itemInfo instanceof LauncherAppWidgetInfo) {
            final LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo) itemInfo;
            mWorkspace.removeWorkspaceItem(v);
            if (deleteFromDb) {
                getModelWriter().deleteWidgetInfo(widgetInfo, getAppWidgetHost());
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "Key event", event);
        return (event.getKeyCode() == KeyEvent.KEYCODE_HOME) || super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchInProgress = true;
                break;
            case MotionEvent.ACTION_UP:
                mLastTouchUpTime = System.currentTimeMillis();
                // Follow through
            case MotionEvent.ACTION_CANCEL:
                mTouchInProgress = false;
                break;
        }
        TestLogging.recordMotionEvent(TestProtocol.SEQUENCE_MAIN, "Touch event", ev);
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Returns true if a touch interaction is in progress
     */
    public boolean isTouchInProgress() {
        return mTouchInProgress;
    }

    @Override
    public void onBackPressed() {
        if (finishAutoCancelActionMode()) {
            return;
        }

        if (mDragController.isDragging()) {
            mDragController.cancelDrag();
            return;
        }

        // Note: There should be at most one log per method call. This is enforced implicitly
        // by using if-else statements.
        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(this);
        if (topView != null && topView.onBackPressed()) {
            // Handled by the floating view.
        } else {
            mStateManager.getState().onBackPressed(this);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public ActivityOptions getActivityLaunchOptions(View v) {
        return mAppTransitionManager.getActivityLaunchOptions(this, v);
    }

    public LauncherAppTransitionManager getAppTransitionManager() {
        return mAppTransitionManager;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected boolean onErrorStartingShortcut(Intent intent, ItemInfo info) {
        // Due to legacy reasons, direct call shortcuts require Launchers to have the
        // corresponding permission. Show the appropriate permission prompt if that
        // is the case.
        if (intent.getComponent() == null
                && Intent.ACTION_CALL.equals(intent.getAction())
                && checkSelfPermission(android.Manifest.permission.CALL_PHONE) !=
                PackageManager.PERMISSION_GRANTED) {

            setWaitingForResult(PendingRequestArgs
                    .forIntent(REQUEST_PERMISSION_CALL_PHONE, intent, info));
            requestPermissions(new String[]{android.Manifest.permission.CALL_PHONE},
                    REQUEST_PERMISSION_CALL_PHONE);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean startActivitySafely(View v, Intent intent, ItemInfo item,
            @Nullable String sourceContainer) {
        if (!hasBeenResumed()) {
            // Workaround an issue where the WM launch animation is clobbered when finishing the
            // recents animation into launcher. Defer launching the activity until Launcher is
            // next resumed.
            addOnResumeCallback(() -> startActivitySafely(v, intent, item, sourceContainer));
            if (mOnDeferredActivityLaunchCallback != null) {
                mOnDeferredActivityLaunchCallback.run();
                mOnDeferredActivityLaunchCallback = null;
            }
            return true;
        }

        boolean success = super.startActivitySafely(v, intent, item, sourceContainer);
        if (success && v instanceof BubbleTextView) {
            // This is set to the view that launched the activity that navigated the user away
            // from launcher. Since there is no callback for when the activity has finished
            // launching, enable the press state and keep this reference to reset the press
            // state when we return to launcher.
            BubbleTextView btv = (BubbleTextView) v;
            btv.setStayPressed(true);
            addOnResumeCallback(btv);
        }
        return success;
    }

    boolean isHotseatLayout(View layout) {
        // TODO: Remove this method
        return mHotseat != null && (layout == mHotseat);
    }

    /**
     * Returns the CellLayout of the specified container at the specified screen.
     */
    public CellLayout getCellLayout(int container, int screenId) {
        return (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT)
                ? mHotseat : mWorkspace.getScreenWithId(screenId);
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
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        final boolean result = super.dispatchPopulateAccessibilityEvent(event);
        final List<CharSequence> text = event.getText();
        text.clear();
        // Populate event with a fake title based on the current state.
        // TODO: When can workspace be null?
        text.add(mWorkspace == null
                ? getString(R.string.all_apps_home_button_label)
                : mStateManager.getState().getDescription(this));
        return result;
    }

    @Override
    public void addOnResumeCallback(OnResumeCallback callback) {
        mOnResumeCallbacks.add(callback);
    }

    /**
     * Persistant callback which notifies when an activity launch is deferred because the activity
     * was not yet resumed.
     */
    public void setOnDeferredActivityLaunchCallback(Runnable callback) {
        mOnDeferredActivityLaunchCallback = callback;
    }

    /**
     * Sets the next page to bind synchronously on next bind.
     * @param page
     */
    public void setPageToBindSynchronously(int page) {
        mPageToBindSynchronously = page;
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public int getPageToBindSynchronously() {
        if (mPageToBindSynchronously != PagedView.INVALID_PAGE) {
            return mPageToBindSynchronously;
        } else  if (mWorkspace != null) {
            return mWorkspace.getCurrentPage();
        } else {
            return 0;
        }
    }

    /**
     * Clear any pending bind callbacks. This is called when is loader is planning to
     * perform a full rebind from scratch.
     */
    @Override
    public void clearPendingBinds() {
        if (mPendingExecutor != null) {
            mPendingExecutor.markCompleted();
            mPendingExecutor = null;

            // We might have set this flag previously and forgot to clear it.
            mAppsView.getAppsStore()
                    .disableDeferUpdatesSilently(AllAppsStore.DEFER_UPDATES_NEXT_DRAW);
        }
    }

    /**
     * Refreshes the shortcuts shown on the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void startBinding() {
        Object traceToken = TraceHelper.INSTANCE.beginSection("startBinding");
        // Floating panels (except the full widget sheet) are associated with individual icons. If
        // we are starting a fresh bind, close all such panels as all the icons are about
        // to go away.
        AbstractFloatingView.closeOpenViews(this, true, TYPE_ALL & ~TYPE_REBIND_SAFE);

        setWorkspaceLoading(true);

        // Clear the workspace because it's going to be rebound
        mDragController.cancelDrag();

        mWorkspace.clearDropTargets();
        mWorkspace.removeAllWorkspaceScreens();
        mAppWidgetHost.clearViews();

        if (mHotseat != null) {
            mHotseat.resetLayout(getDeviceProfile().isVerticalBarLayout());
        }
        TraceHelper.INSTANCE.endSection(traceToken);
    }

    @Override
    public void bindScreens(IntArray orderedScreenIds) {
        // Make sure the first screen is always at the start.
        if (FeatureFlags.QSB_ON_FIRST_SCREEN &&
                orderedScreenIds.indexOf(Workspace.FIRST_SCREEN_ID) != 0) {
            orderedScreenIds.removeValue(Workspace.FIRST_SCREEN_ID);
            orderedScreenIds.add(0, Workspace.FIRST_SCREEN_ID);
        } else if (!FeatureFlags.QSB_ON_FIRST_SCREEN && orderedScreenIds.isEmpty()) {
            // If there are no screens, we need to have an empty screen
            mWorkspace.addExtraEmptyScreen();
        }
        bindAddScreens(orderedScreenIds);

        // After we have added all the screens, if the wallpaper was locked to the default state,
        // then notify to indicate that it can be released and a proper wallpaper offset can be
        // computed before the next layout
        mWorkspace.unlockWallpaperFromDefaultPageOnNextLayout();
    }

    private void bindAddScreens(IntArray orderedScreenIds) {
        int count = orderedScreenIds.size();
        for (int i = 0; i < count; i++) {
            int screenId = orderedScreenIds.get(i);
            if (!FeatureFlags.QSB_ON_FIRST_SCREEN || screenId != Workspace.FIRST_SCREEN_ID) {
                // No need to bind the first screen, as its always bound.
                mWorkspace.insertNewWorkspaceScreenBeforeEmptyScreen(screenId);
            }
        }
    }

    @Override
    public void preAddApps() {
        // If there's an undo snackbar, force it to complete to ensure empty screens are removed
        // before trying to add new items.
        mModelWriter.commitDelete();
        AbstractFloatingView snackbar = AbstractFloatingView.getOpenView(this, TYPE_SNACKBAR);
        if (snackbar != null) {
            snackbar.post(() -> snackbar.close(true));
        }
    }

    @Override
    public void bindAppsAdded(IntArray newScreens, ArrayList<ItemInfo> addNotAnimated,
            ArrayList<ItemInfo> addAnimated) {
        // Add the new screens
        if (newScreens != null) {
            bindAddScreens(newScreens);
        }

        // We add the items without animation on non-visible pages, and with
        // animations on the new page (which we will try and snap to).
        if (addNotAnimated != null && !addNotAnimated.isEmpty()) {
            bindItems(addNotAnimated, false);
        }
        if (addAnimated != null && !addAnimated.isEmpty()) {
            bindItems(addAnimated, true);
        }

        // Remove the extra empty screen
        mWorkspace.removeExtraEmptyScreen(false);
    }

    /**
     * Bind the items start-end from the list.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindItems(final List<ItemInfo> items, final boolean forceAnimateIcons) {
        // Get the list of added items and intersect them with the set of items here
        final Collection<Animator> bounceAnims = new ArrayList<>();
        final boolean animateIcons = forceAnimateIcons && canRunNewAppsAnimation();
        Workspace workspace = mWorkspace;
        int newItemsScreenId = -1;
        int end = items.size();
        for (int i = 0; i < end; i++) {
            final ItemInfo item = items.get(i);

            // Short circuit if we are loading dock items for a configuration which has no dock
            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
                    mHotseat == null) {
                continue;
            }

            final View view;
            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT: {
                    WorkspaceItemInfo info = (WorkspaceItemInfo) item;
                    view = createShortcut(info);
                    break;
                }
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER: {
                    view = FolderIcon.inflateFolderAndIcon(R.layout.folder_icon, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentPage()),
                            (FolderInfo) item);
                    break;
                }
                case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET: {
                    view = inflateAppWidget((LauncherAppWidgetInfo) item);
                    if (view == null) {
                        continue;
                    }
                    break;
                }
                default:
                    throw new RuntimeException("Invalid Item Type");
            }

            /*
             * Remove colliding items.
             */
            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                CellLayout cl = mWorkspace.getScreenWithId(item.screenId);
                if (cl != null && cl.isOccupied(item.cellX, item.cellY)) {
                    View v = cl.getChildAt(item.cellX, item.cellY);
                    Object tag = v.getTag();
                    String desc = "Collision while binding workspace item: " + item
                            + ". Collides with " + tag;
                    if (FeatureFlags.IS_STUDIO_BUILD) {
                        throw (new RuntimeException(desc));
                    } else {
                        Log.d(TAG, desc);
                        getModelWriter().deleteItemFromDatabase(item);
                        continue;
                    }
                }
            }
            workspace.addInScreenFromBind(view, item);
            if (animateIcons) {
                // Animate all the applications up now
                view.setAlpha(0f);
                view.setScaleX(0f);
                view.setScaleY(0f);
                bounceAnims.add(createNewAppBounceAnimation(view, i));
                newItemsScreenId = item.screenId;
            }
        }

        // Animate to the correct page
        if (animateIcons && newItemsScreenId > -1) {
            AnimatorSet anim = new AnimatorSet();
            anim.playTogether(bounceAnims);

            int currentScreenId = mWorkspace.getScreenIdForPageIndex(mWorkspace.getNextPage());
            final int newScreenIndex = mWorkspace.getPageIndexForScreenId(newItemsScreenId);
            final Runnable startBounceAnimRunnable = anim::start;

            if (newItemsScreenId != currentScreenId) {
                // We post the animation slightly delayed to prevent slowdowns
                // when we are loading right after we return to launcher.
                mWorkspace.postDelayed(new Runnable() {
                    public void run() {
                        if (mWorkspace != null) {
                            closeOpenViews(false);

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
        workspace.requestLayout();
    }

    @Override
    public void bindPredictedItems(List<AppInfo> appInfos, IntArray ranks) { }

    /**
     * Add the views for a widget to the workspace.
     */
    public void bindAppWidget(LauncherAppWidgetInfo item) {
        View view = inflateAppWidget(item);
        if (view != null) {
            mWorkspace.addInScreen(view, item);
            mWorkspace.requestLayout();
        }
    }

    private View inflateAppWidget(LauncherAppWidgetInfo item) {
        if (item.hasOptionFlag(LauncherAppWidgetInfo.OPTION_SEARCH_WIDGET)) {
            item.providerName = QsbContainerView.getSearchComponentName(this);
            if (item.providerName == null) {
                getModelWriter().deleteItemFromDatabase(item);
                return null;
            }
        }
        final AppWidgetHostView view;
        if (mIsSafeModeEnabled) {
            view = new PendingAppWidgetHostView(this, item, mIconCache, true);
            prepareAppWidget(view, item);
            return view;
        }

        Object traceToken = TraceHelper.INSTANCE.beginSection("BIND_WIDGET_id=" + item.appWidgetId);

        try {
            final LauncherAppWidgetProviderInfo appWidgetInfo;

            if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)) {
                // If the provider is not ready, bind as a pending widget.
                appWidgetInfo = null;
            } else if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
                // The widget id is not valid. Try to find the widget based on the provider info.
                appWidgetInfo = mAppWidgetManager.findProvider(item.providerName, item.user);
            } else {
                appWidgetInfo = mAppWidgetManager.getLauncherAppWidgetInfo(item.appWidgetId);
            }

            // If the provider is ready, but the width is not yet restored, try to restore it.
            if (!item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)
                    && (item.restoreStatus != LauncherAppWidgetInfo.RESTORE_COMPLETED)) {
                if (appWidgetInfo == null) {
                    Log.d(TAG, "Removing restored widget: id=" + item.appWidgetId
                            + " belongs to component " + item.providerName
                            + ", as the provider is null");
                    getModelWriter().deleteItemFromDatabase(item);
                    return null;
                }

                // If we do not have a valid id, try to bind an id.
                if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
                    if (!item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_ALLOCATED)) {
                        // Id has not been allocated yet. Allocate a new id.
                        item.appWidgetId = mAppWidgetHost.allocateAppWidgetId();
                        item.restoreStatus |= LauncherAppWidgetInfo.FLAG_ID_ALLOCATED;

                        // Also try to bind the widget. If the bind fails, the user will be shown
                        // a click to setup UI, which will ask for the bind permission.
                        PendingAddWidgetInfo pendingInfo = new PendingAddWidgetInfo(appWidgetInfo);
                        pendingInfo.spanX = item.spanX;
                        pendingInfo.spanY = item.spanY;
                        pendingInfo.minSpanX = item.minSpanX;
                        pendingInfo.minSpanY = item.minSpanY;
                        Bundle options = WidgetHostViewLoader.getDefaultOptionsForWidget(this,
                                pendingInfo);

                        boolean isDirectConfig =
                                item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG);
                        if (isDirectConfig && item.bindOptions != null) {
                            Bundle newOptions = item.bindOptions.getExtras();
                            if (options != null) {
                                newOptions.putAll(options);
                            }
                            options = newOptions;
                        }
                        boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
                                item.appWidgetId, appWidgetInfo, options);

                        // We tried to bind once. If we were not able to bind, we would need to
                        // go through the permission dialog, which means we cannot skip the config
                        // activity.
                        item.bindOptions = null;
                        item.restoreStatus &= ~LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG;

                        // Bind succeeded
                        if (success) {
                            // If the widget has a configure activity, it is still needs to set it
                            // up, otherwise the widget is ready to go.
                            item.restoreStatus = (appWidgetInfo.configure == null) || isDirectConfig
                                    ? LauncherAppWidgetInfo.RESTORE_COMPLETED
                                    : LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
                        }

                        getModelWriter().updateItemInDatabase(item);
                    }
                } else if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_UI_NOT_READY)
                        && (appWidgetInfo.configure == null)) {
                    // The widget was marked as UI not ready, but there is no configure activity to
                    // update the UI.
                    item.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED;
                    getModelWriter().updateItemInDatabase(item);
                }
                else if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_UI_NOT_READY)
                        && appWidgetInfo.configure != null) {
                    if (mAppWidgetManager.isAppWidgetRestored(item.appWidgetId)) {
                        item.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED;
                        getModelWriter().updateItemInDatabase(item);
                    }
                }
            }

            if (item.restoreStatus == LauncherAppWidgetInfo.RESTORE_COMPLETED) {
                // Verify that we own the widget
                if (appWidgetInfo == null) {
                    FileLog.e(TAG, "Removing invalid widget: id=" + item.appWidgetId);
                    getModelWriter().deleteWidgetInfo(item, getAppWidgetHost());
                    return null;
                }

                item.minSpanX = appWidgetInfo.minSpanX;
                item.minSpanY = appWidgetInfo.minSpanY;
                view = mAppWidgetHost.createView(this, item.appWidgetId, appWidgetInfo);
            } else if (!item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)
                    && appWidgetInfo != null) {
                mAppWidgetHost.addPendingView(item.appWidgetId,
                        new PendingAppWidgetHostView(this, item, mIconCache, false));
                view = mAppWidgetHost.createView(this, item.appWidgetId, appWidgetInfo);
            } else {
                view = new PendingAppWidgetHostView(this, item, mIconCache, false);
            }
            prepareAppWidget(view, item);
        } finally {
            TraceHelper.INSTANCE.endSection(traceToken);
        }

        return view;
    }

    /**
     * Restores a pending widget.
     *
     * @param appWidgetId The app widget id
     */
    private LauncherAppWidgetInfo completeRestoreAppWidget(int appWidgetId, int finalRestoreFlag) {
        LauncherAppWidgetHostView view = mWorkspace.getWidgetForAppWidgetId(appWidgetId);
        if ((view == null) || !(view instanceof PendingAppWidgetHostView)) {
            Log.e(TAG, "Widget update called, when the widget no longer exists.");
            return null;
        }

        LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) view.getTag();
        info.restoreStatus = finalRestoreFlag;
        if (info.restoreStatus == LauncherAppWidgetInfo.RESTORE_COMPLETED) {
            info.pendingItemInfo = null;
        }

        if (((PendingAppWidgetHostView) view).isReinflateIfNeeded()) {
            view.reInflate();
        }

        getModelWriter().updateItemInDatabase(info);
        return info;
    }

    public void onPageBoundSynchronously(int page) {
        mSynchronouslyBoundPage = page;
        mWorkspace.setCurrentPage(page);
        mPageToBindSynchronously = PagedView.INVALID_PAGE;
    }

    @Override
    public void executeOnNextDraw(ViewOnDrawExecutor executor) {
        clearPendingBinds();
        mPendingExecutor = executor;
        if (!isInState(ALL_APPS)) {
            mAppsView.getAppsStore().enableDeferUpdates(AllAppsStore.DEFER_UPDATES_NEXT_DRAW);
            mPendingExecutor.execute(() -> mAppsView.getAppsStore().disableDeferUpdates(
                    AllAppsStore.DEFER_UPDATES_NEXT_DRAW));
        }

        executor.attachTo(this);
    }

    public void clearPendingExecutor(ViewOnDrawExecutor executor) {
        if (mPendingExecutor == executor) {
            mPendingExecutor = null;
        }
    }

    @Override
    public void finishFirstPageBind(final ViewOnDrawExecutor executor) {
        AlphaProperty property = mDragLayer.getAlphaProperty(ALPHA_INDEX_LAUNCHER_LOAD);
        if (property.getValue() < 1) {
            ObjectAnimator anim = ObjectAnimator.ofFloat(property, MultiValueAlpha.VALUE, 1);
            if (executor != null) {
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        executor.onLoadAnimationCompleted();
                    }
                });
            }
            anim.start();
        } else if (executor != null) {
            executor.onLoadAnimationCompleted();
        }
    }

    /**
     * Callback saying that there aren't any more items to bind.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void finishBindingItems(int pageBoundFirst) {
        Object traceToken = TraceHelper.INSTANCE.beginSection("finishBindingItems");
        mWorkspace.restoreInstanceStateForRemainingPages();

        setWorkspaceLoading(false);

        if (mPendingActivityResult != null) {
            handleActivityResult(mPendingActivityResult.requestCode,
                    mPendingActivityResult.resultCode, mPendingActivityResult.data);
            mPendingActivityResult = null;
        }

        InstallShortcutReceiver.disableAndFlushInstallQueue(
                InstallShortcutReceiver.FLAG_LOADER_RUNNING, this);

        // When undoing the removal of the last item on a page, return to that page.
        // Since we are just resetting the current page without user interaction,
        // override the previous page so we don't log the page switch.
        mWorkspace.setCurrentPage(pageBoundFirst, pageBoundFirst /* overridePrevPage */);
        mPageToBindSynchronously = PagedView.INVALID_PAGE;

        // Cache one page worth of icons
        getViewCache().setCacheSize(R.layout.folder_application,
                mDeviceProfile.inv.numFolderColumns * mDeviceProfile.inv.numFolderRows);
        getViewCache().setCacheSize(R.layout.folder_page, 2);

        TraceHelper.INSTANCE.endSection(traceToken);
    }

    private boolean canRunNewAppsAnimation() {
        if (mDragController.isDragging()) {
            return false;
        } else {
            return (System.currentTimeMillis() - mLastTouchUpTime)
                    > (NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS * 1000);
        }
    }

    private ValueAnimator createNewAppBounceAnimation(View v, int i) {
        ValueAnimator bounceAnim = new PropertyListBuilder().alpha(1).scale(1).build(v)
                .setDuration(InstallShortcutReceiver.NEW_SHORTCUT_BOUNCE_DURATION);
        bounceAnim.setStartDelay(i * InstallShortcutReceiver.NEW_SHORTCUT_STAGGER_DELAY);
        bounceAnim.setInterpolator(new OvershootInterpolator(BOUNCE_ANIMATION_TENSION));
        return bounceAnim;
    }

    private void announceForAccessibility(@StringRes int stringResId) {
        getDragLayer().announceForAccessibility(getString(stringResId));
    }

    /**
     * Add the icons for all apps.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindAllApplications(AppInfo[] apps, int flags) {
        mAppsView.getAppsStore().setApps(apps, flags);
    }

    /**
     * Copies LauncherModel's map of activities to shortcut counts to Launcher's. This is necessary
     * because LauncherModel's map is updated in the background, while Launcher runs on the UI.
     */
    @Override
    public void bindDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMapCopy) {
        mPopupDataProvider.setDeepShortcutMap(deepShortcutMapCopy);
    }

    @Override
    public void bindPromiseAppProgressUpdated(PromiseAppInfo app) {
        mAppsView.getAppsStore().updatePromiseAppProgress(app);
    }

    @Override
    public void bindWidgetsRestored(ArrayList<LauncherAppWidgetInfo> widgets) {
        mWorkspace.widgetsRestored(widgets);
    }

    /**
     * Some shortcuts were updated in the background.
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @param updated list of shortcuts which have changed.
     */
    @Override
    public void bindWorkspaceItemsChanged(ArrayList<WorkspaceItemInfo> updated) {
        if (!updated.isEmpty()) {
            mWorkspace.updateShortcuts(updated);
        }
    }

    /**
     * Update the state of a package, typically related to install state.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindRestoreItemsChange(HashSet<ItemInfo> updates) {
        mWorkspace.updateRestoreItems(updates);
    }

    /**
     * A package was uninstalled/updated.  We take both the super set of packageNames
     * in addition to specific applications to remove, the reason being that
     * this can be called when a package is updated as well.  In that scenario,
     * we only remove specific components from the workspace and hotseat, where as
     * package-removal should clear all items by package name.
     */
    @Override
    public void bindWorkspaceComponentsRemoved(final ItemInfoMatcher matcher) {
        mWorkspace.removeItemsByMatcher(matcher);
        mDragController.onAppsRemoved(matcher);
    }

    @Override
    public void bindAllWidgets(final ArrayList<WidgetListRowEntry> allWidgets) {
        mPopupDataProvider.setAllWidgets(allWidgets);
    }

    /**
     * @param packageUser if null, refreshes all widgets and shortcuts, otherwise only
     *                    refreshes the widgets and shortcuts associated with the given package/user
     */
    public void refreshAndBindWidgetsForPackageUser(@Nullable PackageUserKey packageUser) {
        mModel.refreshAndBindWidgetsAndShortcuts(packageUser);
    }

    /**
     * $ adb shell dumpsys activity com.android.launcher3.Launcher [--all]
     */
    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);

        if (args.length > 0 && TextUtils.equals(args[0], "--all")) {
            writer.println(prefix + "Workspace Items");
            for (int i = 0; i < mWorkspace.getPageCount(); i++) {
                writer.println(prefix + "  Homescreen " + i);

                ViewGroup layout = ((CellLayout) mWorkspace.getPageAt(i)).getShortcutsAndWidgets();
                for (int j = 0; j < layout.getChildCount(); j++) {
                    Object tag = layout.getChildAt(j).getTag();
                    if (tag != null) {
                        writer.println(prefix + "    " + tag.toString());
                    }
                }
            }

            writer.println(prefix + "  Hotseat");
            ViewGroup layout = mHotseat.getShortcutsAndWidgets();
            for (int j = 0; j < layout.getChildCount(); j++) {
                Object tag = layout.getChildAt(j).getTag();
                if (tag != null) {
                    writer.println(prefix + "    " + tag.toString());
                }
            }
        }

        writer.println(prefix + "Misc:");
        dumpMisc(prefix + "\t", writer);
        writer.println(prefix + "\tmWorkspaceLoading=" + mWorkspaceLoading);
        writer.println(prefix + "\tmPendingRequestArgs=" + mPendingRequestArgs
                + " mPendingActivityResult=" + mPendingActivityResult);
        writer.println(prefix + "\tmRotationHelper: " + mRotationHelper);
        writer.println(prefix + "\tmAppWidgetHost.isListening: " + mAppWidgetHost.isListening());

        // Extra logging for general debugging
        mDragLayer.dump(prefix, writer);
        mStateManager.dump(prefix, writer);
        mPopupDataProvider.dump(prefix, writer);

        try {
            FileLog.flushAll(writer);
        } catch (Exception e) {
            // Ignore
        }

        mModel.dumpState(prefix, fd, writer, args);

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.dump(prefix, fd, writer, args);
        }
        mOverlayManager.dump(prefix, writer);
    }

    @Override
    public void onProvideKeyboardShortcuts(
            List<KeyboardShortcutGroup> data, Menu menu, int deviceId) {

        ArrayList<KeyboardShortcutInfo> shortcutInfos = new ArrayList<>();
        if (isInState(NORMAL)) {
            shortcutInfos.add(new KeyboardShortcutInfo(getString(R.string.all_apps_button_label),
                    KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON));
            shortcutInfos.add(new KeyboardShortcutInfo(getString(R.string.widget_button_text),
                    KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON));
        }
        final View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            if (new CustomActionsPopup(this, currentFocus).canShow()) {
                shortcutInfos.add(new KeyboardShortcutInfo(getString(R.string.custom_actions),
                        KeyEvent.KEYCODE_O, KeyEvent.META_CTRL_ON));
            }
            if (currentFocus.getTag() instanceof ItemInfo
                    && ShortcutUtil.supportsShortcuts((ItemInfo) currentFocus.getTag())) {
                shortcutInfos.add(new KeyboardShortcutInfo(
                        getString(R.string.shortcuts_menu_with_notifications_description),
                        KeyEvent.KEYCODE_S, KeyEvent.META_CTRL_ON));
            }
        }
        if (!shortcutInfos.isEmpty()) {
            data.add(new KeyboardShortcutGroup(getString(R.string.home_screen), shortcutInfos));
        }

        super.onProvideKeyboardShortcuts(data, menu, deviceId);
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (event.hasModifiers(KeyEvent.META_CTRL_ON)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_A:
                    if (isInState(NORMAL)) {
                        getStateManager().goToState(ALL_APPS);
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_S: {
                    View focusedView = getCurrentFocus();
                    if (focusedView instanceof BubbleTextView
                            && focusedView.getTag() instanceof ItemInfo
                            && mAccessibilityDelegate.performAction(focusedView,
                            (ItemInfo) focusedView.getTag(),
                            LauncherAccessibilityDelegate.DEEP_SHORTCUTS)) {
                        PopupContainerWithArrow.getOpen(this).requestFocus();
                        return true;
                    }
                    break;
                }
                case KeyEvent.KEYCODE_O:
                    if (new CustomActionsPopup(this, getCurrentFocus()).show()) {
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_W:
                    if (isInState(NORMAL)) {
                        OptionsPopupView.openWidgets(this);
                        return true;
                    }
                    break;
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            // KEYCODE_MENU is sent by some tests, for example
            // LauncherJankTests#testWidgetsContainerFling. Don't just remove its handling.
            if (!mDragController.isDragging() && !mWorkspace.isSwitchingState() &&
                    isInState(NORMAL)) {
                // Close any open floating views.
                closeOpenViews();

                // Setting the touch point to (-1, -1) will show the options popup in the center of
                // the screen.
                if (Utilities.IS_RUNNING_IN_TEST_HARNESS) {
                    Log.d(TestProtocol.PERMANENT_DIAG_TAG, "Opening options popup on key up");
                }
                OptionsPopupView.showDefaultOptions(this, -1, -1);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    protected StateHandler<LauncherState>[] createStateHandlers() {
        return new StateHandler[] { getAllAppsController(), getWorkspace() };
    }

    public TouchController[] createTouchControllers() {
        return new TouchController[] {getDragController(), new AllAppsSwipeController(this)};
    }

    public void useFadeOutAnimationForLauncherStart(CancellationSignal signal) { }

    public void onDragLayerHierarchyChanged() { }

    @Override
    public void returnToHomescreen() {
        super.returnToHomescreen();
        getStateManager().goToState(LauncherState.NORMAL);
    }

    private void closeOpenViews() {
        closeOpenViews(true);
    }

    protected void closeOpenViews(boolean animate) {
        AbstractFloatingView.closeAllOpenViews(this, animate);
    }

    public Stream<SystemShortcut.Factory> getSupportedShortcuts() {
        return Stream.of(APP_INFO, WIDGETS, INSTALL);
    }


    /**
     * @see LauncherState#getOverviewScaleAndOffset(Launcher)
     */
    public float[] getNormalOverviewScaleAndOffset() {
        return new float[] {NO_SCALE, NO_OFFSET};
    }

    public static Launcher getLauncher(Context context) {
        return fromContext(context);
    }

    /**
     * Just a wrapper around the type cast to allow easier tracking of calls.
     */
    public static <T extends Launcher> T cast(ActivityContext activityContext) {
        return (T) activityContext;
    }


    /**
     * Callback for listening for onResume
     */
    public interface OnResumeCallback {

        void onLauncherResume();
    }
}
