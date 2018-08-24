package com.android.launcher3.protect;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import com.android.launcher3.ItemInfo;

public class ProtectedIntent extends Intent {
    public View appview;
    public Intent appintent;
    public ItemInfo appitem;

    public ProtectedIntent(Intent o, View appview, Intent appintent, ItemInfo appitem) {
        super(o);
        this.appview = appview;
        this.appintent = appintent;
        this.appitem = appitem;
    }
}