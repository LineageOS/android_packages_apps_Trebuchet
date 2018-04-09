/*
 * Copyright (C) 2017 The LineageOS Project
 * Copyright (C) 2017 Paranoid Android
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
package com.android.launcher3.icons;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.launcher3.IconCache;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.QuickSettingsActivity;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.graphics.LauncherIcons;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class IconsHandler {
    private static final String TAG = "IconsHandler";
    private static String[] LAUNCHER_INTENTS = new String[] {
            "com.fede.launcher.THEME_ICONPACK",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON"
    };

    private Map<String, IconPackInfo> mIconPacks = new HashMap<>();
    private Map<String, String> mAppFilterDrawables = new HashMap<>();
    private List<Bitmap> mBackImages = new ArrayList<>();
    private List<String> mDrawables = new ArrayList<>();

    private Bitmap mFrontImage;
    private Bitmap mMaskImage;
    private Bitmap mTmpBitmap;

    private Resources mCurrentIconPackRes;
    private Resources mOriginalIconPackRes;
    private String mIconPackPackageName;

    private AlertDialog mAlertDialog;
    private Context mContext;
    private IconCache mIconCache;
    private PackageManager mPackageManager;
    private String mDefaultIconPack;

    private boolean mDialogShowing;
    private float mFactor = 1.0f;

    public IconsHandler(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mDefaultIconPack = context.getString(R.string.icon_pack_default);

        SharedPreferences prefs = Utilities.getPrefs(context.getApplicationContext());
        String iconPack = prefs.getString(QuickSettingsActivity.KEY_ICON_PACK, mDefaultIconPack);
        loadAvailableIconPacks();
        loadIconPack(iconPack, false);
    }

    private void loadIconPack(String packageName, boolean fallback) {
        mIconPackPackageName = packageName;
        if (!fallback) {
            mAppFilterDrawables.clear();
            mBackImages.clear();
            clearCache();
        } else {
            mDrawables.clear();
        }

        if (isDefaultIconPack()) {
            return;
        }

        XmlPullParser xpp = null;

        try {
            mOriginalIconPackRes = mPackageManager.getResourcesForApplication(mIconPackPackageName);
            mCurrentIconPackRes = mOriginalIconPackRes;
            int appfilterid = mOriginalIconPackRes.getIdentifier("appfilter", "xml",
                    mIconPackPackageName);
            if (appfilterid > 0) {
                xpp = mOriginalIconPackRes.getXml(appfilterid);
            }

            if (xpp != null) {
                int eventType = xpp.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if (!fallback & xpp.getName().equals("iconback")) {
                            for (int i = 0; i < xpp.getAttributeCount(); i++) {
                                if (xpp.getAttributeName(i).startsWith("img")) {
                                    String drawableName = xpp.getAttributeValue(i);
                                    Bitmap iconback = loadBitmap(drawableName);
                                    if (iconback != null) {
                                        mBackImages.add(iconback);
                                    }
                                }
                            }
                        } else if (!fallback && xpp.getName().equals("iconmask")) {
                            if (xpp.getAttributeCount() > 0 &&
                                    xpp.getAttributeName(0).equals("img1")) {
                                String drawableName = xpp.getAttributeValue(0);
                                mMaskImage = loadBitmap(drawableName);
                            }
                        } else if (!fallback && xpp.getName().equals("iconupon")) {
                            if (xpp.getAttributeCount() > 0 &&
                                    xpp.getAttributeName(0).equals("img1")) {
                                String drawableName = xpp.getAttributeValue(0);
                                mFrontImage = loadBitmap(drawableName);
                            }
                        } else if (!fallback && xpp.getName().equals("scale")) {
                            if (xpp.getAttributeCount() > 0 &&
                                    xpp.getAttributeName(0).equals("factor")) {
                                mFactor = Float.valueOf(xpp.getAttributeValue(0));
                            }
                        }
                        if (xpp.getName().equals("item")) {
                            String componentName = null;
                            String drawableName = null;

                            for (int i = 0; i < xpp.getAttributeCount(); i++) {
                                if (xpp.getAttributeName(i).equals("component")) {
                                    componentName = xpp.getAttributeValue(i);
                                } else if (xpp.getAttributeName(i).equals("drawable")) {
                                    drawableName = xpp.getAttributeValue(i);
                                }
                            }
                            if (fallback && getIdentifier(packageName, drawableName,
                                    true) > 0 && !mDrawables.contains(drawableName)) {
                                mDrawables.add(drawableName);
                            }
                            if (!fallback && componentName != null && drawableName != null &&
                                    !mAppFilterDrawables.containsKey(componentName)) {
                                mAppFilterDrawables.put(componentName, drawableName);
                            }
                        }
                    }
                    eventType = xpp.next();
                }
            }
        } catch (NameNotFoundException | XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing appfilter.xml " + e);
        }
    }

    public List<String> getAllDrawables(final String packageName) {
        loadAllDrawables(packageName);
        Collections.sort(mDrawables, String::compareToIgnoreCase);
        return mDrawables;
    }

    private void loadAllDrawables(String packageName) {
        mDrawables.clear();
        XmlPullParser xpp;
        try {
            Resources res = mPackageManager.getResourcesForApplication(packageName);
            mCurrentIconPackRes = res;
            int resource = res.getIdentifier("drawable", "xml", packageName);
            if (resource < 0) {
                return;
            }
            xpp = res.getXml(resource);
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if (xpp.getName().equals("item")) {
                        String drawableName = xpp.getAttributeValue(null, "drawable");
                        if (!mDrawables.contains(drawableName) &&
                                getIdentifier(packageName, drawableName, true) > 0) {
                            mDrawables.add(drawableName);
                        }
                    }
                }
                eventType = xpp.next();
            }
        } catch (NameNotFoundException | XmlPullParserException | IOException e) {
            Log.i(TAG, "Error parsing drawable.xml for package " + packageName +
                    " trying appfilter now");
            // fallback onto appfilter if drawable xml fails
            loadIconPack(packageName, true);
        }
    }

    public boolean isDefaultIconPack() {
        return mDefaultIconPack.equalsIgnoreCase(mIconPackPackageName) ||
                mIconPackPackageName.equals(mContext.getString(R.string.icon_pack_system));
    }

    public List<String> getMatchingDrawables(String packageName) {
        List<String> matchingDrawables = new ArrayList<>();
        ApplicationInfo info = null;
        try {
            info = mPackageManager.getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException ignored) {

        }
        String packageLabel = (info != null ? mPackageManager.getApplicationLabel(info).toString()
                : packageName).replaceAll("[^a-zA-Z]", "").toLowerCase().trim();
        for (String drawable : mDrawables) {
            if (drawable == null) continue;
            String filteredDrawable = drawable.replaceAll("[^a-zA-Z]",
                    "").toLowerCase().trim();
            if (filteredDrawable.length() > 2 && (packageLabel.contains(filteredDrawable) ||
                    filteredDrawable.contains(packageLabel))) {
                matchingDrawables.add(drawable);
            }
        }
        return matchingDrawables;
    }

    public int getIdentifier(String packageName, String drawableName, boolean currentIconPack) {
        if (drawableName == null) {
            return 0;
        }
        if (packageName == null) {
            packageName = mIconPackPackageName;
        }
        return (!currentIconPack ? mOriginalIconPackRes : mCurrentIconPackRes).getIdentifier(
                drawableName, "drawable", packageName);
    }

    public Drawable loadDrawable(String packageName, String drawableName, boolean currentIconPack) {
        if (packageName == null) {
            packageName = mIconPackPackageName;
        }

        final int id = getIdentifier(packageName, drawableName, currentIconPack);
        if (id <= 0) {
            return null;
        }

        return (!currentIconPack ? mOriginalIconPackRes : mCurrentIconPackRes).getDrawable(id);
    }

    private Bitmap loadBitmap(String drawableName) {
        Drawable bitmap = loadDrawable(null, drawableName, true);
        if (bitmap != null && bitmap instanceof BitmapDrawable) {
            return ((BitmapDrawable) bitmap).getBitmap();
        }
        return null;
    }

    private Bitmap getDefaultAppDrawable(ComponentName componentName, boolean isDefaultIconPack) {
        Drawable drawable = null;
        try {
            drawable = mPackageManager.getActivityIcon(componentName);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Unable to find component " + componentName.toString() + e);
        }
        if (drawable == null) {
            return null;
        }

        if (!isDefaultIconPack && drawable instanceof BitmapDrawable) {
            return generateBitmap(((BitmapDrawable) drawable).getBitmap());
        }

        /*
         * Welcome to HaxxWorld
         * The app is using adaptive icons, but the current icon pack doesn't have a valid
         * replacement. The original adaptive icon is loaded instead (in a non-ui thread
         * because the called method doesn't allow doing so)
         */
        if (Utilities.ATLEAST_OREO) {
            final Drawable icon = drawable;

            Thread iconLoader = new Thread(() -> {
                UserHandle userHandle = UserHandle.getUserHandleForUid(Process.myUid());
                mTmpBitmap = LauncherIcons.createBadgedIconBitmap(icon, userHandle, mContext,
                        Build.VERSION_CODES.O);
            });
            iconLoader.start();
            try {
                iconLoader.join();
                return mTmpBitmap;
            } catch (InterruptedException ignored) {
            }
        }

        return generateBitmap(Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888));
    }

    public void switchIconPacks(String packageName) {
        if (packageName.equals(mIconPackPackageName)) {
            packageName = mDefaultIconPack;
        }

        String localizedDefault = mContext.getString(R.string.icon_pack_system);
        if (packageName.equals(mDefaultIconPack) || packageName.equals(localizedDefault) ||
                mIconPacks.containsKey(packageName)) {
            new IconPackLoader(packageName).execute();
        }
    }

    public Bitmap getDrawableIconForPackage(ComponentName componentName) {
        if (isDefaultIconPack()) {
            return getDefaultAppDrawable(componentName, true);
        }

        // sth FUKY here

        String drawableName = mAppFilterDrawables.get(componentName.toString());
        Drawable drawable = loadDrawable(null, drawableName, false);
        if (drawable != null && drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            cacheStoreDrawable(componentName.toString(), bitmap);
            return bitmap;
        }

        Bitmap cachedIcon = cacheGetDrawable(componentName.toString());
        if (cachedIcon != null) {
            return cachedIcon;
        }

        return getDefaultAppDrawable(componentName, false);
    }

    private Bitmap generateBitmap(Bitmap defaultBitmap) {
        if (mBackImages.isEmpty()) {
            return defaultBitmap;
        }
        Random random = new Random();
        int id = random.nextInt(mBackImages.size());
        Bitmap backImage = mBackImages.get(id);
        int w = backImage.getWidth();
        int h = backImage.getHeight();

        Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(backImage, 0, 0, null);

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(defaultBitmap,
                (int) (w * mFactor), (int) (h * mFactor), false);

        Bitmap mutableMask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas maskCanvas = new Canvas(mutableMask);
        Bitmap targetBitmap = mMaskImage == null ? mutableMask : mMaskImage;
        maskCanvas.drawBitmap(targetBitmap, 0, 0, new Paint());

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        canvas.drawBitmap(scaledBitmap, (w - scaledBitmap.getWidth()) / 2,
                (h - scaledBitmap.getHeight()) / 2, null);
        canvas.drawBitmap(mutableMask, 0, 0, paint);

        if (mFrontImage != null) {
            canvas.drawBitmap(mFrontImage, 0, 0, null);
        }
        return result;
    }

    public Pair<List<String>, List<String>> getAllIconPacks() {
        // be sure to update the icon packs list
        loadAvailableIconPacks();

        List<String> iconPackNames = new ArrayList<>();
        List<String> iconPackLabels = new ArrayList<>();
        List<IconPackInfo> iconPacks = new ArrayList<>(mIconPacks.values());
        Collections.sort(iconPacks, (info, info2) ->
                info.label.toString().compareToIgnoreCase(info2.label.toString()));
        for (IconPackInfo info : iconPacks) {
            iconPackNames.add(info.packageName);
            iconPackLabels.add(info.label.toString());
        }
        return Pair.create(iconPackNames, iconPackLabels);
    }

    private void loadAvailableIconPacks() {
        List<ResolveInfo> launcherActivities = new ArrayList<>();
        mIconPacks.clear();

        for (String i : LAUNCHER_INTENTS) {
            launcherActivities.addAll(mPackageManager.queryIntentActivities(
                    new Intent(i), PackageManager.GET_META_DATA));
        }
        for (ResolveInfo ri : launcherActivities) {
            String packageName = ri.activityInfo.packageName;
            IconPackInfo info = new IconPackInfo(ri, mPackageManager);
            mIconPacks.put(packageName, info);
        }
    }

    private boolean isDrawableInCache(String key) {
        File drawableFile = cacheGetFileName(key);
        return drawableFile.isFile();
    }

    private void cacheStoreDrawable(String key, Bitmap bitmap) {
        if (isDrawableInCache(key)) {
            return;
        }

        File drawableFile = cacheGetFileName(key);

        try (FileOutputStream fos = new FileOutputStream(drawableFile)) {
            bitmap.compress(CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Unable to store drawable in cache " + e);
        }
    }

    private Bitmap cacheGetDrawable(String key) {
        if (!isDrawableInCache(key)) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(cacheGetFileName(key))) {
            BitmapDrawable drawable = new BitmapDrawable(mContext.getResources(),
                    BitmapFactory.decodeStream(fis));
            fis.close();
            return drawable.getBitmap();
        } catch (IOException e) {
            Log.e(TAG, "Unable to get drawable from cache " + e);
        }

        return null;
    }

    private File cacheGetFileName(String key) {
        return new File(getIconsCacheDir() + mIconPackPackageName
                + "_" + key.hashCode() + ".png");
    }

    private File getIconsCacheDir() {
        return new File(mContext.getCacheDir().getPath() + "/icons/");
    }

    private void clearCache() {
        File cacheDir = getIconsCacheDir();
        if (!cacheDir.isDirectory()) {
            return;
        }

        for (File item : cacheDir.listFiles()) {
            if (!item.delete()) {
                Log.w(TAG, "Failed to delete file: " + item.getAbsolutePath());
            }
        }
    }

    public void showDialog(Activity activity) {
        loadAvailableIconPacks();
        final IconAdapter adapter = new IconAdapter(mContext, mIconPacks);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.icon_pack_title)
                .setAdapter(adapter, (dialog, position) -> {
                    String selected = adapter.getItem(position);
                    String current = Utilities.getPrefs(mContext.getApplicationContext())
                            .getString(QuickSettingsActivity.KEY_ICON_PACK, mDefaultIconPack);
                    if (!selected.equals(current)) {
                        switchIconPacks(selected);
                    }
                });
        mAlertDialog = builder.create();
        mAlertDialog.show();
        mDialogShowing = true;
    }

    public void hideDialog() {
        if (mDialogShowing && mAlertDialog != null) {
            mAlertDialog.dismiss();
            mDialogShowing = false;
        }
    }

    private static class IconPackInfo {
        String packageName;
        CharSequence label;
        Drawable icon;

        IconPackInfo(ResolveInfo r, PackageManager packageManager) {
            packageName = r.activityInfo.packageName;
            icon = r.loadIcon(packageManager);
            label = r.loadLabel(packageManager);
        }

        private IconPackInfo(String label, Drawable icon, String packageName) {
            this.label = label;
            this.icon = icon;
            this.packageName = packageName;
        }
    }

    private static class IconAdapter extends BaseAdapter {
        ArrayList<IconPackInfo> mSupportedPackages;
        LayoutInflater mLayoutInflater;
        String mCurrentIconPack;

        IconAdapter(Context context, Map<String, IconPackInfo> supportedPackages) {
            mLayoutInflater = LayoutInflater.from(context);
            mSupportedPackages = new ArrayList<>(supportedPackages.values());
            Collections.sort(mSupportedPackages, (lhs, rhs) ->
                    lhs.label.toString().compareToIgnoreCase(rhs.label.toString()));

            Resources res = context.getResources();

            Drawable icon = res.getDrawable(android.R.mipmap.sym_def_app_icon);
            String defaultLabel = res.getString(R.string.icon_pack_system);

            mSupportedPackages.add(0, new IconPackInfo(defaultLabel, icon, defaultLabel));
            mCurrentIconPack = Utilities.getPrefs(context.getApplicationContext())
                    .getString(QuickSettingsActivity.KEY_ICON_PACK,
                            res.getString(R.string.icon_pack_default));
        }

        @Override
        public int getCount() {
            return mSupportedPackages.size();
        }

        @Override
        public String getItem(int position) {
            return mSupportedPackages.get(position).packageName;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.target_edit_iconpack_chooser, null);
            }
            IconPackInfo info = mSupportedPackages.get(position);
            TextView txtView = (TextView) convertView.findViewById(R.id.title);
            txtView.setText(info.label);
            ImageView imgView = (ImageView) convertView.findViewById(R.id.icon);
            imgView.setImageDrawable(info.icon);
            RadioButton radioButton = (RadioButton) convertView.findViewById(R.id.radio);
            radioButton.setChecked(info.packageName.equals(mCurrentIconPack));
            return convertView;
        }
    }

    private class IconPackLoader extends AsyncTask<Void, Void, Void> {
        private String mIconPackPackageName;

        private IconPackLoader(String packageName) {
            mIconPackPackageName = packageName;
            mIconCache = LauncherAppState.getInstance(mContext).getIconCache();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            loadIconPack(mIconPackPackageName, false);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Utilities.getPrefs(mContext.getApplicationContext()).edit()
                    .putString(QuickSettingsActivity.KEY_ICON_PACK, mIconPackPackageName).apply();
            mIconCache.clearIconDataBase();
            mIconCache.flush();
            LauncherAppState.getInstance(mContext).getModel().forceReload();
        }
    }
}
