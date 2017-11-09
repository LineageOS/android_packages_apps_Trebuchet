package com.android.launcher3.lineage;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Pair;

import java.util.Locale;

public class LineageUtils {

    private static final String GRID_VALUE_SEPARATOR = "x";
    private static final int GRID_ROW_VALUE_DEFAULT = 4;
    private static final int GRID_COLUMN_VALUE_DEFAULT = 5;

    public static boolean hasPackageInstalled(Context context, String pkgName) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(pkgName, 0);
            return ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static Pair<Integer, Integer> extractCustomGrid(String value) {
        int columns = GRID_COLUMN_VALUE_DEFAULT;
        int rows = GRID_ROW_VALUE_DEFAULT;
        String[] values = value.split(GRID_VALUE_SEPARATOR);

        if (values.length == 2) {
            try {
                columns = Integer.parseInt(values[0]);
                rows = Integer.parseInt(values[1]);
            } catch (NumberFormatException e) {
                // Ignore and fallback to default
                columns = GRID_COLUMN_VALUE_DEFAULT;
                rows = GRID_ROW_VALUE_DEFAULT;
            }
        }

        return new Pair<>(columns, rows);

    }

    public static String getGridValue(int columns, int rows) {
        return String.format(Locale.ENGLISH, "%1$d%2$s%3$d", columns,
                GRID_VALUE_SEPARATOR, rows);
    }
}
