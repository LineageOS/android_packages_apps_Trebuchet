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
package com.android.launcher3.celllayout.testcases;

import android.graphics.Point;

import java.util.Map;

/**
 * The grids represent the workspace to be build by TestWorkspaceBuilder, to see what each character
 * in the board mean refer to {@code CellType}
 */
public class SimpleReorderCase {

    /** 5x5 Test
     **/
    private static final String START_BOARD_STR_5x5 = ""
            + "xxxxx\n"
            + "--mm-\n"
            + "--mm-\n"
            + "-----\n"
            + "-----";
    private static final Point MOVE_TO_5x5 = new Point(4, 4);
    private static final String END_BOARD_STR_5x5 = ""
            + "xxxxx\n"
            + "-----\n"
            + "-----\n"
            + "---mm\n"
            + "---mm";
    private static final ReorderTestCase TEST_CASE_5x5 = new ReorderTestCase(START_BOARD_STR_5x5,
            MOVE_TO_5x5,
            END_BOARD_STR_5x5);

    /** 4x4 Test
     **/
    private static final String START_BOARD_STR_4x4 = ""
            + "xxxx\n"
            + "--mm\n"
            + "--mm\n"
            + "----";
    private static final Point MOVE_TO_4x4 = new Point(3, 3);
    private static final String END_BOARD_STR_4x4 = ""
            + "xxxx\n"
            + "----\n"
            + "--mm\n"
            + "--mm";
    private static final ReorderTestCase TEST_CASE_4x4 = new ReorderTestCase(START_BOARD_STR_4x4,
            MOVE_TO_4x4,
            END_BOARD_STR_4x4);

    public static final Map<Point, ReorderTestCase> TEST_BY_GRID_SIZE =
            Map.of(new Point(5, 5), TEST_CASE_5x5,
                    new Point(4, 4), TEST_CASE_4x4);
}
