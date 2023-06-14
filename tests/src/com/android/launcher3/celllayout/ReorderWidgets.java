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
package com.android.launcher3.celllayout;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Point;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.celllayout.testcases.FullReorderCase;
import com.android.launcher3.celllayout.testcases.MoveOutReorderCase;
import com.android.launcher3.celllayout.testcases.MultipleCellLayoutsSimpleReorder;
import com.android.launcher3.celllayout.testcases.PushReorderCase;
import com.android.launcher3.celllayout.testcases.ReorderTestCase;
import com.android.launcher3.celllayout.testcases.SimpleReorderCase;
import com.android.launcher3.tapl.Widget;
import com.android.launcher3.tapl.WidgetResizeFrame;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.TaplTestsLauncher3;
import com.android.launcher3.util.rule.ShellCommandRule;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ReorderWidgets extends AbstractLauncherUiTest {

    @Rule
    public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grantWidgetBind();

    private static final String TAG = ReorderWidgets.class.getSimpleName();

    TestWorkspaceBuilder mWorkspaceBuilder;

    @Before
    public void setup() throws Throwable {
        mWorkspaceBuilder = new TestWorkspaceBuilder(this, mTargetContext);
        TaplTestsLauncher3.initialize(this);
        clearHomescreen();
    }

    /**
     * Validate if the given board represent the current CellLayout
     **/
    private boolean validateBoard(List<CellLayoutBoard> testBoards) {
        ArrayList<CellLayoutBoard> workspaceBoards = workspaceToBoards();
        if (workspaceBoards.size() < testBoards.size()) {
            return false;
        }
        for (int i = 0; i < testBoards.size(); i++) {
            if (testBoards.get(i).compareTo(workspaceBoards.get(i)) != 0) {
                return false;
            }
        }
        return true;
    }

    private FavoriteItemsTransaction buildWorkspaceFromBoards(List<CellLayoutBoard> boards,
            FavoriteItemsTransaction transaction) {
        for (int i = 0; i < boards.size(); i++) {
            CellLayoutBoard board = boards.get(i);
            mWorkspaceBuilder.buildFromBoard(board, transaction, i);
        }
        return transaction;
    }

    private void printCurrentWorkspace() {
        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(mTargetContext);
        ArrayList<CellLayoutBoard> boards = workspaceToBoards();
        for (int i = 0; i < boards.size(); i++) {
            Log.d(TAG, "Screen number " + i);
            Log.d(TAG, ".\n" + boards.get(i).toString(idp.numColumns, idp.numRows));
        }
    }

    private ArrayList<CellLayoutBoard> workspaceToBoards() {
        return getFromLauncher(CellLayoutTestUtils::workspaceToBoards);
    }

    private void runTestCase(ReorderTestCase testCase)
            throws ExecutionException, InterruptedException {
        CellLayoutBoard.WidgetRect mainWidgetCellPos = CellLayoutBoard.getMainFromList(
                testCase.mStart);

        FavoriteItemsTransaction transaction =
                new FavoriteItemsTransaction(mTargetContext, this);
        transaction = buildWorkspaceFromBoards(testCase.mStart, transaction);
        transaction.commit();
        // resetLoaderState triggers the launcher to start loading the workspace which allows
        // waitForLauncherCondition to wait for that condition, otherwise the condition would
        // always be true and it wouldn't wait for the changes to be applied.
        resetLoaderState();
        Log.d(TestProtocol.FLAKY_BINDING, "waiting for: isWorkspaceLoading=false");
        waitForLauncherCondition("Workspace didn't finish loading", l -> {
            boolean isWorkspaceLoading = l.isWorkspaceLoading();

            Log.d(TestProtocol.FLAKY_BINDING, "checking: isWorkspaceLoading=" + isWorkspaceLoading);

            return !isWorkspaceLoading;
        });
        Widget widget = mLauncher.getWorkspace().getWidgetAtCell(mainWidgetCellPos.getCellX(),
                mainWidgetCellPos.getCellY());
        assertNotNull(widget);
        WidgetResizeFrame resizeFrame = widget.dragWidgetToWorkspace(testCase.moveMainTo.x,
                testCase.moveMainTo.y, mainWidgetCellPos.getSpanX(), mainWidgetCellPos.getSpanY());
        resizeFrame.dismiss();

        boolean isValid = false;
        for (List<CellLayoutBoard> boards : testCase.mEnd) {
            isValid |= validateBoard(boards);
            if (isValid) break;
        }
        printCurrentWorkspace();
        assertTrue("Non of the valid boards match with the current state", isValid);
    }

    /**
     * Run only the test define for the current grid size if such test exist
     *
     * @param testCaseMap map containing all the tests per grid size (Point)
     */
    private void runTestCaseMap(Map<Point, ReorderTestCase> testCaseMap, String testName)
            throws ExecutionException, InterruptedException {
        Point iconGridDimensions = mLauncher.getWorkspace().getIconGridDimensions();
        Log.d(TAG, "Running test " + testName + " for grid " + iconGridDimensions);
        Assume.assumeTrue(
                "The test " + testName + " doesn't support " + iconGridDimensions + " grid layout",
                testCaseMap.containsKey(iconGridDimensions));
        runTestCase(testCaseMap.get(iconGridDimensions));
    }

    @Test
    public void simpleReorder() throws ExecutionException, InterruptedException {
        runTestCaseMap(SimpleReorderCase.TEST_BY_GRID_SIZE,
                SimpleReorderCase.class.getSimpleName());
    }

    @Ignore
    @Test
    public void pushTest() throws ExecutionException, InterruptedException {
        runTestCaseMap(PushReorderCase.TEST_BY_GRID_SIZE, PushReorderCase.class.getSimpleName());
    }

    @Ignore
    @Test
    public void fullReorder() throws ExecutionException, InterruptedException {
        runTestCaseMap(FullReorderCase.TEST_BY_GRID_SIZE, FullReorderCase.class.getSimpleName());
    }

    @Test
    public void moveOutReorder() throws ExecutionException, InterruptedException {
        runTestCaseMap(MoveOutReorderCase.TEST_BY_GRID_SIZE,
                MoveOutReorderCase.class.getSimpleName());
    }

    @Test
    public void multipleCellLayoutsSimpleReorder() throws ExecutionException, InterruptedException {
        Assume.assumeTrue("Test doesn't support foldables", !mLauncher.isTwoPanels());
        runTestCaseMap(MultipleCellLayoutsSimpleReorder.TEST_BY_GRID_SIZE,
                MultipleCellLayoutsSimpleReorder.class.getSimpleName());
    }
}
