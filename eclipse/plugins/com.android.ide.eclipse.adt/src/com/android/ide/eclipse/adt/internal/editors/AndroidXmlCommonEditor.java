/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors;

import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.XmlEditorDelegate.IXmlEditorCreator;
import com.android.ide.eclipse.adt.internal.editors.animator.AnimationEditorDelegate;
import com.android.ide.eclipse.adt.internal.editors.color.ColorEditorDelegate;
import com.android.ide.eclipse.adt.internal.editors.drawable.DrawableEditorDelegate;
import com.android.ide.eclipse.adt.internal.editors.menu.MenuEditorDelegator;
import com.android.ide.eclipse.adt.internal.editors.resources.ResourcesEditorDelegate;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.editors.xml.OtherXmlEditorDelegate;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IShowEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.w3c.dom.Document;

/**
 * Multi-page form editor for ALL /res XML files.
 *
 * This editor doesn't actually do anything. Instead, it defers actual implementation
 * to {@link XmlEditorDelegate} instances.
 */
public class AndroidXmlCommonEditor extends AndroidXmlEditor implements IShowEditorInput {

    public static final String ID = AdtConstants.EDITORS_NAMESPACE + ".XmlCommonEditor"; //$NON-NLS-1$

    /**
     * Registered {@link XmlEditorDelegate}s.
     * All delegates must have a {@code Creator} class which is instantiated
     * once here statically. All the creators are invoked in the order they
     * are defined and the first one to return a non-null delegate is used.
     */
    private static final IXmlEditorCreator[] DELEGATES = {
            /* TODO next CL: new ManifestEditorDelegate.Creator(), */
            /* TODO next CL: new LayoutEditorDelegate.Creator(), */
            new ResourcesEditorDelegate.Creator(),
            new AnimationEditorDelegate.Creator(),
            new ColorEditorDelegate.Creator(),
            new DrawableEditorDelegate.Creator(),
            new MenuEditorDelegator.Creator(),
            new OtherXmlEditorDelegate.Creator(),
    };

    private XmlEditorDelegate mDelegate = null;

    /**
     * Creates the form editor for resources XML files.
     */
    public AndroidXmlCommonEditor() {
        super();
    }

    @Override
    public void init(IEditorSite site, IEditorInput editorInput)
            throws PartInitException {
        super.init(site, editorInput);

        if (editorInput instanceof IFileEditorInput) {

            IFileEditorInput fileInput = (IFileEditorInput) editorInput;
            IFile file = fileInput.getFile();

            IEditorDescriptor desc = IDE.getDefaultEditor(file);
            if (desc != null) {
                String id = desc.getId();
                if (id != null && !id.equals(ID) && id.startsWith(AdtConstants.EDITORS_NAMESPACE)) {
                    // It starts by our editor namespace but it's not the right ID.
                    // This is an old Android XML ID. Change it to our new ID.
                    IDE.setDefaultEditor(file, ID);
                }
            }

            for (IXmlEditorCreator creator : DELEGATES) {
                mDelegate = creator.createForFile(this, fileInput);
                if (mDelegate != null) {
                    return;
                }
            }

            // We didn't find any editor.
            // We'll use the OtherXmlEditorDelegate as a catch-all editor.
            AdtPlugin.log(IStatus.INFO,
                    "No valid Android XML Editor Delegate found for file %1$s",
                    file.getFullPath());
            mDelegate = new OtherXmlEditorDelegate(this);
            return;
        }

        // We can't do anything if we don't have a valid file.
        AdtPlugin.log(IStatus.INFO,
                "Android XML Editor cannot process non-file input %1$s" +
                (editorInput == null ? "null" : editorInput.toString()));
        throw new PartInitException("Android XML Editor cannot process this input.");
    }

    /**
     * @return The root node of the UI element hierarchy
     */
    @Override
    public UiElementNode getUiRootNode() {
        return mDelegate == null ? null : mDelegate.getUiRootNode();
    }

    public XmlEditorDelegate getDelegate() {
        return mDelegate;
    }

    // ---- Base Class Overrides ----

    @Override
    public void dispose() {
        if (mDelegate != null) {
            mDelegate.dispose();
        }

        super.dispose();
    }

    /**
     * Save the XML.
     * <p/>
     * The actual save operation is done in the super class by committing
     * all data to the XML model and then having the Structured XML Editor
     * save the XML.
     * <p/>
     * Here we just need to tell the graphical editor that the model has
     * been saved.
     */
    @Override
    public void doSave(IProgressMonitor monitor) {
        super.doSave(monitor);
        if (mDelegate != null) {
            mDelegate.doSave(monitor);
        }
    }

    /**
     * Returns whether the "save as" operation is supported by this editor.
     * <p/>
     * Save-As is a valid operation for the ManifestEditor since it acts on a
     * single source file.
     *
     * @see IEditorPart
     */
    @Override
    public boolean isSaveAsAllowed() {
        return mDelegate == null ? false : mDelegate.isSaveAsAllowed();
    }

    /**
     * Create the various form pages.
     */
    @Override
    protected void createFormPages() {
        if (mDelegate != null) {
            mDelegate.createFormPages();
        }
    }

    @Override
    protected void postCreatePages() {
        super.postCreatePages();

        if (mDelegate != null) {
            mDelegate.postCreatePages();
        }
    }

    /* (non-java doc)
     * Change the tab/title name to include the name of the layout.
     */
    @Override
    protected void setInput(IEditorInput input) {
        super.setInput(input);
        if (mDelegate != null) {
            mDelegate.setInput(input);
        }
    }

    /**
     * Processes the new XML Model, which XML root node is given.
     *
     * @param xml_doc The XML document, if available, or null if none exists.
     */
    @Override
    protected void xmlModelChanged(Document xml_doc) {
        if (mDelegate != null) {
            mDelegate.xmlModelChanged(xml_doc);
        }
        super.xmlModelChanged(xml_doc);
    }

    @Override
    public Job runLint() {
        Job job = super.runLint();
        if (mDelegate instanceof XmlLayoutEditDelegate) {
            ((XmlLayoutEditDelegate) mDelegate).postRunLintJob(job);
        }
        return job;
    }

    /**
     * Returns the custom IContentOutlinePage or IPropertySheetPage when asked for it.
     */
    @Override
    public Object getAdapter(@SuppressWarnings("rawtypes") Class adapter) {
        if (mDelegate != null) {
            Object value = mDelegate.getAdapter(adapter);
            if (value != null) {
                return value;
            }
        }

        // return default
        return super.getAdapter(adapter);
    }

    @Override
    protected void pageChange(int newPageIndex) {
        if (mDelegate != null) {
            mDelegate.pageChange(newPageIndex);
        }

        super.pageChange(newPageIndex);

        if (mDelegate != null) {
            mDelegate.postPageChange(newPageIndex);
        }
    }

    @Override
    public void initUiRootNode(boolean force) {
        if (mDelegate != null) {
            mDelegate.initUiRootNode(force);
        }
    }

    @Override
    public void showEditorInput(IEditorInput editorInput) {
        if (mDelegate instanceof XmlLayoutEditDelegate) {
            ((XmlLayoutEditDelegate) mDelegate).showEditorInput(editorInput);
        }
    }


    // --------------------
    // Base methods exposed so that XmlEditorDelegate can access them

    @Override
    public void setPartName(String partName) {
        super.setPartName(partName);
    }

    @Override
    public void setPageText(int pageIndex, String text) {
        super.setPageText(pageIndex, text);
    }

    @Override
    public void firePropertyChange(int propertyId) {
        super.firePropertyChange(propertyId);
    }

    @Override
    public int getPageCount() {
        return super.getPageCount();
    }

    @Override
    public int getCurrentPage() {
        return super.getCurrentPage();
    }
}
