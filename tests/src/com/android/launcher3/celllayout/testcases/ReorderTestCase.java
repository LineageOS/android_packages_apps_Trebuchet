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

import com.android.launcher3.celllayout.CellLayoutBoard;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ReorderTestCase {
    public CellLayoutBoard mStart;
    public Point moveMainTo;
    public List<CellLayoutBoard> mEnd;

    ReorderTestCase(CellLayoutBoard start, Point moveMainTo, CellLayoutBoard ... end) {
        mStart = start;
        this.moveMainTo = moveMainTo;
        mEnd = Arrays.asList(end);
    }

    ReorderTestCase(String start, Point moveMainTo, String ... end) {
        mStart = CellLayoutBoard.boardFromString(start);
        this.moveMainTo = moveMainTo;
        mEnd = Arrays
                .asList(end)
                .stream()
                .map(CellLayoutBoard::boardFromString)
                .collect(Collectors.toList());
    }
}
