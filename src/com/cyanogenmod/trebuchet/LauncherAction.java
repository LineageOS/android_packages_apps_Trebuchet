/*
 * Copyright (C) 2013 The CyanogenMod Project
 * Copyright (C) 2013 The Android Open Source Project
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

package com.cyanogenmod.trebuchet;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LauncherAction {

    public enum Action {
        AllApps;
        int getString() {
            return getString(this);
        }
        int getDrawable() {
            return getDrawable(this);
        }
        static int getString(Action action) {
            switch (action) {
                case AllApps:
                    return R.string.all_apps_button_label;
                default:
                    return -1;
            }
        }
        static int getDrawable(Action action) {
            switch (action) {
                case AllApps:
                    return R.drawable.ic_allapps;
                default:
                    return -1;
            }
        }
    }

    public static List<Action> getAllActions() {
        return Arrays.asList(Action.values());
    }

    public static class AddAdapter extends BaseAdapter {

        public class ItemInfo {
            public Action action;
            public Drawable drawable;
            public String title;
            public ItemInfo(Action info, Resources res) {
                action = info;
                drawable = res.getDrawable(info.getDrawable());
                title = res.getString(info.getString());
            }
        }

        private final LayoutInflater mInflater;

        private final List<ItemInfo> mItems = new ArrayList<ItemInfo>();

        public AddAdapter(Launcher launcher) {
            super();

            mInflater = (LayoutInflater) launcher.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            // Create default actions
            Resources res = launcher.getResources();

            List<Action> items = LauncherAction.getAllActions();
            for (Action item : items) {
                mItems.add(new ItemInfo(item, res));
            }
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ItemInfo item = (ItemInfo) getItem(position);

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.add_list_item, parent, false);
            }

            TextView textView = (TextView) convertView;
            textView.setTag(item);
            textView.setText(item.title);
            textView.setCompoundDrawablesWithIntrinsicBounds(item.drawable, null, null, null);

            return convertView;
        }

        public int getCount() {
            return mItems.size();
        }

        public Object getItem(int position) {
            return mItems.get(position);
        }

        public long getItemId(int position) {
            return position;
        }
    }
}
