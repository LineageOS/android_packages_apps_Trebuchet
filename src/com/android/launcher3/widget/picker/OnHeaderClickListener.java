/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.widget.picker;

import com.android.launcher3.util.PackageUserKey;

/**
 * A listener to be invoked when a header is clicked.
 */
public interface OnHeaderClickListener {
    /**
     * Calls when a header is clicked to show / hide widgets for a package.
     */
    void onHeaderClicked(boolean showWidgets, PackageUserKey key);
}
