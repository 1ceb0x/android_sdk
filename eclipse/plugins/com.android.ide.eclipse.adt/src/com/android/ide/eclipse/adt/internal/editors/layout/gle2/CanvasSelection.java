/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.sdklib.SdkConstants;

import org.eclipse.swt.graphics.Rectangle;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one selection in {@link LayoutCanvas}.
 */
/* package */ class CanvasSelection {

    /** Current selected view info. Can be null. */
    private final CanvasViewInfo mCanvasViewInfo;

    /** Current selection border rectangle. Null when mCanvasViewInfo is null . */
    private final Rectangle mRect;

    /** The node proxy for drawing the selection. Null when mCanvasViewInfo is null. */
    private final NodeProxy mNodeProxy;

    /** The name displayed over the selection, typically the widget class name. Can be null. */
    private final String mName;

    /**
     * Creates a new {@link CanvasSelection} object.
     * @param canvasViewInfo The view info being selected. Must not be null.
     * @param nodeFactory
     */
    public CanvasSelection(CanvasViewInfo canvasViewInfo,
            RulesEngine gre,
            NodeFactory nodeFactory) {

        assert canvasViewInfo != null;

        mCanvasViewInfo = canvasViewInfo;

        if (canvasViewInfo == null) {
            mRect = null;
            mNodeProxy = null;
        } else {
            Rectangle r = canvasViewInfo.getSelectionRect();
            mRect = new Rectangle(r.x, r.y, r.width, r.height);
            mNodeProxy = nodeFactory.create(canvasViewInfo);
        }

        mName = initDisplayName(canvasViewInfo, gre);
    }

    /**
     * Returns true when this selection item represents the root, the top level
     * layout element in the editor.
     * @return True if and only if this element is at the root of the hierarchy
     */
    public boolean isRoot() {
        return mNodeProxy.getParent() == null;
    }

    /**
     * Returns the selected view info. Cannot be null.
     */
    public CanvasViewInfo getViewInfo() {
        return mCanvasViewInfo;
    }

    /**
     * Returns the selection border rectangle.
     * Cannot be null.
     */
    public Rectangle getRect() {
        return mRect;
    }

    /**
     * The name displayed over the selection, typically the widget class name.
     * Can be null.
     */
    public String getName() {
        return mName;
    }

    /** Returns the node associated with this selection (may be null) */
    /* package */ NodeProxy getNode() {
        return mNodeProxy;
    }

    //----

    private String initDisplayName(CanvasViewInfo canvasViewInfo, RulesEngine gre) {
        if (canvasViewInfo == null) {
            return null;
        }

        String fqcn = canvasViewInfo.getName();
        if (fqcn == null) {
            return null;
        }

        if (fqcn.equals(SdkConstants.CLASS_MOCK_VIEW)) {
            // The MockView class from the layout bridge is used to display views that
            // cannot be rendered properly (such as SurfaceView or missing custom views).
            // This view itself already displays the class name it represents so we don't
            // need to display anything here.
            return "";
        }

        String name = gre.callGetDisplayName(canvasViewInfo.getUiViewNode());

        if (name == null) {
            // The name is typically a fully-qualified class name. Let's make it a tad shorter.

            if (fqcn.startsWith("android.")) {                                      // $NON-NLS-1$
                // For android classes, convert android.foo.Name to android...Name
                int first = fqcn.indexOf('.');
                int last = fqcn.lastIndexOf('.');
                if (last > first) {
                    name = fqcn.substring(0, first) + ".." + fqcn.substring(last);   // $NON-NLS-1$
                }
            } else {
                // For custom non-android classes, it's best to keep the 2 first segments of
                // the namespace, e.g. we want to get something like com.example...MyClass
                int first = fqcn.indexOf('.');
                first = fqcn.indexOf('.', first + 1);
                int last = fqcn.lastIndexOf('.');
                if (last > first) {
                    name = fqcn.substring(0, first) + ".." + fqcn.substring(last);   // $NON-NLS-1$
                }
            }
        }

        return name;
    }

    /**
     * Gets the XML text from the given selection for a text transfer.
     * The returned string can be empty but not null.
     */
    /* package */ static String getAsText(LayoutCanvas canvas, List<CanvasSelection> selection) {
        StringBuilder sb = new StringBuilder();

        LayoutEditor layoutEditor = canvas.getLayoutEditor();
        for (CanvasSelection cs : selection) {
            CanvasViewInfo vi = cs.getViewInfo();
            UiViewElementNode key = vi.getUiViewNode();
            Node node = key.getXmlNode();
            String t = layoutEditor.getXmlText(node);
            if (t != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(t);
            }
        }

        return sb.toString();
    }

    /**
     * Returns elements representing the given selection of canvas items.
     *
     * @param items Items to wrap in elements
     * @return An array of wrapper elements. Never null.
     */
    /* package */ static SimpleElement[] getAsElements(List<CanvasSelection> items) {
        ArrayList<SimpleElement> elements = new ArrayList<SimpleElement>();

        for (CanvasSelection cs : items) {
            CanvasViewInfo vi = cs.getViewInfo();

            SimpleElement e = vi.toSimpleElement();
            elements.add(e);
        }

        return elements.toArray(new SimpleElement[elements.size()]);
    }
}
