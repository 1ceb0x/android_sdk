/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.ui;

import static com.android.AndroidConstants.FD_RES_VALUES;
import static com.android.ide.eclipse.adt.AdtConstants.EXT_XML;
import static com.android.ide.eclipse.adt.AdtConstants.WS_SEP;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.NAME_ATTR;
import static com.android.sdklib.SdkConstants.FD_RESOURCES;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors;
import com.android.ide.eclipse.adt.internal.editors.xml.Hyperlinks;
import com.android.ide.eclipse.adt.internal.refactorings.extractstring.ExtractStringRefactoring;
import com.android.ide.eclipse.adt.internal.refactorings.extractstring.ExtractStringWizard;
import com.android.ide.eclipse.adt.internal.resources.ResourceHelper;
import com.android.ide.eclipse.adt.internal.resources.ResourceNameValidator;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceItem;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceRepository;
import com.android.resources.ResourceType;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.window.Window;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.AbstractElementListSelectionDialog;
import org.eclipse.ui.dialogs.SelectionStatusDialog;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A dialog to let the user select a resource based on a resource type.
 */
@SuppressWarnings("restriction") // XML model
public class ResourceChooser extends AbstractElementListSelectionDialog {

    private Pattern mProjectResourcePattern;
    private ResourceType mResourceType;
    private final ResourceRepository mProjectResources;
    private final ResourceRepository mFrameworkResources;
    private Pattern mSystemResourcePattern;
    private Button mProjectButton;
    private Button mSystemButton;
    private Button mNewButton;
    private String mCurrentResource;
    private final IProject mProject;

    /**
     * Creates a Resource Chooser dialog.
     * @param project Project being worked on
     * @param type The type of the resource to choose
     * @param projectResources The repository for the project
     * @param frameworkResources The Framework resource repository
     * @param parent the parent shell
     */
    public ResourceChooser(IProject project, ResourceType type,
            ResourceRepository projectResources,
            ResourceRepository frameworkResources,
            Shell parent) {
        super(parent, new ResourceLabelProvider());
        mProject = project;

        mResourceType = type;
        mProjectResources = projectResources;
        mFrameworkResources = frameworkResources;

        mProjectResourcePattern = Pattern.compile(
                "@" + mResourceType.getName() + "/(.+)"); //$NON-NLS-1$ //$NON-NLS-2$

        mSystemResourcePattern = Pattern.compile(
                "@android:" + mResourceType.getName() + "/(.+)"); //$NON-NLS-1$ //$NON-NLS-2$

        setTitle("Resource Chooser");
        setMessage(String.format("Choose a %1$s resource",
                mResourceType.getDisplayName().toLowerCase()));
    }

    public void setCurrentResource(String resource) {
        mCurrentResource = resource;
    }

    public String getCurrentResource() {
        return mCurrentResource;
    }

    @Override
    protected void computeResult() {
        Object[] elements = getSelectedElements();
        if (elements.length == 1 && elements[0] instanceof ResourceItem) {
            ResourceItem item = (ResourceItem)elements[0];

            mCurrentResource = ResourceHelper.getXmlString(mResourceType, item,
                    mSystemButton.getSelection());
        }
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite top = (Composite)super.createDialogArea(parent);

        createMessageArea(top);

        createButtons(top);
        createFilterText(top);
        createFilteredList(top);

        // create the "New Resource" button
        createNewResButtons(top);

        setupResourceList();
        selectResourceString(mCurrentResource);

        return top;
    }

    /**
     * Creates the radio button to switch between project and system resources.
     * @param top the parent composite
     */
    private void createButtons(Composite top) {
        mProjectButton = new Button(top, SWT.RADIO);
        mProjectButton.setText("Project Resources");
        mProjectButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);
                if (mProjectButton.getSelection()) {
                    setupResourceList();
                    mNewButton.setEnabled(true);
                }
            }
        });
        mSystemButton = new Button(top, SWT.RADIO);
        mSystemButton.setText("System Resources");
        mSystemButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);
                if (mSystemButton.getSelection()) {
                    setupResourceList();
                    mNewButton.setEnabled(false);
                }
            }
        });
    }

    /**
     * Creates the "New Resource" button.
     * @param top the parent composite
     */
    private void createNewResButtons(Composite top) {
        mNewButton = new Button(top, SWT.NONE);

        String title = String.format("New %1$s...", mResourceType.getDisplayName());
        mNewButton.setText(title);

        // We only support adding new values right now
        mNewButton.setEnabled(ResourceNameValidator.isValueBasedResourceType(mResourceType));

        mNewButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                super.widgetSelected(e);

                if (mResourceType == ResourceType.STRING) {
                    createNewString();
                } else {
                    assert ResourceNameValidator.isValueBasedResourceType(mResourceType);
                    String newName = createNewValue(mResourceType);
                    if (newName != null) {
                        // Recompute the "current resource" to select the new id
                        ResourceItem[] items = setupResourceList();
                        selectItemName(newName, items);
                    }
                }
            }
        });
    }

    private String createNewValue(ResourceType type) {
        // Show a name/value dialog entering the key name and the value
        Shell shell = AdtPlugin.getDisplay().getActiveShell();
        if (shell == null) {
            return null;
        }
        NameValueDialog dialog = new NameValueDialog(shell, getFilter());
        if (dialog.open() != Window.OK) {
            return null;
        }

        String name = dialog.getName();
        String value = dialog.getValue();
        if (name.length() == 0 || value.length() == 0) {
            return null;
        }

        // Find "dimens.xml" file in res/values/ (or corresponding name for other
        // value types)
        String fileName = type.getName() + 's';
        String projectPath = FD_RESOURCES + WS_SEP + FD_RES_VALUES + WS_SEP + fileName + '.' + EXT_XML;
        IResource member = mProject.findMember(projectPath);
        if (member != null) {
            if (member instanceof IFile) {
                IFile file = (IFile) member;
                // File exists: Must add item to the XML
                IModelManager manager = StructuredModelManager.getModelManager();
                IStructuredModel model = null;
                try {
                    model = manager.getExistingModelForEdit(file);
                    if (model == null) {
                        model = manager.getModelForEdit(file);
                    }
                    if (model instanceof IDOMModel) {
                        model.beginRecording(this, String.format("Add %1$s",
                                type.getDisplayName()));
                        IDOMModel domModel = (IDOMModel) model;
                        Document document = domModel.getDocument();
                        Element root = document.getDocumentElement();
                        IStructuredDocument structuredDocument = model.getStructuredDocument();
                        Node lastElement = null;
                        NodeList childNodes = root.getChildNodes();
                        String indent = null;
                        for (int i = childNodes.getLength() - 1; i >= 0; i--) {
                            Node node = childNodes.item(i);
                            if (node.getNodeType() == Node.ELEMENT_NODE) {
                                lastElement = node;
                                indent = AndroidXmlEditor.getIndent(structuredDocument, node);
                                break;
                            }
                        }
                        if (indent == null || indent.length() == 0) {
                            indent = "    "; //$NON-NLS-1$
                        }
                        Node nextChild = lastElement != null ? lastElement.getNextSibling() : null;
                        Text indentNode = document.createTextNode('\n' + indent);
                        root.insertBefore(indentNode, nextChild);
                        Element element = document.createElement(Hyperlinks.getTagName(type));
                        element.setAttribute(NAME_ATTR, name);
                        root.insertBefore(element, nextChild);
                        Text valueNode = document.createTextNode(value);
                        element.appendChild(valueNode);
                        model.save();
                        return name;
                    }
                } catch (Exception e) {
                    AdtPlugin.log(e, "Cannot access XML value model");
                } finally {
                    if (model != null) {
                        model.endRecording(this);
                        model.releaseFromEdit();
                    }
                }
            }

            return null;
        } else {
            // No such file exists: just create it
            String prolog = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"; //$NON-NLS-1$
            StringBuilder sb = new StringBuilder(prolog);

            String root = ResourcesDescriptors.ROOT_ELEMENT;
            sb.append('<').append(root).append('>').append('\n');
            sb.append("    "); //$NON-NLS-1$
            sb.append('<');
            sb.append(type.getName());
            sb.append(" name=\""); //$NON-NLS-1$
            sb.append(name);
            sb.append('"');
            sb.append('>');
            sb.append(value);
            sb.append('<').append('/');
            sb.append(type.getName());
            sb.append(">\n");                            //$NON-NLS-1$
            sb.append('<').append('/').append(root).append('>').append('\n');
            String result = sb.toString();
            String error = null;
            try {
                byte[] buf = result.getBytes("UTF8");    //$NON-NLS-1$
                InputStream stream = new ByteArrayInputStream(buf);
                IFile file = mProject.getFile(new Path(projectPath));
                file.create(stream, true /*force*/, null /*progress*/);
                return name;
            } catch (UnsupportedEncodingException e) {
                error = e.getMessage();
            } catch (CoreException e) {
                error = e.getMessage();
            }

            error = String.format("Failed to generate %1$s: %2$s", name, error);
            AdtPlugin.displayError("New Android XML File", error);
        }
        return null;
    }

    private void createNewString() {
        ExtractStringRefactoring ref = new ExtractStringRefactoring(
                mProject, true /*enforceNew*/);
        RefactoringWizard wizard = new ExtractStringWizard(ref, mProject);
        RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard);
        try {
            IWorkbench w = PlatformUI.getWorkbench();
            if (op.run(w.getDisplay().getActiveShell(), wizard.getDefaultPageTitle()) ==
                    IDialogConstants.OK_ID) {

                // Recompute the "current resource" to select the new id
                ResourceItem[] items = setupResourceList();

                // select it if possible
                selectItemName(ref.getXmlStringId(), items);
            }
        } catch (InterruptedException ex) {
            // Interrupted. Pass.
        }
    }

    /**
     * Setups the current list.
     */
    private ResourceItem[] setupResourceList() {
        Collection<ResourceItem> items = null;
        if (mProjectButton.getSelection()) {
            items = mProjectResources.getResourceItemsOfType(mResourceType);
        } else if (mSystemButton.getSelection()) {
            items = mFrameworkResources.getResourceItemsOfType(mResourceType);
        }

        ResourceItem[] arrayItems = items.toArray(new ResourceItem[items.size()]);

        // sort the array
        Arrays.sort(arrayItems);

        setListElements(arrayItems);

        return arrayItems;
    }

    /**
     * Select an item by its name, if possible.
     */
    private void selectItemName(String itemName, ResourceItem[] items) {
        if (itemName == null || items == null) {
            return;
        }

        for (ResourceItem item : items) {
            if (itemName.equals(item.getName())) {
                setSelection(new Object[] { item });
                break;
            }
        }
    }

    /**
     * Select an item by its full resource string.
     * This also selects between project and system repository based on the resource string.
     */
    private void selectResourceString(String resourceString) {
        boolean isSystem = false;
        String itemName = null;

        // Is this a system resource?
        // If not a system resource or if they are not available, this will be a project res.
        Matcher m = mSystemResourcePattern.matcher(resourceString);
        if (m.matches()) {
            itemName = m.group(1);
            isSystem = true;
        }

        if (!isSystem && itemName == null) {
            // Try to match project resource name
            m = mProjectResourcePattern.matcher(resourceString);
            if (m.matches()) {
                itemName = m.group(1);
            }
        }

        // Update the repository selection
        mProjectButton.setSelection(!isSystem);
        mSystemButton.setSelection(isSystem);
        mNewButton.setEnabled(!isSystem);

        // Update the list
        ResourceItem[] items = setupResourceList();

        // If we have a selection name, select it
        if (itemName != null) {
            selectItemName(itemName, items);
        }
    }

    /** Dialog asking for a Name/Value pair */
    private class NameValueDialog extends SelectionStatusDialog implements Listener {
        private org.eclipse.swt.widgets.Text mNameText;
        private org.eclipse.swt.widgets.Text mValueText;
        private String mInitialName;
        private String mName;
        private String mValue;
        private ResourceNameValidator mValidator;

        public NameValueDialog(Shell parent, String initialName) {
            super(parent);
            mInitialName = initialName;
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Composite container = new Composite(parent, SWT.NONE);
            container.setLayout(new GridLayout(2, false));
            GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
            // Wide enough to accommodate the error label
            gridData.widthHint = 500;
            container.setLayoutData(gridData);


            Label nameLabel = new Label(container, SWT.NONE);
            nameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
            nameLabel.setText("Name:");

            mNameText = new org.eclipse.swt.widgets.Text(container, SWT.BORDER);
            mNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
            if (mInitialName != null) {
                mNameText.setText(mInitialName);
                mNameText.selectAll();
            }

            Label valueLabel = new Label(container, SWT.NONE);
            valueLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
            valueLabel.setText("Value:");

            mValueText = new org.eclipse.swt.widgets.Text(container, SWT.BORDER);
            mValueText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

            mNameText.addListener(SWT.Modify, this);
            mValueText.addListener(SWT.Modify, this);

            validate();

            return container;
        }

        @Override
        protected void computeResult() {
            mName = mNameText.getText().trim();
            mValue = mValueText.getText().trim();
        }

        private String getName() {
            return mName;
        }

        private String getValue() {
            return mValue;
        }

        public void handleEvent(Event event) {
            validate();
        }

        private void validate() {
            IStatus status;
            computeResult();
            if (mName.length() == 0) {
                status = new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID, "Enter a name");
            } else if (mValue.length() == 0) {
                status = new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID, "Enter a value");
            } else {
                if (mValidator == null) {
                    mValidator = ResourceNameValidator.create(false, mProject, mResourceType);
                }
                String error = mValidator.isValid(mName);
                if (error != null) {
                    status = new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID, error);
                } else {
                    status = new Status(IStatus.OK, AdtPlugin.PLUGIN_ID, null);
                }
            }
            updateStatus(status);
        }
    }
}
