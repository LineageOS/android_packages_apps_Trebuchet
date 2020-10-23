/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItem;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.systemui.plugins.AllAppsSearchPlugin;
import com.android.systemui.plugins.shared.SearchTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * An interface to a search box that AllApps can command.
 */
public class AllAppsSearchBarController
        implements TextWatcher, OnEditorActionListener, ExtendedEditText.OnBackKeyListener,
        OnFocusChangeListener {

    protected BaseDraggingActivity mLauncher;
    protected Callbacks mCb;
    protected ExtendedEditText mInput;
    protected String mQuery;

    protected SearchAlgorithm mSearchAlgorithm;

    public void setVisibility(int visibility) {
        mInput.setVisibility(visibility);
    }

    /**
     * Sets the references to the apps model and the search result callback.
     */
    public final void initialize(
            SearchAlgorithm searchAlgorithm, ExtendedEditText input,
            BaseDraggingActivity launcher, Callbacks cb, Consumer<List<Bundle>> secondaryCb) {
        mCb = cb;
        mLauncher = launcher;

        mInput = input;
        mInput.addTextChangedListener(this);
        mInput.setOnEditorActionListener(this);
        mInput.setOnBackKeyListener(this);
        mInput.setOnFocusChangeListener(this);
        mSearchAlgorithm = searchAlgorithm;
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        if (mSearchAlgorithm instanceof PluginWrapper) {
            ((PluginWrapper) mSearchAlgorithm).runOnPluginIfConnected(
                    AllAppsSearchPlugin::startedSearchSession);
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Do nothing
    }

    @Override
    public void afterTextChanged(final Editable s) {
        mQuery = s.toString();
        if (mQuery.isEmpty()) {
            mSearchAlgorithm.cancel(true);
            mCb.clearSearchResult();
        } else {
            mSearchAlgorithm.cancel(false);
            mSearchAlgorithm.doSearch(mQuery, mCb);
        }
    }

    public void refreshSearchResult() {
        if (TextUtils.isEmpty(mQuery)) {
            return;
        }
        // If play store continues auto updating an app, we want to show partial result.
        mSearchAlgorithm.cancel(false);
        mSearchAlgorithm.doSearch(mQuery, mCb);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (FeatureFlags.ENABLE_DEVICE_SEARCH.get()) {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                // selectFocusedView should return SearchTargetEvent that is passed onto onClick
                if (Launcher.getLauncher(mLauncher).getAppsView().selectFocusedView(v)) {
                    return true;
                }
            }
        }

        // Skip if it's not the right action
        if (actionId != EditorInfo.IME_ACTION_SEARCH) {
            return false;
        }

        // Skip if the query is empty
        String query = v.getText().toString();
        if (query.isEmpty()) {
            return false;
        }
        return mLauncher.startActivitySafely(v,
                PackageManagerHelper.getMarketSearchIntent(mLauncher, query), null
        );
    }

    @Override
    public boolean onBackKey() {
        // Only hide the search field if there is no query
        String query = Utilities.trim(mInput.getEditableText().toString());
        if (query.isEmpty()) {
            reset();
            return true;
        }
        return false;
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        if (!hasFocus) {
            mInput.hideKeyboard();
        }
    }

    /**
     * Resets the search bar state.
     */
    public void reset() {
        mCb.clearSearchResult();
        mInput.reset();
        mQuery = null;
    }

    /**
     * Focuses the search field to handle key events.
     */
    public void focusSearchField() {
        mInput.showKeyboard();
    }

    /**
     * Returns whether the search field is focused.
     */
    public boolean isSearchFieldFocused() {
        return mInput.isFocused();
    }

    /**
     * A wrapper setup for running essential calls to plugin from search controller
     */
    public interface PluginWrapper {
        /**
         * executes call if plugin is connected
         */
        void runOnPluginIfConnected(Consumer<AllAppsSearchPlugin> plugin);
    }

    /**
     * Callback for getting search results.
     */
    public interface Callbacks {

        /**
         * Called when the search from primary source is complete.
         *
         * @param items sorted list of search result adapter items
         */
        void onSearchResult(String query, ArrayList<AdapterItem> items);

        /**
         * Called when the search from secondary source is complete.
         *
         * @param items sorted list of search result adapter items
         */
        void onAppendSearchResult(String query, ArrayList<AdapterItem> items);

        /**
         * Called when the search results should be cleared.
         */
        void clearSearchResult();
    }

    /**
     * An interface for supporting dynamic search results
     */
    public interface SearchTargetHandler {

        /**
         * Update view using values from {@link SearchTarget}
         */
        void applySearchTarget(SearchTarget searchTarget);

        /**
         * Handles selection of SearchTarget
         */
        default void handleSelection(int eventType) {
        }

    }


}