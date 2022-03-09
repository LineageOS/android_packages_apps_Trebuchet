package com.android.launcher3.model;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.util.LauncherModelHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Tests for {@link PackageInstallStateChangedTask}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PackageInstallStateChangedTaskTest {

    private LauncherModelHelper mModelHelper;

    @Before
    public void setup() throws Exception {
        mModelHelper = new LauncherModelHelper();
        mModelHelper.initializeData("package_install_state_change_task_data");
    }

    @After
    public void tearDown() {
        mModelHelper.destroy();
    }

    private PackageInstallStateChangedTask newTask(String pkg, int progress) {
        int state = PackageInstallInfo.STATUS_INSTALLING;
        PackageInstallInfo installInfo = new PackageInstallInfo(pkg, state, progress,
                android.os.Process.myUserHandle());
        return new PackageInstallStateChangedTask(installInfo);
    }

    @Test
    public void testSessionUpdate_ignore_installed() throws Exception {
        mModelHelper.executeTaskForTest(newTask("app1", 30));

        // No shortcuts were updated
        verifyProgressUpdate(0);
    }

    @Test
    public void testSessionUpdate_shortcuts_updated() throws Exception {
        mModelHelper.executeTaskForTest(newTask("app3", 30));

        verifyProgressUpdate(30, 5, 6, 7);
    }

    @Test
    public void testSessionUpdate_widgets_updated() throws Exception {
        mModelHelper.executeTaskForTest(newTask("app4", 30));

        verifyProgressUpdate(30, 8, 9);
    }

    private void verifyProgressUpdate(int progress, Integer... idsUpdated) {
        HashSet<Integer> updates = new HashSet<>(Arrays.asList(idsUpdated));
        for (ItemInfo info : mModelHelper.getBgDataModel().itemsIdMap) {
            if (info instanceof WorkspaceItemInfo) {
                assertEquals(updates.contains(info.id) ? progress: 100,
                        ((WorkspaceItemInfo) info).getProgressLevel());
            } else {
                assertEquals(updates.contains(info.id) ? progress: -1,
                        ((LauncherAppWidgetInfo) info).installProgress);
            }
        }
    }
}
