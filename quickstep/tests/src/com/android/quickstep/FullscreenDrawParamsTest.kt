/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.quickstep

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.FakeInvariantDeviceProfileTest
import com.android.quickstep.util.TaskCornerRadius
import com.android.quickstep.views.TaskView.FullscreenDrawParams
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper
import com.android.systemui.shared.system.QuickStepContract
import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

/** Test for FullscreenDrawParams class. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class FullscreenDrawParamsTest : FakeInvariantDeviceProfileTest() {

    private val TASK_SCALE = 0.7f
    private var mThumbnailData: ThumbnailData = mock(ThumbnailData::class.java)

    private val mPreviewPositionHelper = PreviewPositionHelper()
    private lateinit var params: FullscreenDrawParams

    @Before
    fun setup() {
        params = FullscreenDrawParams(context)
    }

    @Test
    fun setStartProgress_correctCornerRadiusForTablet() {
        initializeVarsForTablet()
        val dp = newDP()
        val previewRect = Rect(0, 0, 100, 100)
        val canvasWidth = (dp.widthPx * TASK_SCALE).roundToInt()
        val canvasHeight = (dp.heightPx * TASK_SCALE).roundToInt()
        val currentRotation = 0
        val isRtl = false

        mPreviewPositionHelper.updateThumbnailMatrix(
            previewRect,
            mThumbnailData,
            canvasWidth,
            canvasHeight,
            dp.widthPx,
            dp.heightPx,
            dp.taskbarHeight,
            dp.isTablet,
            currentRotation,
            isRtl
        )
        params.setProgress(
            /* fullscreenProgress= */ 0f,
            /* parentScale= */ 1.0f,
            /* taskViewScale= */ 1.0f,
            /* previewWidth= */ 0,
            dp,
            mPreviewPositionHelper
        )

        val expectedRadius = TaskCornerRadius.get(context)
        assertThat(params.mCurrentDrawnCornerRadius).isEqualTo(expectedRadius)
    }

    @Test
    fun setFullProgress_correctCornerRadiusForTablet() {
        initializeVarsForTablet()
        val dp = newDP()
        val previewRect = Rect(0, 0, 100, 100)
        val canvasWidth = (dp.widthPx * TASK_SCALE).roundToInt()
        val canvasHeight = (dp.heightPx * TASK_SCALE).roundToInt()
        val currentRotation = 0
        val isRtl = false

        mPreviewPositionHelper.updateThumbnailMatrix(
            previewRect,
            mThumbnailData,
            canvasWidth,
            canvasHeight,
            dp.widthPx,
            dp.heightPx,
            dp.taskbarHeight,
            dp.isTablet,
            currentRotation,
            isRtl
        )
        params.setProgress(
            /* fullscreenProgress= */ 1.0f,
            /* parentScale= */ 1.0f,
            /* taskViewScale= */ 1.0f,
            /* previewWidth= */ 0,
            dp,
            mPreviewPositionHelper
        )

        val expectedRadius = QuickStepContract.getWindowCornerRadius(context)
        assertThat(params.mCurrentDrawnCornerRadius).isEqualTo(expectedRadius)
    }

    @Test
    fun setStartProgress_correctCornerRadiusForPhone() {
        initializeVarsForPhone()
        val dp = newDP()
        val previewRect = Rect(0, 0, 100, 100)
        val canvasWidth = (dp.widthPx * TASK_SCALE).roundToInt()
        val canvasHeight = (dp.heightPx * TASK_SCALE).roundToInt()
        val currentRotation = 0
        val isRtl = false

        mPreviewPositionHelper.updateThumbnailMatrix(
            previewRect,
            mThumbnailData,
            canvasWidth,
            canvasHeight,
            dp.widthPx,
            dp.heightPx,
            dp.taskbarHeight,
            dp.isTablet,
            currentRotation,
            isRtl
        )
        params.setProgress(
            /* fullscreenProgress= */ 0f,
            /* parentScale= */ 1.0f,
            /* taskViewScale= */ 1.0f,
            /* previewWidth= */ 0,
            dp,
            mPreviewPositionHelper
        )

        val expectedRadius = TaskCornerRadius.get(context)
        assertThat(params.mCurrentDrawnCornerRadius).isEqualTo(expectedRadius)
    }

    @Test
    fun setFullProgress_correctCornerRadiusForPhone() {
        initializeVarsForPhone()
        val dp = newDP()
        val previewRect = Rect(0, 0, 100, 100)
        val canvasWidth = (dp.widthPx * TASK_SCALE).roundToInt()
        val canvasHeight = (dp.heightPx * TASK_SCALE).roundToInt()
        val currentRotation = 0
        val isRtl = false

        mPreviewPositionHelper.updateThumbnailMatrix(
            previewRect,
            mThumbnailData,
            canvasWidth,
            canvasHeight,
            dp.widthPx,
            dp.heightPx,
            dp.taskbarHeight,
            dp.isTablet,
            currentRotation,
            isRtl
        )
        params.setProgress(
            /* fullscreenProgress= */ 1.0f,
            /* parentScale= */ 1.0f,
            /* taskViewScale= */ 1.0f,
            /* previewWidth= */ 0,
            dp,
            mPreviewPositionHelper
        )

        val expectedRadius = QuickStepContract.getWindowCornerRadius(context)
        assertThat(params.mCurrentDrawnCornerRadius).isEqualTo(expectedRadius)
    }
}
