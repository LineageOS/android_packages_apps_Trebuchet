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
import com.android.launcher3.DeviceProfileBaseTest
import com.android.launcher3.util.WindowBounds
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class HotseatWidthCalculationTest : DeviceProfileBaseTest() {

    /**
     * This is a case when after setting the hotseat, the space needs to be recalculated
     * but it doesn't need to change QSB width or remove icons
     */
    @Test
    fun distribute_border_space_when_space_is_enough_portrait() {
        initializeVarsForTablet(isGestureMode = false)
        windowBounds = WindowBounds(Rect(0, 0, 1800, 2560), Rect(0, 104, 0, 0))
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dp.hotseatBarEndOffset).isEqualTo(510)
        assertThat(dp.numShownHotseatIcons).isEqualTo(6)
        assertThat(dp.hotseatBorderSpace).isEqualTo(70)

        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(150)
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(580)

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.hotseatQsbWidth).isEqualTo(1445)
    }

    /**
     * This is a case when after setting the hotseat, and recalculating spaces
     * it still needs to remove icons for everything to fit
     */
    @Test
    fun decrease_num_of_icons_when_not_enough_space_portrait() {
        initializeVarsForTablet(isGestureMode = false)
        windowBounds = WindowBounds(Rect(0, 0, 1300, 2560), Rect(0, 104, 0, 0))
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dp.hotseatBarEndOffset).isEqualTo(510)
        assertThat(dp.numShownHotseatIcons).isEqualTo(4)
        assertThat(dp.hotseatBorderSpace).isEqualTo(40)

        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(150)
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(550)

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.hotseatQsbWidth).isEqualTo(1080)
    }

    /**
     * This is a case when after setting the hotseat, the space needs to be recalculated
     * but it doesn't need to change QSB width or remove icons
     */
    @Test
    fun distribute_border_space_when_space_is_enough_landscape() {
        initializeVarsForTwoPanel(isGestureMode = false, isLandscape = true)
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dp.hotseatBarEndOffset).isEqualTo(705)
        assertThat(dp.numShownHotseatIcons).isEqualTo(6)
        assertThat(dp.hotseatBorderSpace).isEqualTo(54)

        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(231)
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(759)

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.hotseatQsbWidth).isEqualTo(1468)
    }

    /**
     * This is a case when the hotseat spans a certain amount of columns
     * and the nav buttons push the hotseat to the side, but not enough to change the border space.
     */
    @Test
    fun nav_buttons_dont_interfere_with_required_hotseat_width() {
        initializeVarsForTablet(isGestureMode = false, isLandscape = true)
        inv?.apply {
            hotseatColumnSpan = IntArray(4) { 4 }
            inlineQsb = BooleanArray(4) { false }
        }
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dp.hotseatBarEndOffset).isEqualTo(660)
        assertThat(dp.numShownHotseatIcons).isEqualTo(6)
        assertThat(dp.hotseatBorderSpace).isEqualTo(100)

        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(300)
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(1040)

        assertThat(dp.isQsbInline).isFalse()
        assertThat(dp.hotseatQsbWidth).isEqualTo(1233)
    }

    /**
     * This is a case when after setting the hotseat, the QSB width needs to be changed to fit
     */
    @Test
    fun decrease_qsb_when_not_enough_space_landscape() {
        initializeVarsForTablet(isGestureMode = false, isLandscape = true)
        windowBounds = WindowBounds(Rect(0, 0, 2460, 1600), Rect(0, 104, 0, 0))
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dp.hotseatBarEndOffset).isEqualTo(660)
        assertThat(dp.numShownHotseatIcons).isEqualTo(6)
        assertThat(dp.hotseatBorderSpace).isEqualTo(36)

        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(864)
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(696)

        assertThat(dp.isQsbInline).isTrue()
        assertThat(dp.hotseatQsbWidth).isEqualTo(528)
    }

    /**
     * This is a case when after setting the hotseat, changing QSB width, and recalculating spaces
     * it still needs to remove icons for everything to fit
     */
    @Test
    fun decrease_num_of_icons_when_not_enough_space_landscape() {
        initializeVarsForTablet(isGestureMode = false, isLandscape = true)
        windowBounds = WindowBounds(Rect(0, 0, 2260, 1600), Rect(0, 104, 0, 0))
        val dp = newDP()
        dp.isTaskbarPresentInApps = true

        assertThat(dp.hotseatBarEndOffset).isEqualTo(660)
        assertThat(dp.numShownHotseatIcons).isEqualTo(5)
        assertThat(dp.hotseatBorderSpace).isEqualTo(36)

        assertThat(dp.getHotseatLayoutPadding(context).left).isEqualTo(816)
        assertThat(dp.getHotseatLayoutPadding(context).right).isEqualTo(700)

        assertThat(dp.isQsbInline).isTrue()
        assertThat(dp.hotseatQsbWidth).isEqualTo(480)
    }
}
