/*
 * Copyright (C) 2020 The LineageOS Project
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
package com.android.launcher3.lineage;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import android.os.Bundle;

import android.view.View;
import android.view.ViewGroup;

import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.android.launcher3.R;

import java.util.Collections;
import java.util.List;

public class TvLineageLauncher extends AppCompatActivity {

    protected BrowseSupportFragment mBrowseSupportFragment;
    private ArrayObjectAdapter rowsAdapter;
    private Context mContext = null;
    private AppAdapter appRowAdapter = null;
    private PackageManager pm = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tvlauncher);

        mContext = this;
        pm = getPackageManager();

        mBrowseSupportFragment = (BrowseSupportFragment) getSupportFragmentManager().findFragmentById(R.id.browse_support_fragment);
        mBrowseSupportFragment.setHeadersState(BrowseSupportFragment.HEADERS_DISABLED);
        mBrowseSupportFragment.setTitle(getString(R.string.derived_app_name));

        rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> launchables = pm.queryIntentActivities(main, 0);
        Collections.sort(launchables, new ResolveInfo.DisplayNameComparator(pm));
	appRowAdapter = new AppAdapter(new AppPresenter(), launchables);

	String headerName = getResources().getString(R.string.tv_header_app);
        HeaderItem header = new HeaderItem(0, headerName);
        rowsAdapter.add(new ListRow(header, appRowAdapter));

        mBrowseSupportFragment.setAdapter(rowsAdapter);
        mBrowseSupportFragment.setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                ActivityInfo activity = ((ResolveInfo) item).activityInfo;
                Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(
                        activity.applicationInfo.packageName);
                if (launchIntent != null) {
                    launchIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
		    launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(launchIntent);
                }
            }
        });
    }

    class AppAdapter extends ArrayObjectAdapter {
        AppAdapter(AppPresenter presenter, List<ResolveInfo> apps) {
            super(presenter);
	    addAll(0, apps);
        }
    }

    public class AppPresenter extends Presenter {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            ImageCardView cardView = new ImageCardView(mContext) {
                @Override
                public void setSelected(boolean selected) {
                    int selected_background = mContext.getResources().getColor(R.color.focused_background);
                    int default_background = mContext.getResources().getColor(R.color.icon_background);
                    int color = selected ? selected_background : default_background;
                    findViewById(R.id.info_field).setBackgroundColor(color);
                    super.setSelected(selected);
                }
            };
            cardView.setFocusable(true);
            cardView.setFocusableInTouchMode(true);
            return new ViewHolder(cardView);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            ImageCardView cardView = (ImageCardView) viewHolder.view;
            cardView.setMainImageDimensions(320,180);
            ResolveInfo appInfo = (ResolveInfo) item;
            cardView.setMainImageScaleType(ImageView.ScaleType.CENTER_INSIDE);
            cardView.getMainImageView().setImageDrawable(appInfo.loadIcon(pm));
            cardView.setTitleText(appInfo.loadLabel(pm));
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
            ImageCardView cardView = (ImageCardView) viewHolder.view;
            cardView.setBadgeImage(null);
            cardView.setMainImage(null);
        }
    }
}
