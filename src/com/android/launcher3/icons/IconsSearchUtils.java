package com.android.launcher3.icons;

import java.util.ArrayList;
import java.util.List;

class IconsSearchUtils {
    static void filter(String query, List<String> matchingIcons, List<String> allIcons,
                       IconPickerActivity.GridAdapter mGridAdapter) {

        //new array list that will hold the filtered data
        List<String> resultsFromAllIcons = new ArrayList<>();
        List<String> resultsFromMatchingIcons = new ArrayList<>();
        boolean noMatch = matchingIcons.isEmpty();

        if (query.isEmpty()) {
            resultsFromAllIcons.clear();
            resultsFromAllIcons.add(null);
            resultsFromAllIcons.addAll(allIcons);
            resultsFromMatchingIcons.clear();
            if (!noMatch) {
                resultsFromMatchingIcons.add(null);
                resultsFromMatchingIcons.addAll(matchingIcons);
            }
            mGridAdapter.filterList(resultsFromAllIcons, resultsFromMatchingIcons);
        } else {
            resultsFromAllIcons.clear();
            resultsFromMatchingIcons.clear();
            if (noMatch) {
                getFilteredResults(allIcons, resultsFromAllIcons, query);
            } else {
                resultsFromAllIcons.clear();
                resultsFromMatchingIcons.clear();
                getFilteredResults(allIcons, resultsFromAllIcons, query);
                getFilteredResults(matchingIcons, resultsFromMatchingIcons, query);
            }
            //calling a method of the adapter class and passing the filtered list
            mGridAdapter.filterList(resultsFromAllIcons, resultsFromMatchingIcons);
        }
    }

    private static void getFilteredResults(List<String> originalList, List<String> filteredResults,
                                           String query) {
        for (String item : originalList) {
            if (item.contains(query)) filteredResults.add(item);
        }
    }
}