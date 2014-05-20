package com.android.launcher3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.android.launcher3.settings.SettingsProvider;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class HiddenFolderActivity extends Activity {
    public static final String HIDDEN_FOLDER_NAME = "hiddenFolderName";
    public static final String HIDDEN_FOLDER_STATUS = "hiddenFolderStatus";
    public static final String HIDDEN_FOLDER_INFO = "hiddenFolderInfo";
    public static final String HIDDEN_FOLDER_INFO_TITLES = "hiddenFolderInfoTitles";
    public static final String HIDDEN_FOLDER_LAUNCH = "hiddenFolderLaunchPosition";

    private static final int REQ_CREATE_PATTERN = 1;
    private static final int REQ_ENTER_PATTERN = 2;

    private String[] mComponentInfo;
    private String[] mComponentTitles;
    private boolean mHidden;
    private PackageManager mPackageManager;
    private AppsAdapter mAppsAdapter;
    private ArrayList<AppEntry> mAppEntries;
    private String mPatternUnlock;

    private OnClickListener mClicklistener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mHidden = !mHidden;
            if (mHidden && mPatternUnlock.equals("")) {
                // One time unlock pattern
                Intent intent = new Intent(
                        LockPatternActivity.ACTION_CREATE_PATTERN, null,
                        getApplicationContext(), LockPatternActivity.class);
                startActivityForResult(intent, REQ_CREATE_PATTERN);
            } else {
                setReturn(-1);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hidden_folder);

        mPackageManager = getPackageManager();

        Intent intent = getIntent();
        mComponentInfo = intent.getStringArrayExtra(HIDDEN_FOLDER_INFO);
        mComponentTitles = intent
                .getStringArrayExtra(HIDDEN_FOLDER_INFO_TITLES);
        mHidden = intent.getBooleanExtra(HIDDEN_FOLDER_STATUS, false);
        String title = intent.getStringExtra(HIDDEN_FOLDER_NAME);
        title = title
                + " ("
                + (mHidden ? getResources().getString(R.string.hidden_folder)
                        : getResources().getString(R.string.unhidden_folder))
                + ")";

        TextView titleTextView = (TextView) findViewById(R.id.folder_name);
        titleTextView.setText(title);

        mAppsAdapter = new AppsAdapter(this, R.layout.hidden_apps_list_item);
        mAppsAdapter.setNotifyOnChange(true);
        mAppEntries = loadApps();
        mAppsAdapter.clear();
        mAppsAdapter.addAll(mAppEntries);

        ListView list = (ListView) findViewById(R.id.hidden_apps_list);
        list.setAdapter(mAppsAdapter);

        RelativeLayout titleBar = (RelativeLayout) findViewById(R.id.folder_title_lock);
        titleBar.setOnClickListener(mClicklistener);

        // If this folder is already hidden, validate with the pattern
        mPatternUnlock = SettingsProvider.getStringCustomDefault(this,
                SettingsProvider.SETTINGS_UI_DRAWER_HIDDEN_APPS_UNLOCK, "");
        if (mHidden && !mPatternUnlock.equals("")) {
            Intent lockPattern = new Intent(
                    LockPatternActivity.ACTION_COMPARE_PATTERN, null,
                    getApplicationContext(), LockPatternActivity.class);
            lockPattern.putExtra(LockPatternActivity.EXTRA_PATTERN,
                    mPatternUnlock);
            startActivityForResult(lockPattern, REQ_ENTER_PATTERN);
        }
    }

    private ArrayList<AppEntry> loadApps() {
        ArrayList<AppEntry> apps = new ArrayList<AppEntry>();
        int size = mComponentInfo.length;
        for (int i = 0; i < size; i++) {
            apps.add(new AppEntry(mComponentInfo[i], mComponentTitles[i]));
        }
        return apps;
    }

    private void setReturn(int position) {
        Intent returnIntent = new Intent();
        returnIntent.putExtra(HIDDEN_FOLDER_STATUS, mHidden);
        returnIntent.putExtra(HIDDEN_FOLDER_LAUNCH, position);
        setResult(RESULT_OK, returnIntent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQ_CREATE_PATTERN:
                switch (resultCode) {
                    case RESULT_OK:
                        mPatternUnlock = data
                                .getStringExtra(LockPatternActivity.EXTRA_PATTERN);
                        SettingsProvider.putString(this,
                                SettingsProvider.SETTINGS_UI_DRAWER_HIDDEN_APPS_UNLOCK,
                                mPatternUnlock);
                        setReturn(-1);
                        break;
                    case RESULT_CANCELED:
                        // user failed to define a pattern, do not lock the folder
                        mHidden = false;
                        setReturn(-1);
                        break;
                }
                break;
            case REQ_ENTER_PATTERN:
                switch (resultCode) {
                    case RESULT_OK:
                        // user passed, continue!
                        break;
                    case RESULT_CANCELED:
                        // The user couldn't unlock
                        setReturn(-1);
                        break;
                }
                break;
        }
    }

    public class AppsAdapter extends ArrayAdapter<AppEntry> {

        private final LayoutInflater mInflator;

        private ConcurrentHashMap<String, Drawable> mIcons;
        private Drawable mDefaultImg;
        private List<AppEntry> mApps;

        public AppsAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);

            mApps = new ArrayList<AppEntry>();

            mInflator = LayoutInflater.from(context);

            // set the default icon till the actual app icon is loaded in async
            // task
            mDefaultImg = context.getResources().getDrawable(
                    android.R.mipmap.sym_def_app_icon);
            mIcons = new ConcurrentHashMap<String, Drawable>();
        }

        @Override
        public View getView(final int position, View convertView,
                ViewGroup parent) {
            final AppViewHolder viewHolder;

            if (convertView == null) {
                convertView = mInflator.inflate(
                        R.layout.hidden_folder_apps_list_item, parent, false);
                viewHolder = new AppViewHolder(convertView, position);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (AppViewHolder) convertView.getTag();
            }

            AppEntry app = getItem(position);

            viewHolder.title.setText(app.title);

            Drawable icon = mIcons.get(app.componentName.getPackageName());
            viewHolder.icon.setImageDrawable(icon != null ? icon : mDefaultImg);

            convertView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    setReturn(viewHolder.position);
                }
            });

            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            // If we have new items, we have to load their icons
            // If items were deleted, remove them from our mApps
            List<AppEntry> newApps = new ArrayList<AppEntry>(getCount());
            List<AppEntry> oldApps = new ArrayList<AppEntry>(getCount());
            for (int i = 0; i < getCount(); i++) {
                AppEntry app = getItem(i);
                if (mApps.contains(app)) {
                    oldApps.add(app);
                } else {
                    newApps.add(app);
                }
            }

            if (newApps.size() > 0) {
                new LoadIconsTask().execute(newApps.toArray(new AppEntry[] {}));
                newApps.addAll(oldApps);
                mApps = newApps;
            } else {
                mApps = oldApps;
            }
        }

        /**
         * An asynchronous task to load the icons of the installed applications.
         */
        private class LoadIconsTask extends AsyncTask<AppEntry, Void, Void> {
            @Override
            protected Void doInBackground(AppEntry... apps) {
                for (AppEntry app : apps) {
                    try {
                        if (mIcons.containsKey(app.componentName
                                .getPackageName())) {
                            continue;
                        }
                        Drawable icon = mPackageManager
                                .getApplicationIcon(app.componentName
                                        .getPackageName());
                        mIcons.put(app.componentName.getPackageName(), icon);
                        publishProgress();
                    } catch (PackageManager.NameNotFoundException e) {
                        // ignored; app will show up with default image
                    }
                }

                return null;
            }

            @Override
            protected void onProgressUpdate(Void... progress) {
                notifyDataSetChanged();
            }
        }
    }

    private final class AppEntry {

        public final ComponentName componentName;
        public final String title;

        public AppEntry(String component, String title) {
            componentName = ComponentName.unflattenFromString(component);
            this.title = title;
        }
    }

    private static class AppViewHolder {
        public final TextView title;
        public final ImageView icon;
        public final int position;

        public AppViewHolder(View parentView, int position) {
            icon = (ImageView) parentView.findViewById(R.id.icon);
            title = (TextView) parentView.findViewById(R.id.title);
            this.position = position;
        }
    }
}
