package com.android.launcher3.util;

import android.content.res.AssetManager;
import android.content.res.Resources;
import com.android.launcher3.Utilities;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ResourceHack {
    private static Method setConfigurationMethod() throws NoSuchMethodException {
        return (Utilities.ATLEAST_OREO) ?
                AssetManager.class.getDeclaredMethod("setConfiguration",
                        /* mcc */ int.class, /* mnc */ int.class, /* locale */ String.class,
                        /* orientation*/ int.class, /* touchscreen*/ int.class, /* density*/ int.class,
                        /* keyboard */ int.class, /* keyboardHidden */ int.class, /* navigation */ int.class,
                        /* screenWidth */ int.class, /* screenHeight */ int.class, /* smallestScreenWidthDp */ int.class,
                        /* screenWidthDp */ int.class, /* screenHeightDp */ int.class, /* screenLayout */ int.class,
                        /* uiMode */ int.class, /* colorMode */ int.class, /* majorVersion */ int.class)
            :
                AssetManager.class.getDeclaredMethod("setConfiguration",
                        /* mcc */ int.class, /* mnc */ int.class, /* locale */ String.class,
                        /* orientation*/ int.class, /* touchscreen*/ int.class, /* density*/ int.class,
                        /* keyboard */ int.class, /* keyboardHidden */ int.class, /* navigation */ int.class,
                        /* screenWidth */ int.class, /* screenHeight */ int.class, /* smallestScreenWidthDp */ int.class,
                        /* screenWidthDp */ int.class, /* screenHeightDp */ int.class, /* screenLayout */ int.class,
                        /* uiMode */ int.class, /* majorVersion */ int.class);
    }

    public static Resources setResSdk(Resources res, int sdk) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        res.getDisplayMetrics().scaledDensity = res.getDisplayMetrics().density * (res.getConfiguration().fontScale != 0.0F?res.getConfiguration().fontScale:1.0F);
        int width;
        int height;
        if(res.getDisplayMetrics().widthPixels >= res.getDisplayMetrics().heightPixels) {
            width = res.getDisplayMetrics().widthPixels;
            height = res.getDisplayMetrics().heightPixels;
        } else {
            width = res.getDisplayMetrics().heightPixels;
            height = res.getDisplayMetrics().widthPixels;
        }
        if(Utilities.ATLEAST_OREO) {
            setConfigurationMethod().invoke(res.getAssets(), Integer.valueOf(res.getConfiguration().mcc), Integer.valueOf(res.getConfiguration().mnc),
                    res.getConfiguration().locale.toLanguageTag(), Integer.valueOf(res.getConfiguration().orientation), Integer.valueOf(res.getConfiguration().touchscreen),
                    Integer.valueOf(res.getConfiguration().densityDpi), Integer.valueOf(res.getConfiguration().keyboard), Integer.valueOf(res.getConfiguration().keyboardHidden),
                    Integer.valueOf(res.getConfiguration().navigation), Integer.valueOf(width), Integer.valueOf(height), Integer.valueOf(res.getConfiguration().smallestScreenWidthDp),
                    Integer.valueOf(res.getConfiguration().screenWidthDp), Integer.valueOf(res.getConfiguration().screenHeightDp), Integer.valueOf(res.getConfiguration().screenLayout),
                    Integer.valueOf(res.getConfiguration().uiMode), Integer.valueOf(res.getConfiguration().colorMode), Integer.valueOf(sdk));
        } else {
            setConfigurationMethod().invoke(res.getAssets(), Integer.valueOf(res.getConfiguration().mcc), Integer.valueOf(res.getConfiguration().mnc),
                    res.getConfiguration().locale.toLanguageTag(), Integer.valueOf(res.getConfiguration().orientation), Integer.valueOf(res.getConfiguration().touchscreen),
                    Integer.valueOf(res.getConfiguration().densityDpi), Integer.valueOf(res.getConfiguration().keyboard), Integer.valueOf(res.getConfiguration().keyboardHidden),
                    Integer.valueOf(res.getConfiguration().navigation), Integer.valueOf(width), Integer.valueOf(height), Integer.valueOf(res.getConfiguration().smallestScreenWidthDp),
                    Integer.valueOf(res.getConfiguration().screenWidthDp), Integer.valueOf(res.getConfiguration().screenHeightDp), Integer.valueOf(res.getConfiguration().screenLayout),
                    Integer.valueOf(res.getConfiguration().uiMode), Integer.valueOf(sdk));
        }
        return res;
    }
}