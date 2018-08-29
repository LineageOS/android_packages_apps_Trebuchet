package com.android.launcher3.util;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Xml;

import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AdaptiveIconDrawableCompat;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class DrawableHack {
    private static ClassLoader classLoader = DrawableHack.class.getClassLoader();

    private static Method methodInflateFromXml() throws ClassNotFoundException, NoSuchMethodException{
        return classLoader.loadClass("android.graphics.drawable.DrawableInflater").getDeclaredMethod("inflateFromXml",
                String.class, XmlPullParser.class, AttributeSet.class, Resources.Theme.class);
    }
    private static Method methodGetDrawableInflater() throws  NoSuchMethodException{
        return Resources.class.getDeclaredMethod("getDrawableInflater");
    }

    private static Field fieldClassLoader() throws  ClassNotFoundException, NoSuchFieldException {
        return classLoader.loadClass("android.graphics.drawable.DrawableInflater").getDeclaredField("mClassLoader");
    }

    public static Drawable inflateFromXml(Object drawableInflater, XmlPullParser parser)
            throws XmlPullParserException, IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return inflateFromXml(drawableInflater, parser, null);
    }

    public static Drawable inflateFromXml(Object drawableInflater, XmlPullParser parser, Resources.Theme theme)
            throws XmlPullParserException, IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return inflateFromXml(drawableInflater, parser, Xml.asAttributeSet(parser), theme);
    }

    public static Drawable inflateFromXml(Object drawableInflater, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme)
            throws XmlPullParserException, IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        while(parser.next()!=XmlPullParser.START_TAG && parser.getEventType()!=XmlPullParser.END_DOCUMENT);

        if(parser.getEventType() != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (Utilities.ATLEAST_NOUGAT) return (Drawable) methodInflateFromXml().invoke(drawableInflater, parser.getName(), parser, attrs, theme);
        else if (drawableInflater instanceof DrawableInflaterVL) {
            AdaptiveIconDrawableCompat drawable = (AdaptiveIconDrawableCompat) ((DrawableInflaterVL)drawableInflater).inflateFromXml(parser.getName(), parser, attrs, theme);
            if (!Utilities.ATLEAST_MARSHMALLOW) drawable.mUseMyUglyWorkaround=false;
            return drawable;
        }
        else throw new UnsupportedOperationException("");
    }

    private static ClassLoader wrappedClassLoader = new ClassLoader() {
        @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
            return classLoader.loadClass((name.equals("adaptive-icon"))?AdaptiveIconDrawableCompat.class.getName():name);
        }
    };

    public static Object getDrawableInflater(Resources res) throws Exception{
        if (Utilities.ATLEAST_NOUGAT) {
            Object inflater = methodGetDrawableInflater().invoke(res);
            Field mClassLoader = fieldClassLoader();
            mClassLoader.setAccessible(true);
            mClassLoader.set(inflater, wrappedClassLoader);
            return inflater;
        }
        else {
            DrawableInflaterVL inflater = new DrawableInflaterVL(res, Resources.class.getClassLoader());
            inflater.mClassLoader = wrappedClassLoader;
            return inflater;
        }
    }
}