package com.android.launcher3;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.launcher3.settings.SettingsProvider;

public class IconPackHelper {

    static final String ICON_MASK_TAG = "iconmask";
    static final String ICON_BACK_TAG = "iconback";
    static final String ICON_UPON_TAG = "iconupon";
    static final String ICON_SCALE_TAG = "scale";

    public final static String[] sSupportedActions = new String[] {
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme"
    };

    public static final String[] sSupportedCategories = new String[] {
        "com.fede.launcher.THEME_ICONPACK",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME"
    };

    // Holds package/class -> drawable
    private Map<String, String> mIconPackResources;
    private final Context mContext;
    private String mLoadedIconPackName;
    private Resources mLoadedIconPackResource;
    private Drawable mIconBack, mIconUpon, mIconMask;
    private float mIconScale;

    public Drawable getIconBack() {
        return mIconBack;
    }

    public Drawable getIconMask() {
        return mIconMask;
    }

    public Drawable getIconUpon() {
        return mIconUpon;
    }

    public float getIconScale() {
        return mIconScale;
    }

    IconPackHelper(Context context) {
        mContext = context;
        mIconPackResources = new HashMap<String, String>();
    }

    private Drawable getDrawableForName(String name) {
        if (isIconPackLoaded()) {
            String item = mIconPackResources.get(name);
            if (!TextUtils.isEmpty(item)) {
                int id = getResourceIdForDrawable(item);
                if (id != 0) {
                    return mLoadedIconPackResource.getDrawable(id);
                }
            }
        }
        return null;
    }

    public static List<IconPackInfo> getSupportedPackages(Context context) {
        Intent i = new Intent();
        Map<String, IconPackInfo> packages = new HashMap<String, IconPackInfo>();
        PackageManager packageManager = context.getPackageManager();
        for (String action : sSupportedActions) {
            i.setAction(action);
            for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
                IconPackInfo info = new IconPackInfo(r, packageManager);
                packages.put(r.activityInfo.packageName, info);
            }
        }
        i = new Intent(Intent.ACTION_MAIN);
        for (String category : sSupportedCategories) {
            i.addCategory(category);
            for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
                IconPackInfo info = new IconPackInfo(r, packageManager);
                packages.put(r.activityInfo.packageName, info);
            }
            i.removeCategory(category);
        }
        return new ArrayList<IconPackInfo>(packages.values());
    }

    private static void loadResourcesFromXmlParser(XmlPullParser parser,
            Map<String, String> iconPackResources) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        do {

            if (eventType != XmlPullParser.START_TAG) {
                continue;
            }

            if (parser.getName().equalsIgnoreCase(ICON_MASK_TAG) ||
                    parser.getName().equalsIgnoreCase(ICON_BACK_TAG) ||
                    parser.getName().equalsIgnoreCase(ICON_UPON_TAG)) {
                String icon = parser.getAttributeValue(null, "img");
                if (icon == null) {
                    if (parser.getAttributeCount() == 1) {
                        icon = parser.getAttributeValue(0);
                    }
                }
                iconPackResources.put(parser.getName().toLowerCase(), icon);
                continue;
            }

            if (parser.getName().equalsIgnoreCase(ICON_SCALE_TAG)) {
                String factor = parser.getAttributeValue(null, "factor");
                if (factor == null) {
                    if (parser.getAttributeCount() == 1) {
                        factor = parser.getAttributeValue(0);
                    }
                }
                iconPackResources.put(parser.getName().toLowerCase(), factor);
                continue;
            }

            if (!parser.getName().equalsIgnoreCase("item")) {
                continue;
            }

            String component = parser.getAttributeValue(null, "component");
            String drawable = parser.getAttributeValue(null, "drawable");

            // Validate component/drawable exist
            if (TextUtils.isEmpty(component) || TextUtils.isEmpty(drawable)) {
                continue;
            }

            // Validate format/length of component
            if (!component.startsWith("ComponentInfo{") || !component.endsWith("}")
                    || component.length() < 16) {
                continue;
            }

            // Sanitize stored value
            component = component.substring(14, component.length() - 1).toLowerCase();

            ComponentName name = null;
            if (!component.contains("/")) {
                // Package icon reference
                iconPackResources.put(component, drawable);
            } else {
                name = ComponentName.unflattenFromString(component);
                if (name != null) {
                    iconPackResources.put(name.getPackageName(), drawable);
                    iconPackResources.put(name.getPackageName() + "." + name.getClassName(), drawable);
                }
            }
        } while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT);
    }

    private static void loadApplicationResources(Context context,
            Map<String, String> iconPackResources, String packageName) {
        Field[] drawableItems = null;
        try {
            Context appContext = context.createPackageContext(packageName,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            drawableItems = Class.forName(packageName+".R$drawable",
                    true, appContext.getClassLoader()).getFields();
        } catch (Exception e){
            return;
        }

        for (Field f : drawableItems) {
            String name = f.getName();

            String icon = name.toLowerCase();
            name = name.replaceAll("_", ".");

            iconPackResources.put(name, icon);

            int activityIndex = name.lastIndexOf(".");
            if (activityIndex <= 0 || activityIndex == name.length() - 1) {
                continue;
            }

            String iconPackage = name.substring(0, activityIndex);
            if (TextUtils.isEmpty(iconPackage)) {
                continue;
            }
            iconPackResources.put(iconPackage, icon);

            String iconActivity = name.substring(activityIndex + 1);
            if (TextUtils.isEmpty(iconActivity)) {
                continue;
            }
            iconPackResources.put(iconPackage + "." + iconActivity, icon);
        }
    }

    public boolean loadIconPack(String packageName) {
        mIconPackResources = getIconPackResources(mContext, packageName);
        Resources res = null;
        try {
            res = mContext.getPackageManager().getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        mLoadedIconPackResource = res;
        mLoadedIconPackName = packageName;
        mIconBack = getDrawableForName(ICON_BACK_TAG);
        mIconMask = getDrawableForName(ICON_MASK_TAG);
        mIconUpon = getDrawableForName(ICON_UPON_TAG);
        String scale = mIconPackResources.get(ICON_SCALE_TAG);
        if (scale != null) {
            try {
                mIconScale = Float.valueOf(scale);
            } catch (NumberFormatException e) {
            }
        }
        return true;
    }

    public static Map<String, String> getIconPackResources(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }

        Resources res = null;
        try {
            res = context.getPackageManager().getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        XmlPullParser parser = null;
        InputStream inputStream = null;
        Map<String, String> iconPackResources = new HashMap<String, String>();

        try {
            inputStream = res.getAssets().open("appfilter.xml");
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            parser = factory.newPullParser();
            parser.setInput(inputStream, "UTF-8");
        } catch (Exception e) {
            // Catch any exception since we want to fall back to parsing the xml/
            // resource in all cases
            int resId = res.getIdentifier("appfilter", "xml", packageName);
            if (resId != 0) {
                parser = res.getXml(resId);
            }
        }

        if (parser != null) {
            try {
                  loadResourcesFromXmlParser(parser, iconPackResources);
                  return iconPackResources;
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // Cleanup resources
                if (parser instanceof XmlResourceParser) {
                    ((XmlResourceParser) parser).close();
                }
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        // Application uses a different theme format (most likely launcher pro)
        int arrayId = res.getIdentifier("theme_iconpack", "array", packageName);
        if (arrayId == 0) {
            arrayId = res.getIdentifier("icon_pack", "array", packageName);
        }

        if (arrayId != 0) {
            String[] iconPack = res.getStringArray(arrayId);
            for (String entry : iconPack) {

                if (TextUtils.isEmpty(entry)) {
                    continue;
                }

                String icon = entry.toLowerCase();
                entry = entry.replaceAll("_", ".");

                iconPackResources.put(entry, icon);

                int activityIndex = entry.lastIndexOf(".");
                if (activityIndex <= 0 || activityIndex == entry.length() - 1) {
                    continue;
                }

                String iconPackage = entry.substring(0, activityIndex);
                if (TextUtils.isEmpty(iconPackage)) {
                    continue;
                }
                iconPackResources.put(iconPackage, icon);

                String iconActivity = entry.substring(activityIndex + 1);
                if (TextUtils.isEmpty(iconActivity)) {
                    continue;
                }
                iconPackResources.put(iconPackage + "." + iconActivity, icon);
            }
        } else {
            loadApplicationResources(context, iconPackResources, packageName);
        }
        return iconPackResources;
    }

    public void unloadIconPack() {
        mLoadedIconPackResource = null;
        mLoadedIconPackName = null;
        mIconPackResources = null;
        mIconMask = null;
        mIconBack = null;
        mIconUpon = null;
        mIconScale = 1f;
    }

    public static void pickIconPack(final Context context) {
        final List<IconPackInfo> supportedPackages = getSupportedPackages(context);
        if (supportedPackages.isEmpty()) {
            Toast.makeText(context, R.string.no_iconpacks_summary, Toast.LENGTH_SHORT).show();
            return;
        }

        // Sort alphabetically
        Collections.sort(supportedPackages, new Comparator<IconPackInfo>() {
            @Override
            public int compare(IconPackInfo a, IconPackInfo b) {
                return a.label.toString().compareToIgnoreCase(b.label.toString());
            }
        });

        // Add default icons
        Resources res = context.getResources();
        String defaultLabel = res.getString(R.string.default_iconpack_title);
        Drawable icon = res.getDrawable(R.mipmap.ic_launcher_home);
        supportedPackages.add(0, new IconPackInfo(defaultLabel, icon, ""));

        final String currentIconPack = SettingsProvider.getStringCustomDefault(context,
                SettingsProvider.SETTINGS_UI_GENERAL_ICONS_ICON_PACK, "");
        int currentPosition = 0;

        for (int i = 0; i < supportedPackages.size(); i++) {
            IconPackInfo info = supportedPackages.get(i);
            if (info.packageName.equals(currentIconPack)) {
                currentPosition = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.dialog_pick_iconpack_title);

        builder.setSingleChoiceItems(new IconAdapter(context, supportedPackages), currentPosition, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                IconPackInfo info = supportedPackages.get(which);
                if (info.packageName.equals(currentIconPack)) {
                    ((Launcher) context).getWorkspace().exitOverviewMode(true);
                }
                SettingsProvider.putString(context,
                        SettingsProvider.SETTINGS_UI_GENERAL_ICONS_ICON_PACK, info.packageName);
                LauncherAppState.getInstance().getIconCache().flush();
                LauncherAppState.getInstance().getModel().forceReload();
                ((Launcher) context).getWorkspace().exitOverviewMode(true);
            }
        });
        builder.show().getWindow().getDecorView().setAlpha(0.8f);
    }

    boolean isIconPackLoaded() {
        return mLoadedIconPackResource != null &&
                mLoadedIconPackName != null &&
                mIconPackResources != null;
    }

    private int getResourceIdForDrawable(String resource) {
        int resId = mLoadedIconPackResource.getIdentifier(resource, "drawable", mLoadedIconPackName);
        return resId;
    }

    public Resources getIconPackResources() {
        return mLoadedIconPackResource;
    }

    public int getResourceIdForActivityIcon(ActivityInfo info) {
        String drawable = mIconPackResources.get(info.packageName.toLowerCase()
                + "." + info.name.toLowerCase());
        if (drawable == null) {
            // Icon pack doesn't have an icon for the activity, fallback to package icon
            drawable = mIconPackResources.get(info.packageName.toLowerCase());
            if (drawable == null) {
                return 0;
            }
        }
        return getResourceIdForDrawable(drawable);
    }

    static class IconPackInfo {
        String packageName;
        CharSequence label;
        Drawable icon;

        IconPackInfo(ResolveInfo r, PackageManager packageManager) {
            packageName = r.activityInfo.packageName;
            icon = r.loadIcon(packageManager);
            label = r.loadLabel(packageManager);
        }

        IconPackInfo(){
        }

        public IconPackInfo(String label, Drawable icon, String packageName) {
            this.label = label;
            this.icon = icon;
            this.packageName = packageName;
        }
    }

    private static class IconAdapter extends ArrayAdapter<IconPackInfo> {
        private LayoutInflater mInflater;

        public IconAdapter(Context context, List<IconPackInfo> supportedPackages) {
            super(context, -1, supportedPackages);

            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            IconPackInfo info = getItem(position);
            CheckedTextView textView;

            if (convertView == null) {
                textView = (CheckedTextView) mInflater.inflate(R.layout.list_item_checkable, parent, false);
            } else {
                textView = (CheckedTextView) convertView;
            }

            if (textView != null) {
                textView.setText(info.label);
                textView.setTag(info);

                // Scale icon
                int drawablePadding = textView.getCompoundDrawablePadding();
                Drawable icon = info.icon;
                icon.setBounds(0, 0, icon.getIntrinsicWidth() - 2 * drawablePadding, icon.getIntrinsicHeight() - 2 * drawablePadding);

                textView.setCompoundDrawablesRelative(icon, null, null, null);
            }

            return textView;
        }
    }

}