/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.util

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import com.android.launcher3.R
import com.android.launcher3.tests.R as TestR
import kotlin.IntArray

class TestResourceHelper(private val context: Context, private val specsFileId: Int) :
    ResourceHelper(context, specsFileId) {
    override fun obtainStyledAttributes(attrs: AttributeSet, styleId: IntArray): TypedArray {
        var clone = styleId.clone()
        if (styleId == R.styleable.SpecSize) clone = TestR.styleable.SpecSize
        else if (styleId == R.styleable.WorkspaceSpec) clone = TestR.styleable.WorkspaceSpec
        return context.obtainStyledAttributes(attrs, clone)
    }
}
