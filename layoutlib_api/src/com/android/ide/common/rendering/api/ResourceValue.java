/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.common.rendering.api;

import com.android.layoutlib.api.IResourceValue;
import com.android.resources.ResourceType;

/**
 * Represents an android resource with a name and a string value.
 */
@SuppressWarnings("deprecation")
public class ResourceValue implements IResourceValue {
    private final ResourceType mType;
    private final String mName;
    private String mValue = null;
    private final boolean mIsFramework;

    public ResourceValue(ResourceType type, String name, boolean isFramework) {
        mType = type;
        mName = name;
        mIsFramework = isFramework;
    }

    public ResourceValue(ResourceType type, String name, String value, boolean isFramework) {
        mType = type;
        mName = name;
        mValue = value;
        mIsFramework = isFramework;
    }

    public ResourceType getResourceType() {
        return mType;
    }

    /**
     * Returns the type of the resource. For instance "drawable", "color", etc...
     * @deprecated use {@link #getResourceType()} instead.
     */
    @Deprecated
    public String getType() {
        return mType.getName();
    }

    /**
     * Returns the name of the resource, as defined in the XML.
     */
    public final String getName() {
        return mName;
    }

    /**
     * Returns the value of the resource, as defined in the XML. This can be <code>null</code>
     */
    public final String getValue() {
        return mValue;
    }

    /**
     * Returns whether the resource is a framework resource (<code>true</code>) or a project
     * resource (<code>false</false>).
     */
    public final boolean isFramework() {
        return mIsFramework;
    }

    /**
     * Sets the value of the resource.
     * @param value the new value
     */
    public void setValue(String value) {
        mValue = value;
    }

    /**
     * Sets the value from another resource.
     * @param value the resource value
     */
    public void replaceWith(ResourceValue value) {
        mValue = value.mValue;
    }

    @Override
    public String toString() {
        return "ResourceValue [" + mType + "/" + mName + " = " + mValue  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + " (framework:" + mIsFramework + ")]"; //$NON-NLS-1$ //$NON-NLS-2$
    }


}
