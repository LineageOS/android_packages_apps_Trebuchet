/*
 *  Copyright (c) 2016. The CyanogenMod Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.android.launcher3.stats.internal.model;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

public class RemoteFolderAction implements ITrackingAction {
    @Override
    public List<Bundle> createTrackingBundles(String trackingId, TrackingEvent.Category category,
                                              List<TrackingEvent> eventList) {
        return new ArrayList<Bundle>();
    }
}
