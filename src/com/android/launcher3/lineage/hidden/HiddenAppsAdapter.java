/*
 * Copyright (C) 2019 The LineageOS Project
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
package com.android.launcher3.lineage.hidden;

import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.R;
import com.android.launcher3.lineage.hidden.db.HiddenComponent;

import java.util.ArrayList;
import java.util.List;

class HiddenAppsAdapter extends RecyclerView.Adapter<HiddenAppsAdapter.ViewHolder> {
    private List<HiddenComponent> mList = new ArrayList<>();
    private Listener mListener;

    HiddenAppsAdapter(Listener listener) {
        mListener = listener;
    }

    public void update(List<HiddenComponent> list) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new Callback(mList, list));
        mList = list;
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hidden_app, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        viewHolder.bind(mList.get(i));
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    public interface Listener {
        void onItemChanged(@NonNull HiddenComponent component);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView mIconView;
        private TextView mLabelView;
        private ImageView mLockView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            mIconView = itemView.findViewById(R.id.item_hidden_app_icon);
            mLabelView = itemView.findViewById(R.id.item_hidden_app_title);
            mLockView = itemView.findViewById(R.id.item_hidden_app_switch);
        }

        void bind(HiddenComponent component) {
            mIconView.setImageDrawable(component.getIcon());
            mLabelView.setText(component.getLabel());
            mLockView.setImageResource(component.isHidden() ?
                    R.drawable.ic_hidden_locked : R.drawable.ic_hidden_unlocked);

            itemView.setOnClickListener(v -> {
                component.invertVisibility();

                mLockView.setImageResource(component.isHidden() ?
                        R.drawable.avd_hidden_lock : R.drawable.avd_hidden_unlock);
                AnimatedVectorDrawable avd  = (AnimatedVectorDrawable) mLockView.getDrawable();

                int position = getAdapterPosition();
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                    avd.registerAnimationCallback(new Animatable2.AnimationCallback() {
                        @Override
                        public void onAnimationEnd(Drawable drawable) {
                            updateList(position, component);
                        }
                    });
                    avd.start();
                } else {
                    avd.start();
                    updateList(position, component);
                }
            });
        }

        private void updateList(int position, HiddenComponent component) {
            mListener.onItemChanged(component);
            mList.set(position, component);
            notifyItemChanged(position);
        }
    }

    private static class Callback extends DiffUtil.Callback {
        List<HiddenComponent> mOldList;
        List<HiddenComponent> mNewList;

        public Callback(List<HiddenComponent> oldList,
                        List<HiddenComponent> newList) {
            mOldList = oldList;
            mNewList = newList;
        }


        @Override
        public int getOldListSize() {
            return mOldList.size();
        }

        @Override
        public int getNewListSize() {
            return mNewList.size();
        }

        @Override
        public boolean areItemsTheSame(int iOld, int iNew) {
            String oldPkg = mOldList.get(iOld).getPackageName();
            String newPkg = mNewList.get(iNew).getPackageName();
            return oldPkg.equals(newPkg);
        }

        @Override
        public boolean areContentsTheSame(int iOld, int iNew) {
            return mOldList.get(iOld).equals(mNewList.get(iNew));
        }
    }
}
