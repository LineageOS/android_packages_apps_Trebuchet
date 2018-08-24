package com.android.launcher3.protect;

import android.graphics.drawable.Drawable;

class ProtectedComponent {
    String packageName;
    Drawable icon;
    String label;
    boolean isProtected;

    ProtectedComponent(String packageName, Drawable icon, String label,
                              boolean isProtected) {
        this.packageName = packageName;
        this.icon = icon;
        this.label = label;
        this.isProtected = isProtected;
    }
}
