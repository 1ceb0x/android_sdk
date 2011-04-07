/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.sdklib;


import com.android.sdklib.SdkManager.LayoutlibVersion;

public class SdkManagerTest extends SdkManagerTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SuppressWarnings("deprecation")
    public void testSdkManager_LayoutlibVersion() {
        SdkManager sdkman = getSdkManager();
        IAndroidTarget t = sdkman.getTargets()[0];

        assertTrue(t instanceof PlatformTarget);

        LayoutlibVersion lv = ((PlatformTarget) t).getLayoutlibVersion();
        assertNotNull(lv);
        assertEquals(5, lv.getApi());
        assertEquals(2, lv.getRevision());

        assertSame(lv, sdkman.getMaxLayoutlibVersion());
    }
}