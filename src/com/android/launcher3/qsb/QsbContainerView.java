/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.qsb;

import static android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND;
import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID;
import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER;

import android.app.Activity;
import android.app.Fragment;
import android.app.SearchManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.launcher3.AppWidgetResizeFrame;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.FragmentWithPreview;

/**
 * A frame layout which contains a QSB. This internally uses fragment to bind the view, which
 * allows it to contain the logic for {@link Fragment#startActivityForResult(Intent, int)}.
 *
 * Note: AppWidgetManagerCompat can be disabled using FeatureFlags. In QSB, we should use
 * AppWidgetManager directly, so that it keeps working in that case.
 */
public class QsbContainerView extends FrameLayout {
    private static final String QSB_PROVIDER_CLASS
            = "com.google.android.googlequicksearchbox.SearchWidgetProvider";

    public QsbContainerView(Context context) {
        super(context);
    }

    public QsbContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public QsbContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(0, 0, 0, 0);
    }

    protected void setPaddingUnchecked(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
    }

    public static void updateDefaultLayout(Context context, AppWidgetProviderInfo info) {
        ComponentName provider = info.provider;
        if (QSB_PROVIDER_CLASS.equals(provider.getClassName())) {
            try {
                ActivityInfo activityInfo =
                    context.getPackageManager().getReceiverInfo(provider,
                        PackageManager.GET_META_DATA);
                Bundle metaData = activityInfo.metaData;
                int resId = metaData.getInt(
                    "com.google.android.gsa.searchwidget.alt_initial_layout_cqsb", -1);
                if (resId != -1) {
                    info.initialLayout = resId;
                }
            } catch (Exception e) {
                // Ignore the exception since if any exceptions happen
                //  the original initialyLayout would be used.
            }
        }
    }

    /**
     * A fragment to display the QSB.
     */
    public static class QsbFragment extends FragmentWithPreview {

        public static final int QSB_WIDGET_HOST_ID = 1026;
        private static final int REQUEST_BIND_QSB = 1;

        protected String mKeyWidgetId = "qsb_widget_id";
        private QsbWidgetHost mQsbWidgetHost;
        private AppWidgetProviderInfo mWidgetInfo;
        private QsbWidgetHostView mQsb;

        // We need to store the orientation here, due to a bug (b/64916689) that results in widgets
        // being inflated in the wrong orientation.
        private int mOrientation;

        @Override
        public void onInit(Bundle savedInstanceState) {
            mQsbWidgetHost = createHost();
            mOrientation = getContext().getResources().getConfiguration().orientation;
        }

        protected QsbWidgetHost createHost() {
            return new QsbWidgetHost(getContext(), QSB_WIDGET_HOST_ID,
                    (c) -> new QsbWidgetHostView(c));
        }

        private FrameLayout mWrapper;

        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            mWrapper = new FrameLayout(getContext());

            // Only add the view when enabled
            if (isQsbEnabled()) {
                mWrapper.addView(createQsb(mWrapper));
            }
            return mWrapper;
        }

        private View createQsb(ViewGroup container) {
            mWidgetInfo = getSearchWidgetProvider();
            if (mWidgetInfo == null) {
                // There is no search provider, just show the default widget.
                return getDefaultView(container, false /* show setup icon */);
            }
            Bundle opts = createBindOptions();
            // round quick search bar
            opts.putString("attached-launcher-identifier",
                    getActivity().getPackageName());
            opts.putString("requested-widget-style", "cqsb");

            Context context = getContext();
            AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);

            int widgetId = Utilities.getPrefs(context).getInt(mKeyWidgetId, -1);
            AppWidgetProviderInfo widgetInfo = widgetManager.getAppWidgetInfo(widgetId);
            boolean isWidgetBound = (widgetInfo != null) &&
                    widgetInfo.provider.equals(mWidgetInfo.provider);

            int oldWidgetId = widgetId;
            if (!isWidgetBound && !isInPreviewMode()) {
                if (widgetId > -1) {
                    // widgetId is already bound and its not the correct provider. reset host.
                    mQsbWidgetHost.deleteHost();
                }

                widgetId = mQsbWidgetHost.allocateAppWidgetId();
                isWidgetBound = widgetManager.bindAppWidgetIdIfAllowed(
                        widgetId, mWidgetInfo.getProfile(), mWidgetInfo.provider, opts);
                if (!isWidgetBound) {
                    mQsbWidgetHost.deleteAppWidgetId(widgetId);
                    widgetId = -1;
                }

                if (oldWidgetId != widgetId) {
                    saveWidgetId(widgetId);
                }
            }

            if (isWidgetBound) {
                mQsb = (QsbWidgetHostView) mQsbWidgetHost.createView(context, widgetId, mWidgetInfo);
                mQsb.setId(R.id.qsb_widget);

                if (!isInPreviewMode()) {
                    if (!containsAll(AppWidgetManager.getInstance(context)
                            .getAppWidgetOptions(widgetId), opts)) {
                        mQsb.updateAppWidgetOptions(opts);
                    }
                    mQsbWidgetHost.startListening();
                }
                return mQsb;
            }

            // Return a default widget with setup icon.
            return getDefaultView(container, true /* show setup icon */);
        }

        private void saveWidgetId(int widgetId) {
            Utilities.getPrefs(getContext()).edit().putInt(mKeyWidgetId, widgetId).apply();
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == REQUEST_BIND_QSB) {
                if (resultCode == Activity.RESULT_OK) {
                    saveWidgetId(data.getIntExtra(EXTRA_APPWIDGET_ID, -1));
                    rebindFragment();
                } else {
                    mQsbWidgetHost.deleteHost();
                }
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            if (mQsb != null && mQsb.isReinflateRequired(mOrientation)) {
                rebindFragment();
            }
        }

        @Override
        public void onDestroy() {
            mQsbWidgetHost.stopListening();
            super.onDestroy();
        }

        private void rebindFragment() {
            // Exit if the embedded qsb is disabled
            if (!isQsbEnabled()) {
                return;
            }

            if (mWrapper != null && getContext() != null) {
                mWrapper.removeAllViews();
                mWrapper.addView(createQsb(mWrapper));
            }
        }

        public boolean isQsbEnabled() {
            return FeatureFlags.QSB_ON_FIRST_SCREEN;
        }

        protected Bundle createBindOptions() {
            InvariantDeviceProfile idp = LauncherAppState.getIDP(getContext());

            Bundle opts = new Bundle();
            Rect size = AppWidgetResizeFrame.getWidgetSizeRanges(getContext(),
                    idp.numColumns, 1, null);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size.left);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size.top);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size.right);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size.bottom);
            return opts;
        }

        protected View getDefaultView(ViewGroup container, boolean showSetupIcon) {
            // Return a default widget with setup icon.
            View v = QsbWidgetHostView.getDefaultView(container);
            if (showSetupIcon) {
                View setupButton = v.findViewById(R.id.btn_qsb_setup);
                setupButton.setVisibility(View.VISIBLE);
                setupButton.setOnClickListener((v2) -> startActivityForResult(
                        new Intent(ACTION_APPWIDGET_BIND)
                                .putExtra(EXTRA_APPWIDGET_ID, mQsbWidgetHost.allocateAppWidgetId())
                                .putExtra(EXTRA_APPWIDGET_PROVIDER, mWidgetInfo.provider),
                        REQUEST_BIND_QSB));
            }
            return v;
        }

        /**
         * Returns a widget with category {@link AppWidgetProviderInfo#WIDGET_CATEGORY_SEARCHBOX}
         * provided by the same package which is set to be global search activity.
         * If widgetCategory is not supported, or no such widget is found, returns the first widget
         * provided by the package.
         */
        protected AppWidgetProviderInfo getSearchWidgetProvider() {
            SearchManager searchManager =
                    (SearchManager) getContext().getSystemService(Context.SEARCH_SERVICE);
            ComponentName searchComponent = searchManager.getGlobalSearchActivity();
            if (searchComponent == null) return null;
            String providerPkg = searchComponent.getPackageName();

            AppWidgetProviderInfo defaultWidgetForSearchPackage = null;

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getContext());
            for (AppWidgetProviderInfo info : appWidgetManager.getInstalledProviders()) {
                if (info.provider.getPackageName().equals(providerPkg) && info.configure == null) {
                    if ((info.widgetCategory
                            & AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX) != 0) {
                        return info;
                    } else if (defaultWidgetForSearchPackage == null) {
                        defaultWidgetForSearchPackage = info;
                    }
                }
            }
            return defaultWidgetForSearchPackage;
        }
    }

    public static class QsbWidgetHost extends AppWidgetHost {

        private final WidgetViewFactory mViewFactory;

        public QsbWidgetHost(Context context, int hostId, WidgetViewFactory viewFactory) {
            super(context, hostId);
            mViewFactory = viewFactory;
        }

        @Override
        protected AppWidgetHostView onCreateView(
                Context context, int appWidgetId, AppWidgetProviderInfo appWidget) {
            return mViewFactory.newView(context);
        }
    }

    public interface WidgetViewFactory {

        QsbWidgetHostView newView(Context context);
    }

    /**
     * Returns true if {@param original} contains all entries defined in {@param updates} and
     * have the same value.
     * The comparison uses {@link Object#equals(Object)} to compare the values.
     */
    private static boolean containsAll(Bundle original, Bundle updates) {
        for (String key : updates.keySet()) {
            Object value1 = updates.get(key);
            Object value2 = original.get(key);
            if (value1 == null) {
                if (value2 != null) {
                    return false;
                }
            } else if (!value1.equals(value2)) {
                return false;
            }
        }
        return true;
    }
}
