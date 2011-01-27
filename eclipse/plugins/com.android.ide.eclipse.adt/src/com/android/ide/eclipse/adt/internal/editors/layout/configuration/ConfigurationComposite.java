/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.layout.configuration;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.sdk.LoadStatus;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.resources.ResourceType;
import com.android.ide.eclipse.adt.internal.resources.configurations.DockModeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.adt.internal.resources.configurations.LanguageQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.NightModeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.PixelDensityQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.RegionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ResourceQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenDimensionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenOrientationQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.VersionQualifier;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFile;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFolder;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFolderType;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.LayoutDevice;
import com.android.ide.eclipse.adt.internal.sdk.LayoutDeviceManager;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.internal.sdk.LayoutDevice.DeviceConfig;
import com.android.resources.Density;
import com.android.resources.DockMode;
import com.android.resources.NightMode;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.IAndroidTarget;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;

/**
 * A composite that displays the current configuration displayed in a Graphical Layout Editor.
 * <p/>
 * The composite has several entry points:<br>
 * - {@link #setFile(IFile)}<br>
 *   Called after the constructor to set the file being edited. Nothing else is performed.<br>
 *<br>
 * - {@link #onXmlModelLoaded()}<br>
 *   Called when the XML model is loaded, either the first time or when the Target/SDK changes.
 *   This initializes the UI, either with the first compatible configuration found, or attempts
 *   to restore a configuration if one is found to have been saved in the file persistent storage.
 *   (see {@link #storeState()})<br>
 *<br>
 * - {@link #replaceFile(IFile)}<br>
 *   Called when a file, representing the same resource but with a different config is opened<br>
 *   by the user.<br>
 *<br>
 * - {@link #changeFileOnNewConfig(IFile)}<br>
 *   Called when config change triggers the editing of a file with a different config.
 *<p/>
 * Additionally, the composite can handle the following events.<br>
 * - SDK reload. This is when the main SDK is finished loading.<br>
 * - Target reload. This is when the target used by the project is the edited file has finished<br>
 *   loading.<br>
 */
public class ConfigurationComposite extends Composite {

    public final static QualifiedName NAME_CONFIG_STATE =
        new QualifiedName(AdtPlugin.PLUGIN_ID, "state");//$NON-NLS-1$
    private final static String THEME_SEPARATOR = "----------"; //$NON-NLS-1$

    private final static int LOCALE_LANG = 0;
    private final static int LOCALE_REGION = 1;

    private Label mCurrentLayoutLabel;
    private Button mCreateButton;

    private Combo mDeviceCombo;
    private Combo mDeviceConfigCombo;
    private Combo mLocaleCombo;
    private Combo mDockCombo;
    private Combo mNightCombo;
    private Combo mThemeCombo;
    private Combo mTargetCombo;

    private int mPlatformThemeCount = 0;
    /** updates are disabled if > 0 */
    private int mDisableUpdates = 0;

    private List<LayoutDevice> mDeviceList;
    private final List<IAndroidTarget> mTargetList = new ArrayList<IAndroidTarget>();

    private final ArrayList<ResourceQualifier[] > mLocaleList =
        new ArrayList<ResourceQualifier[]>();

    private final ConfigState mState = new ConfigState();

    private boolean mSdkChanged = false;
    private boolean mFirstXmlModelChange = true;

    /** The config listener given to the constructor. Never null. */
    private final IConfigListener mListener;

    /** The {@link FolderConfiguration} representing the state of the UI controls */
    private final FolderConfiguration mCurrentConfig = new FolderConfiguration();

    /** The file being edited */
    private IFile mEditedFile;
    /** The {@link ProjectResources} for the edited file's project */
    private ProjectResources mResources;
    /** The target of the project of the file being edited. */
    private IAndroidTarget mProjectTarget;
    /** The target of the project of the file being edited. */
    private IAndroidTarget mRenderingTarget;
    /** The {@link FolderConfiguration} being edited. */
    private FolderConfiguration mEditedConfig;
    /** Serialized state to use when initializing the configuration after the SDK is loaded */
    private String mInitialState;

    /**
     * Interface implemented by the part which owns a {@link ConfigurationComposite}.
     * This notifies the owners when the configuration change.
     * The owner must also provide methods to provide the configuration that will
     * be displayed.
     */
    public interface IConfigListener {
        /**
         * Called when the {@link FolderConfiguration} change. The new config can be queried
         * with {@link ConfigurationComposite#getCurrentConfig()}.
         */
        void onConfigurationChange();

        /**
         * Called when the current theme changes. The theme can be queried with
         * {@link ConfigurationComposite#getTheme()}.
         */
        void onThemeChange();

        /**
         * Called when the "Create" button is clicked.
         */
        void onCreate();

        /**
         * Called before the rendering target changes.
         * @param oldTarget the old rendering target
         */
        void onRenderingTargetPreChange(IAndroidTarget oldTarget);

        /**
         * Called after the rendering target changes.
         *
         * @param target the new rendering target
         */
        void onRenderingTargetPostChange(IAndroidTarget target);

        ProjectResources getProjectResources();
        ProjectResources getFrameworkResources();
        ProjectResources getFrameworkResources(IAndroidTarget target);
        Map<String, Map<String, ResourceValue>> getConfiguredProjectResources();
        Map<String, Map<String, ResourceValue>> getConfiguredFrameworkResources();
    }

    /**
     * State of the current config. This is used during UI reset to attempt to return the
     * rendering to its original configuration.
     */
    private class ConfigState {
        private final static String SEP = ":"; //$NON-NLS-1$
        private final static String SEP_LOCALE = "-"; //$NON-NLS-1$

        LayoutDevice device;
        String configName;
        ResourceQualifier[] locale;
        String theme;
        /** dock mode. Guaranteed to be non null */
        DockMode dock = DockMode.NONE;
        /** night mode. Guaranteed to be non null */
        NightMode night = NightMode.NOTNIGHT;
        /** the version being targeted for rendering */
        IAndroidTarget target;

        String getData() {
            StringBuilder sb = new StringBuilder();
            if (device != null) {
                sb.append(device.getName());
                sb.append(SEP);
                sb.append(configName);
                sb.append(SEP);
                if (locale != null) {
                    if (locale[0] != null && locale[1] != null) {
                        // locale[0]/[1] can be null sometimes when starting Eclipse
                        sb.append(((LanguageQualifier) locale[0]).getValue());
                        sb.append(SEP_LOCALE);
                        sb.append(((RegionQualifier) locale[1]).getValue());
                    }
                }
                sb.append(SEP);
                sb.append(theme);
                sb.append(SEP);
                sb.append(dock.getResourceValue());
                sb.append(SEP);
                sb.append(night.getResourceValue());
                sb.append(SEP);
                if (target != null) {
                    sb.append(targetToString(target));
                    sb.append(SEP);
                }
            }

            return sb.toString();
        }

        boolean setData(String data) {
            String[] values = data.split(SEP);
            if (values.length == 6 || values.length == 7) {
                for (LayoutDevice d : mDeviceList) {
                    if (d.getName().equals(values[0])) {
                        device = d;
                        FolderConfiguration config = device.getFolderConfigByName(values[1]);
                        if (config != null) {
                            configName = values[1];

                            locale = new ResourceQualifier[2];
                            String locales[] = values[2].split(SEP_LOCALE);
                            if (locales.length >= 2) {
                                if (locales[0].length() > 0) {
                                    locale[0] = new LanguageQualifier(locales[0]);
                                }
                                if (locales[1].length() > 0) {
                                    locale[1] = new RegionQualifier(locales[1]);
                                }
                            }

                            theme = values[3];
                            dock = DockMode.getEnum(values[4]);
                            if (dock == null) {
                                dock = DockMode.NONE;
                            }
                            night = NightMode.getEnum(values[5]);
                            if (night == null) {
                                night = NightMode.NOTNIGHT;
                            }

                            if (values.length == 7 && mTargetList != null) {
                                target = stringToTarget(values[6]);
                            }

                            return true;
                        }
                    }
                }
            }

            return false;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (device != null) {
                sb.append(device.getName());
            } else {
                sb.append("null");
            }
            sb.append(SEP);
            sb.append(configName);
            sb.append(SEP);
            if (locale != null) {
                sb.append(((LanguageQualifier) locale[0]).getValue());
                sb.append(SEP_LOCALE);
                sb.append(((RegionQualifier) locale[1]).getValue());
            }
            sb.append(SEP);
            sb.append(theme);
            sb.append(SEP);
            sb.append(dock.getResourceValue());
            sb.append(SEP);
            sb.append(night.getResourceValue());
            sb.append(SEP);

            if (target != null) {
                sb.append(targetToString(target));
                sb.append(SEP);
            }

            return sb.toString();
        }

        /**
         * Returns a String id to represent an {@link IAndroidTarget} which can be translated
         * back to an {@link IAndroidTarget} by the matching {@link #stringToTarget}. The id
         * will never contain the {@link #SEP} character.
         *
         * @param target the target to return an id for
         * @return an id for the given target; never null
         */
        private String targetToString(IAndroidTarget target) {
            return target.getFullName().replace(SEP, "");  //$NON-NLS-1$
        }

        /**
         * Returns an {@link IAndroidTarget} that corresponds to the given id that was
         * originally returned by {@link #targetToString}. May be null, if the platform is no
         * longer available, or if the platform list has not yet been initialized.
         *
         * @param id the id that corresponds to the desired platform
         * @return an {@link IAndroidTarget} that matches the given id, or null
         */
        private IAndroidTarget stringToTarget(String id) {
            if (mTargetList != null && mTargetList.size() > 0) {
                for (IAndroidTarget target : mTargetList) {
                    if (id.equals(targetToString(target))) {
                        return target;
                    }
                }
            }

            return null;
        }
    }

    /**
     * Abstract class implemented by the part which owns a {@link ConfigurationComposite}
     * to define and handle custom buttons in the button bar. Each button is
     * implemented using a button, with a callback when the button is selected.
     */
    public static abstract class CustomButton {

        /** The UI label of the toggle. Can be null if the image exists. */
        private final String mUiLabel;

        /** The image to use for this toggle. Can be null if the label exists. */
        private final Image mImage;

        /** The tooltip for the toggle. Can be null. */
        private final String mUiTooltip;

        /** Whether the button is a toggle */
        private final boolean mIsToggle;
        /** The default value of the toggle. */
        private final boolean mDefaultValue;

        /**
         * the SWT button.
         */
        private Button mButton;


        /**
         * Initializes a new {@link CustomButton}. The values set here will be used
         * later to create the actual button.
         *
         * @param uiLabel      The UI label of the button. Can be null if the image exists.
         * @param image        The image to use for this button. Can be null if the label exists.
         * @param uiTooltip    The tooltip for the button. Can be null.
         * @param isToggle     Whether the button is a toggle button.
         * @param defaultValue The default value of the toggle if <var>isToggle</var> is true.
         */
        public CustomButton(
                String uiLabel,
                Image image,
                String uiTooltip,
                boolean isToggle,
                boolean defaultValue) {
            mUiLabel = uiLabel;
            mImage = image;
            mUiTooltip = uiTooltip;
            mIsToggle = isToggle;
            mDefaultValue = defaultValue;
        }

        /**
         * Initializes a new {@link CustomButton} that is <b>not</b> a toggle.
         * The values set here will be used later to create the actual button.
         *
         * @param uiLabel      The UI label of the button. Can be null if the image exists.
         * @param image        The image to use for this button. Can be null if the label exists.
         * @param uiTooltip    The tooltip for the button. Can be null.
         */
        public CustomButton(
                String uiLabel,
                Image image,
                String uiTooltip) {
            this(uiLabel, image, uiTooltip, false, false);
        }


        /**
         * Called by the {@link ConfigurationComposite} when the button is selected
         * @param newState the state of the button if the button is a toggle.
         */
        public abstract void onSelected(boolean newState);

        private void createButton(Composite parent) {
            int style = SWT.FLAT;
            if (mIsToggle) {
                style |= SWT.TOGGLE;
            }
            mButton = new Button(parent, style);

            if (mUiTooltip != null) {
                mButton.setToolTipText(mUiTooltip);
            }
            if (mImage != null) {
                mButton.setImage(mImage);
            }
            if (mUiLabel != null) {
                mButton.setText(mUiLabel);
            }

            if (mIsToggle && mDefaultValue) {
                mButton.setSelection(true);
            }

            mButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    onSelected(mButton.getSelection());
                }
            });
        }

        public boolean isEnabled() {
            return mButton != null && mButton.isEnabled();
        }

        public void setEnabled(boolean enabledState) {
            if (mButton != null) {
                mButton.setEnabled(enabledState);
            }
        }

        public void setToolTipText(String text) {
            if (mButton != null) {
                mButton.setToolTipText(text);
            }
        }

        public void setSelection(boolean selected) {
            if (mButton != null) {
                mButton.setSelection(selected);
            }
        }

        public boolean getSelection() {
            if (mButton != null) {
                return mButton.getSelection();
            }

            return mDefaultValue;
        }
    }

    /**
     * Creates a new {@link ConfigurationComposite} and adds it to the parent.
     *
     * The method also receives custom buttons to set into the configuration composite. The list
     * is organized as an array of arrays. Each array represents a group of buttons thematically
     * grouped together.
     *
     * @param listener An {@link IConfigListener} that gets and sets configuration properties.
     *          Mandatory, cannot be null.
     * @param customButtons An array of array of {@link CustomButton} to define extra action/toggle
     *          buttons to display at the top of the composite. Can be empty or null.
     * @param parent The parent composite.
     * @param style The style of this composite.
     * @param initialState The initial state (serialized form) to use for the configuration
     */
    public ConfigurationComposite(IConfigListener listener,
            CustomButton[][] customButtons,
            Composite parent, int style, String initialState) {
        super(parent, style);
        mListener = listener;
        mInitialState = initialState;

        if (customButtons == null) {
            customButtons = new CustomButton[0][0];
        }

        GridLayout gl;
        GridData gd;
        int cols = 9;  // device+config+locale+dock+day/night+separator*2+theme+apiLevel

        // ---- First line: custom buttons, clipping button, editing config display.
        Composite labelParent = new Composite(this, SWT.NONE);
        labelParent.setLayout(gl = new GridLayout(2 + customButtons.length + 1, false));
        gl.marginWidth = gl.marginHeight = 0;
        labelParent.setLayoutData(gd = new GridData(GridData.FILL_HORIZONTAL));
        gd.horizontalSpan = cols;

        new Label(labelParent, SWT.NONE).setText("Editing config:");
        mCurrentLayoutLabel = new Label(labelParent, SWT.NONE);
        mCurrentLayoutLabel.setLayoutData(gd = new GridData(GridData.FILL_HORIZONTAL));
        gd.widthHint = 50;

        for (CustomButton[] buttons : customButtons) {
            if (buttons.length == 1) {
                buttons[0].createButton(labelParent);
            } else if (buttons.length > 1) {
                Composite buttonParent = new Composite(labelParent, SWT.NONE);
                buttonParent.setLayout(gl = new GridLayout(buttons.length, false));
                gl.marginWidth = gl.marginHeight = 0;
                gl.horizontalSpacing = 1;
                for (CustomButton button : buttons) {
                    button.createButton(buttonParent);
                }

            }
        }

        mCreateButton = new Button(labelParent, SWT.PUSH | SWT.FLAT);
        mCreateButton.setText("Create...");
        mCreateButton.setEnabled(false);
        mCreateButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (mListener != null) {
                    mListener.onCreate();
                }
            }
        });

        // ---- 2nd line: device/config/locale/theme Combos, create button.

        setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        setLayout(gl = new GridLayout(cols, false));
        gl.marginHeight = 0;
        gl.horizontalSpacing = 0;

        mDeviceCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mDeviceCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mDeviceCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onDeviceChange(true /* recomputeLayout*/);
            }
        });

        mDeviceConfigCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mDeviceConfigCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mDeviceConfigCombo.addSelectionListener(new SelectionAdapter() {
            @Override
             public void widgetSelected(SelectionEvent e) {
                onDeviceConfigChange();
            }
        });

        mLocaleCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mLocaleCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mLocaleCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onLocaleChange();
            }
        });

        mDockCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mDockCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        for (DockMode mode : DockMode.values()) {
            mDockCombo.add(mode.getLongDisplayValue());
        }
        mDockCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onDockChange();
            }
        });

        mNightCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mNightCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        for (NightMode mode : NightMode.values()) {
            mNightCombo.add(mode.getLongDisplayValue());
        }
        mNightCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onDayChange();
            }
        });

        // first separator
        Label separator = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        separator.setLayoutData(gd = new GridData(
                GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_VERTICAL));
        gd.heightHint = 0;

        mThemeCombo = new Combo(this, SWT.READ_ONLY | SWT.DROP_DOWN);
        mThemeCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mThemeCombo.setEnabled(false);

        mThemeCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onThemeChange();
            }
        });

        // second separator
        separator = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        separator.setLayoutData(gd = new GridData(
                GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_VERTICAL));
        gd.heightHint = 0;

        mTargetCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mTargetCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mTargetCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onRenderingTargetChange();
            }
        });
    }

    // ---- Init and reset/reload methods ----

    /**
     * Sets the reference to the file being edited.
     * <p/>The UI is initialized in {@link #onXmlModelLoaded()} which is called as the XML model is
     * loaded (or reloaded as the SDK/target changes).
     *
     * @param file the file being opened
     *
     * @see #onXmlModelLoaded()
     * @see #replaceFile(IFile)
     * @see #changeFileOnNewConfig(IFile)
     */
    public void setFile(IFile file) {
        mEditedFile = file;
    }

    /**
     * Replaces the UI with a given file configuration. This is meant to answer the user
     * explicitly opening a different version of the same layout from the Package Explorer.
     * <p/>This attempts to keep the current config, but may change it if it's not compatible or
     * not the best match
     * <p/>This will NOT trigger a redraw event (will not call
     * {@link IConfigListener#onConfigurationChange()}.)
     * @param file the file being opened.
     */
    public void replaceFile(IFile file) {
        // if there is no previous selection, revert to default mode.
        if (mState.device == null) {
            setFile(file); // onTargetChanged will be called later.
            return;
        }

        mEditedFile = file;
        IProject iProject = mEditedFile.getProject();
        mResources = ResourceManager.getInstance().getProjectResources(iProject);

        ResourceFolder resFolder = ResourceManager.getInstance().getResourceFolder(file);
        mEditedConfig = resFolder.getConfiguration();

        mDisableUpdates++; // we do not want to trigger onXXXChange when setting
                           // new values in the widgets.

        try {
            // only attempt to do anything if the SDK and targets are loaded.
            LoadStatus sdkStatus = AdtPlugin.getDefault().getSdkLoadStatus();
            if (sdkStatus == LoadStatus.LOADED) {
                LoadStatus targetStatus = Sdk.getCurrent().checkAndLoadTargetData(mProjectTarget,
                        null /*project*/);

                if (targetStatus == LoadStatus.LOADED) {

                    // update the current config selection to make sure it's
                    // compatible with the new file
                    adaptConfigSelection(true /*needBestMatch*/);

                    // compute the final current config
                    computeCurrentConfig();

                    // update the string showing the config value
                    updateConfigDisplay(mEditedConfig);
                }
            }
        } finally {
            mDisableUpdates--;
        }
    }

    /**
     * Updates the UI with a new file that was opened in response to a config change.
     * @param file the file being opened.
     *
     * @see #replaceFile(IFile)
     */
    public void changeFileOnNewConfig(IFile file) {
        mEditedFile = file;
        IProject iProject = mEditedFile.getProject();
        mResources = ResourceManager.getInstance().getProjectResources(iProject);

        ResourceFolder resFolder = ResourceManager.getInstance().getResourceFolder(file);
        mEditedConfig = resFolder.getConfiguration();

        // All that's needed is to update the string showing the config value
        // (since the config combo were chosen by the user).
        updateConfigDisplay(mEditedConfig);
    }

    /**
     * Responds to the event that the basic SDK information finished loading.
     * @param target the possibly new target object associated with the file being edited (in case
     * the SDK path was changed).
     */
    public void onSdkLoaded(IAndroidTarget target) {
        // a change to the SDK means that we need to check for new/removed devices.
        mSdkChanged = true;

        // store the new target.
        mProjectTarget = target;

        mDisableUpdates++; // we do not want to trigger onXXXChange when setting
                           // new values in the widgets.
        try {
            // this is going to be followed by a call to onTargetLoaded.
            // So we can only care about the layout devices in this case.
            initDevices();
            initTargets();
        } finally {
            mDisableUpdates--;
        }
    }

    /**
     * Answers to the XML model being loaded, either the first time or when the Target/SDK changes.
     * <p>This initializes the UI, either with the first compatible configuration found,
     * or attempts to restore a configuration if one is found to have been saved in the file
     * persistent storage.
     * <p>If the SDK or target are not loaded, nothing will happened (but the method must be called
     * back when those are loaded).
     * <p>The method automatically handles being called the first time after editor creation, or
     * being called after during SDK/Target changes (as long as {@link #onSdkLoaded(IAndroidTarget)}
     * is properly called).
     *
     * @see #storeState()
     * @see #onSdkLoaded(IAndroidTarget)
     */
    public AndroidTargetData onXmlModelLoaded() {
        AndroidTargetData targetData = null;

        // only attempt to do anything if the SDK and targets are loaded.
        LoadStatus sdkStatus = AdtPlugin.getDefault().getSdkLoadStatus();
        if (sdkStatus == LoadStatus.LOADED) {
            mDisableUpdates++; // we do not want to trigger onXXXChange when setting

            try {
                // init the devices if needed (new SDK or first time going through here)
                if (mSdkChanged || mFirstXmlModelChange) {
                    initDevices();
                    initTargets();
                }

                IProject iProject = mEditedFile.getProject();

                Sdk currentSdk = Sdk.getCurrent();
                if (currentSdk != null) {
                    mProjectTarget = currentSdk.getTarget(iProject);
                }

                LoadStatus targetStatus = LoadStatus.FAILED;
                if (mProjectTarget != null) {
                    targetStatus = Sdk.getCurrent().checkAndLoadTargetData(mProjectTarget, null);
                    initTargets();
                }

                if (targetStatus == LoadStatus.LOADED) {
                    if (mResources == null) {
                        mResources = ResourceManager.getInstance().getProjectResources(iProject);
                    }
                    if (mEditedConfig == null) {
                        ResourceFolder resFolder = mResources.getResourceFolder(
                                (IFolder) mEditedFile.getParent());
                        mEditedConfig = resFolder.getConfiguration();
                    }

                    targetData = Sdk.getCurrent().getTargetData(mProjectTarget);

                    // get the file stored state
                    boolean loadedConfigData = false;
                    try {
                        String data = mEditedFile.getPersistentProperty(NAME_CONFIG_STATE);

                        if (mInitialState != null) {
                            data = mInitialState;
                            mInitialState = null;
                        }
                        if (data != null) {
                            loadedConfigData = mState.setData(data);
                        }
                    } catch (CoreException e) {
                        // pass
                    }

                    // update the themes and locales.
                    updateThemes();
                    updateLocales();

                    // If the current state was loaded from the persistent storage, we update the
                    // UI with it and then try to adapt it (which will handle incompatible
                    // configuration).
                    // Otherwise, just look for the first compatible configuration.
                    if (loadedConfigData) {
                        // first make sure we have the config to adapt
                        selectDevice(mState.device);
                        fillConfigCombo(mState.configName);

                        adaptConfigSelection(false /*needBestMatch*/);

                        mDockCombo.select(DockMode.getIndex(mState.dock));
                        mNightCombo.select(NightMode.getIndex(mState.night));
                        mTargetCombo.select(mTargetList.indexOf(mState.target));

                        targetData = Sdk.getCurrent().getTargetData(mState.target);
                    } else {
                        findAndSetCompatibleConfig(false /*favorCurrentConfig*/);
                        // We don't want the -first- combobox item, we want the
                        // default one which is sometimes a different index
                        //mTargetCombo.select(0);
                    }

                    // update the string showing the config value
                    updateConfigDisplay(mEditedConfig);

                    // compute the final current config
                    computeCurrentConfig();
                }
            } finally {
                mDisableUpdates--;
                mFirstXmlModelChange = false;
            }
        }

        return targetData;
    }

    private static class ConfigBundle {
        FolderConfiguration config;
        int localeIndex;
        int dockModeIndex;
        int nightModeIndex;

        ConfigBundle() {
            config = new FolderConfiguration();
            localeIndex = 0;
            dockModeIndex = 0;
            nightModeIndex = 0;
        }

        ConfigBundle(ConfigBundle bundle) {
            config = new FolderConfiguration();
            config.set(bundle.config);
            localeIndex = bundle.localeIndex;
            dockModeIndex = bundle.dockModeIndex;
            nightModeIndex = bundle.nightModeIndex;
        }
    }

    /**
     * Finds a device/config that can display {@link #mEditedConfig}.
     * <p/>Once found the device and config combos are set to the config.
     * <p/>If there is no compatible configuration, a custom one is created.
     * @param favorCurrentConfig if true, and no best match is found, don't change
     * the current config. This must only be true if the current config is compatible.
     */
    private void findAndSetCompatibleConfig(boolean favorCurrentConfig) {
        LayoutDevice anyDeviceMatch = null; // a compatible device/config/locale
        String anyConfigMatchName = null;
        ConfigBundle anyConfigBundle = null;

        LayoutDevice bestDeviceMatch = null; // an actual best match
        String bestConfigMatchName = null;
        ConfigBundle bestConfigBundle = null;

        FolderConfiguration testConfig = new FolderConfiguration();

        // get a locale that match the host locale roughly (may not be exact match on the region.)
        int localeHostMatch = getLocaleMatch();

        // build a list of combinations of non standard qualifiers to add to each device's
        // qualifier set when testing for a match.
        // These qualifiers are: locale, nightmode, car dock.
        List<ConfigBundle> addConfig = new ArrayList<ConfigBundle>();

        // If the edited file has locales, then we have to select a matching locale from
        // the list.
        // However, if it doesn't, we don't randomly take the first locale, we take one
        // matching the current host locale (making sure it actually exist in the project)
        int start, max;
        if (mEditedConfig.getLanguageQualifier() != null || localeHostMatch == -1) {
            // add all the locales
            start = 0;
            max = mLocaleList.size();
        } else {
            // only add the locale host match
            start = localeHostMatch;
            max = localeHostMatch + 1; // test is <
        }

        for (int i = start ; i < max ; i++) {
            ResourceQualifier[] l = mLocaleList.get(i);

            ConfigBundle bundle = new ConfigBundle();
            bundle.config.setLanguageQualifier((LanguageQualifier) l[LOCALE_LANG]);
            bundle.config.setRegionQualifier((RegionQualifier) l[LOCALE_REGION]);

            bundle.localeIndex = i;
            addConfig.add(bundle);
        }

        // add the dock mode to the bundle combinations.
        addDockModeToBundles(addConfig);

        // add the night mode to the bundle combinations.
        addNightModeToBundles(addConfig);

        mainloop: for (LayoutDevice device : mDeviceList) {
            for (DeviceConfig config : device.getConfigs()) {

                // loop on the list of qualifier adds
                for (ConfigBundle bundle : addConfig) {
                    // set the base config. This erase all data in testConfig.
                    testConfig.set(config.getConfig());

                    // add on top of it, the extra qualifiers
                    testConfig.add(bundle.config);

                    if (mEditedConfig.isMatchFor(testConfig)) {
                        // this is a basic match. record it in case we don't find a match
                        // where the edited file is a best config.
                        if (anyDeviceMatch == null) {
                            anyDeviceMatch = device;
                            anyConfigMatchName = config.getName();
                            anyConfigBundle = bundle;
                        }

                        if (isCurrentFileBestMatchFor(testConfig)) {
                            // this is what we want.
                            bestDeviceMatch = device;
                            bestConfigMatchName = config.getName();
                            bestConfigBundle = bundle;
                            break mainloop;
                        }
                    }
                }
            }
        }

        if (bestDeviceMatch == null) {
            if (favorCurrentConfig) {
                // quick check
                if (mEditedConfig.isMatchFor(mCurrentConfig) == false) {
                    AdtPlugin.log(IStatus.ERROR,
                            "favorCurrentConfig can only be true if the current config is compatible");
                }

                // just display the warning
                AdtPlugin.printErrorToConsole(mEditedFile.getProject(),
                        String.format(
                                "'%1$s' is not a best match for any device/locale combination.",
                                mEditedConfig.toDisplayString()),
                        String.format(
                                "Displaying it with '%1$s'",
                                mCurrentConfig.toDisplayString()));
            } else if (anyDeviceMatch != null) {
                // select the device anyway.
                selectDevice(mState.device = anyDeviceMatch);
                fillConfigCombo(anyConfigMatchName);
                mLocaleCombo.select(anyConfigBundle.localeIndex);
                mDockCombo.select(anyConfigBundle.dockModeIndex);
                mNightCombo.select(anyConfigBundle.nightModeIndex);

                // TODO: display a better warning!
                computeCurrentConfig();
                AdtPlugin.printErrorToConsole(mEditedFile.getProject(),
                        String.format(
                                "'%1$s' is not a best match for any device/locale combination.",
                                mEditedConfig.toDisplayString()),
                        String.format(
                                "Displaying it with '%1$s' which is compatible, but will actually be displayed with another more specific version of the layout.",
                                mCurrentConfig.toDisplayString()));

            } else {
                // TODO: there is no device/config able to display the layout, create one.
                // For the base config values, we'll take the first device and config,
                // and replace whatever qualifier required by the layout file.
            }
        } else {
            selectDevice(mState.device = bestDeviceMatch);
            fillConfigCombo(bestConfigMatchName);
            mLocaleCombo.select(bestConfigBundle.localeIndex);
            mDockCombo.select(bestConfigBundle.dockModeIndex);
            mNightCombo.select(bestConfigBundle.nightModeIndex);
        }
    }

    private void addDockModeToBundles(List<ConfigBundle> addConfig) {
        ArrayList<ConfigBundle> list = new ArrayList<ConfigBundle>();

        // loop on each item and for each, add all variations of the dock modes
        for (ConfigBundle bundle : addConfig) {
            int index = 0;
            for (DockMode mode : DockMode.values()) {
                ConfigBundle b = new ConfigBundle(bundle);
                b.config.setDockModeQualifier(new DockModeQualifier(mode));
                b.dockModeIndex = index++;
                list.add(b);
            }
        }

        addConfig.clear();
        addConfig.addAll(list);
    }

    private void addNightModeToBundles(List<ConfigBundle> addConfig) {
        ArrayList<ConfigBundle> list = new ArrayList<ConfigBundle>();

        // loop on each item and for each, add all variations of the night modes
        for (ConfigBundle bundle : addConfig) {
            int index = 0;
            for (NightMode mode : NightMode.values()) {
                ConfigBundle b = new ConfigBundle(bundle);
                b.config.setNightModeQualifier(new NightModeQualifier(mode));
                b.nightModeIndex = index++;
                list.add(b);
            }
        }

        addConfig.clear();
        addConfig.addAll(list);
    }

    /**
     * Adapts the current device/config selection so that it's compatible with
     * {@link #mEditedConfig}.
     * <p/>If the current selection is compatible, nothing is changed.
     * <p/>If it's not compatible, configs from the current devices are tested.
     * <p/>If none are compatible, it reverts to
     * {@link #findAndSetCompatibleConfig(FolderConfiguration)}
     */
    private void adaptConfigSelection(boolean needBestMatch) {
        // check the device config (ie sans locale)
        boolean needConfigChange = true; // if still true, we need to find another config.
        boolean currentConfigIsCompatible = false;
        int configIndex = mDeviceConfigCombo.getSelectionIndex();
        if (configIndex != -1) {
            String configName = mDeviceConfigCombo.getItem(configIndex);
            FolderConfiguration currentConfig = mState.device.getFolderConfigByName(configName);
            if (currentConfig != null && mEditedConfig.isMatchFor(currentConfig)) {
                currentConfigIsCompatible = true; // current config is compatible
                if (needBestMatch == false || isCurrentFileBestMatchFor(currentConfig)) {
                    needConfigChange = false;
                }
            }
        }

        if (needConfigChange) {
            // if the current config/locale isn't a correct match, then
            // look for another config/locale in the same device.
            FolderConfiguration testConfig = new FolderConfiguration();

            // first look in the current device.
            String matchName = null;
            int localeIndex = -1;
            mainloop: for (DeviceConfig config : mState.device.getConfigs()) {
                testConfig.set(config.getConfig());

                // loop on the locales.
                for (int i = 0 ; i < mLocaleList.size() ; i++) {
                    ResourceQualifier[] locale = mLocaleList.get(i);

                    // update the test config with the locale qualifiers
                    testConfig.setLanguageQualifier((LanguageQualifier)locale[LOCALE_LANG]);
                    testConfig.setRegionQualifier((RegionQualifier)locale[LOCALE_REGION]);

                    if (mEditedConfig.isMatchFor(testConfig) &&
                            isCurrentFileBestMatchFor(testConfig)) {
                        matchName = config.getName();
                        localeIndex = i;
                        break mainloop;
                    }
                }
            }

            if (matchName != null) {
                selectConfig(matchName);
                mLocaleCombo.select(localeIndex);
            } else {
                // no match in current device with any config/locale
                // attempt to find another device that can display this particular config.
                findAndSetCompatibleConfig(currentConfigIsCompatible);
            }
        }
    }

    /**
     * Finds a locale matching the config from a file.
     * @param language the language qualifier or null if none is set.
     * @param region the region qualifier or null if none is set.
     */
    private void setLocaleCombo(ResourceQualifier language, ResourceQualifier region) {
        // find the locale match. Since the locale list is based on the content of the
        // project resources there must be an exact match.
        // The only trick is that the region could be null in the fileConfig but in our
        // list of locales, this is represented as a RegionQualifier with value of
        // FAKE_LOCALE_VALUE.
        final int count = mLocaleList.size();
        for (int i = 0 ; i < count ; i++) {
            ResourceQualifier[] locale = mLocaleList.get(i);

            // the language qualifier in the locale list is never null.
            if (locale[LOCALE_LANG].equals(language)) {
                // region comparison is more complex, as the region could be null.
                if (region == null) {
                    if (RegionQualifier.FAKE_REGION_VALUE.equals(
                            ((RegionQualifier)locale[LOCALE_REGION]).getValue())) {
                        // match!
                        mLocaleCombo.select(i);
                        break;
                    }
                } else if (region.equals(locale[LOCALE_REGION])) {
                    // match!
                    mLocaleCombo.select(i);
                    break;
                }
            }
        }
    }

    private void updateConfigDisplay(FolderConfiguration fileConfig) {
        String current = fileConfig.toDisplayString();
        String layoutLabel = current != null ? current : "(Default)";
        mCurrentLayoutLabel.setText(layoutLabel);
        mCurrentLayoutLabel.setToolTipText(layoutLabel);
    }

    private void saveState() {
        if (mDisableUpdates == 0) {
            int index = mDeviceConfigCombo.getSelectionIndex();
            if (index != -1) {
                mState.configName = mDeviceConfigCombo.getItem(index);
            } else {
                mState.configName = null;
            }

            // since the locales are relative to the project, only keeping the index is enough
            index = mLocaleCombo.getSelectionIndex();
            if (index != -1) {
                mState.locale = mLocaleList.get(index);
            } else {
                mState.locale = null;
            }

            index = mThemeCombo.getSelectionIndex();
            if (index != -1) {
                mState.theme = mThemeCombo.getItem(index);
            }

            index = mDockCombo.getSelectionIndex();
            if (index != -1) {
                mState.dock = DockMode.getByIndex(index);
            }

            index = mNightCombo.getSelectionIndex();
            if (index != -1) {
                mState.night = NightMode.getByIndex(index);
            }

            index = mTargetCombo.getSelectionIndex();
            if (index != -1) {
                mState.target = mTargetList.get(index);
            }
        }
    }

    /**
     * Stores the current config selection into the edited file.
     */
    public void storeState() {
        try {
            mEditedFile.setPersistentProperty(NAME_CONFIG_STATE, mState.getData());
        } catch (CoreException e) {
            // pass
        }
    }

    /**
     * Updates the locale combo.
     * This must be called from the UI thread.
     */
    public void updateLocales() {
        if (mListener == null) {
            return; // can't do anything w/o it.
        }

        mDisableUpdates++;

        try {
            // Reset the combo
            mLocaleCombo.removeAll();
            mLocaleList.clear();

            SortedSet<String> languages = null;
            boolean hasLocale = false;

            // get the languages from the project.
            ProjectResources project = mListener.getProjectResources();

            // in cases where the opened file is not linked to a project, this could be null.
            if (project != null) {
                // now get the languages from the project.
                languages = project.getLanguages();

                for (String language : languages) {
                    hasLocale = true;

                    LanguageQualifier langQual = new LanguageQualifier(language);

                    // find the matching regions and add them
                    SortedSet<String> regions = project.getRegions(language);
                    for (String region : regions) {
                        mLocaleCombo.add(
                                String.format("%1$s / %2$s", language, region)); //$NON-NLS-1$
                        RegionQualifier regionQual = new RegionQualifier(region);
                        mLocaleList.add(new ResourceQualifier[] { langQual, regionQual });
                    }

                    // now the entry for the other regions the language alone
                    if (regions.size() > 0) {
                        mLocaleCombo.add(String.format("%1$s / Other", language)); //$NON-NLS-1$
                    } else {
                        mLocaleCombo.add(String.format("%1$s / Any", language)); //$NON-NLS-1$
                    }
                    // create a region qualifier that will never be matched by qualified resources.
                    mLocaleList.add(new ResourceQualifier[] {
                            langQual,
                            new RegionQualifier(RegionQualifier.FAKE_REGION_VALUE)
                    });
                }
            }

            // add a locale not present in the project resources. This will let the dev
            // tests his/her default values.
            if (hasLocale) {
                mLocaleCombo.add("Other");
            } else {
                mLocaleCombo.add("Any locale");
            }

            // create language/region qualifier that will never be matched by qualified resources.
            mLocaleList.add(new ResourceQualifier[] {
                    new LanguageQualifier(LanguageQualifier.FAKE_LANG_VALUE),
                    new RegionQualifier(RegionQualifier.FAKE_REGION_VALUE)
            });

            if (mState.locale != null) {
                // FIXME: this may fails if the layout was deleted (and was the last one to have
                // that local. (we have other problem in this case though)
                setLocaleCombo(mState.locale[LOCALE_LANG],
                        mState.locale[LOCALE_REGION]);
            } else {
                mLocaleCombo.select(0);
            }

            mThemeCombo.getParent().layout();
        } finally {
            mDisableUpdates--;
        }
    }

    private int getLocaleMatch() {
        Locale locale = Locale.getDefault();
        if (locale != null) {
            String currentLanguage = locale.getLanguage();
            String currentRegion = locale.getCountry();

            final int count = mLocaleList.size();
            for (int l = 0 ; l < count ; l++) {
                ResourceQualifier[] localeArray = mLocaleList.get(l);
                LanguageQualifier langQ = (LanguageQualifier)localeArray[LOCALE_LANG];
                RegionQualifier regionQ = (RegionQualifier)localeArray[LOCALE_REGION];

                // there's always a ##/Other or ##/Any (which is the same, the region
                // contains FAKE_REGION_VALUE). If we don't find a perfect region match
                // we take the fake region. Since it's last in the list, this makes the
                // test easy.
                if (langQ.getValue().equals(currentLanguage) &&
                        (regionQ.getValue().equals(currentRegion) ||
                         regionQ.getValue().equals(RegionQualifier.FAKE_REGION_VALUE))) {
                    return l;
                }
            }

            // if no locale match the current local locale, it's likely that it is
            // the default one which is the last one.
            return count - 1;
        }

        return -1;
    }

    /**
     * Updates the theme combo.
     * This must be called from the UI thread.
     */
    private void updateThemes() {
        if (mListener == null) {
            return; // can't do anything w/o it.
        }

        ProjectResources frameworkProject = mListener.getFrameworkResources(getRenderingTarget());

        mDisableUpdates++;

        try {
            // Reset the combo
            mThemeCombo.removeAll();
            mPlatformThemeCount = 0;

            ArrayList<String> themes = new ArrayList<String>();

            // get the themes, and languages from the Framework.
            if (frameworkProject != null) {
                // get the configured resources for the framework
                Map<String, Map<String, ResourceValue>> frameworResources =
                    frameworkProject.getConfiguredResources(getCurrentConfig());

                if (frameworResources != null) {
                    // get the styles.
                    Map<String, ResourceValue> styles = frameworResources.get(
                            ResourceType.STYLE.getName());


                    // collect the themes out of all the styles.
                    for (ResourceValue value : styles.values()) {
                        String name = value.getName();
                        if (name.startsWith("Theme.") || name.equals("Theme")) {
                            themes.add(value.getName());
                            mPlatformThemeCount++;
                        }
                    }

                    // sort them and add them to the combo
                    Collections.sort(themes);

                    for (String theme : themes) {
                        mThemeCombo.add(theme);
                    }

                    mPlatformThemeCount = themes.size();
                    themes.clear();
                }
            }

            // now get the themes and languages from the project.
            ProjectResources project = mListener.getProjectResources();
            // in cases where the opened file is not linked to a project, this could be null.
            if (project != null) {
                // get the configured resources for the project
                Map<String, Map<String, ResourceValue>> configuredProjectRes =
                    mListener.getConfiguredProjectResources();

                if (configuredProjectRes != null) {
                    // get the styles.
                    Map<String, ResourceValue> styleMap = configuredProjectRes.get(
                            ResourceType.STYLE.getName());

                    if (styleMap != null) {
                        // collect the themes out of all the styles, ie styles that extend,
                        // directly or indirectly a platform theme.
                        for (ResourceValue value : styleMap.values()) {
                            if (isTheme(value, styleMap)) {
                                themes.add(value.getName());
                            }
                        }

                        // sort them and add them the to the combo.
                        if (mPlatformThemeCount > 0 && themes.size() > 0) {
                            mThemeCombo.add(THEME_SEPARATOR);
                        }

                        Collections.sort(themes);

                        for (String theme : themes) {
                            mThemeCombo.add(theme);
                        }
                    }
                }
            }

            // try to reselect the previous theme.
            boolean needDefaultSelection = true;

            if (mState.theme != null) {
                final int count = mThemeCombo.getItemCount();
                for (int i = 0 ; i < count ; i++) {
                    if (mState.theme.equals(mThemeCombo.getItem(i))) {
                        mThemeCombo.select(i);
                        needDefaultSelection = false;
                        mThemeCombo.setEnabled(true);
                        break;
                    }
                }
            }

            if (needDefaultSelection) {
                if (mThemeCombo.getItemCount() > 0) {
                    mThemeCombo.select(0);
                    mThemeCombo.setEnabled(true);
                } else {
                    mThemeCombo.setEnabled(false);
                }
            }

            mThemeCombo.getParent().layout();
        } finally {
            mDisableUpdates--;
        }
    }

    // ---- getters for the config selection values ----

    public FolderConfiguration getEditedConfig() {
        return mEditedConfig;
    }

    public FolderConfiguration getCurrentConfig() {
        return mCurrentConfig;
    }

    public void getCurrentConfig(FolderConfiguration config) {
        config.set(mCurrentConfig);
    }

    /**
     * Returns the currently selected {@link Density}. This is guaranteed to be non null.
     */
    public Density getDensity() {
        if (mCurrentConfig != null) {
            PixelDensityQualifier qual = mCurrentConfig.getPixelDensityQualifier();
            if (qual != null) {
                // just a sanity check
                Density d = qual.getValue();
                if (d != Density.NODPI) {
                    return d;
                }
            }
        }

        // no config? return medium as the default density.
        return Density.MEDIUM;
    }

    /**
     * Returns the current device xdpi.
     */
    public float getXDpi() {
        if (mState.device != null) {
            float dpi = mState.device.getXDpi();
            if (Float.isNaN(dpi) == false) {
                return dpi;
            }
        }

        // get the pixel density as the density.
        return getDensity().getDpiValue();
    }

    /**
     * Returns the current device ydpi.
     */
    public float getYDpi() {
        if (mState.device != null) {
            float dpi = mState.device.getYDpi();
            if (Float.isNaN(dpi) == false) {
                return dpi;
            }
        }

        // get the pixel density as the density.
        return getDensity().getDpiValue();
    }

    public Rectangle getScreenBounds() {
        // get the orientation from the current device config
        ScreenOrientationQualifier qual = mCurrentConfig.getScreenOrientationQualifier();
        ScreenOrientation orientation = ScreenOrientation.PORTRAIT;
        if (qual != null) {
            orientation = qual.getValue();
        }

        // get the device screen dimension
        ScreenDimensionQualifier qual2 = mCurrentConfig.getScreenDimensionQualifier();
        int s1, s2;
        if (qual2 != null) {
            s1 = qual2.getValue1();
            s2 = qual2.getValue2();
        } else {
            s1 = 480;
            s2 = 320;
        }

        switch (orientation) {
            default:
            case PORTRAIT:
                return new Rectangle(0, 0, s2, s1);
            case LANDSCAPE:
                return new Rectangle(0, 0, s1, s2);
            case SQUARE:
                return new Rectangle(0, 0, s1, s1);
        }
    }


    /**
     * Returns the current theme, or null if the combo has no selection.
     *
     * @return the theme name, or null
     */
    public String getTheme() {
        int themeIndex = mThemeCombo.getSelectionIndex();
        if (themeIndex != -1) {
            return mThemeCombo.getItem(themeIndex);
        }

        return null;
    }

    /**
     * Returns the current device string, or null if the combo has no selection.
     *
     * @return the device name, or null
     */
    public String getDevice() {
        int deviceIndex = mDeviceCombo.getSelectionIndex();
        if (deviceIndex != -1) {
            return mDeviceCombo.getItem(deviceIndex);
        }

        return null;
    }

    /**
     * Returns whether the current theme selection is a project theme.
     * <p/>The returned value is meaningless if {@link #getTheme()} returns <code>null</code>.
     * @return true for project theme, false for framework theme
     */
    public boolean isProjectTheme() {
        return mThemeCombo.getSelectionIndex() >= mPlatformThemeCount;
    }

    public IAndroidTarget getRenderingTarget() {
        int index = mTargetCombo.getSelectionIndex();
        if (index >= 0) {
            return mTargetList.get(index);
        }

        return null;
    }

    /**
     * Loads the list of {@link IAndroidTarget} and inits the UI with it.
     */
    private void initTargets() {
        mTargetCombo.removeAll();
        mTargetList.clear();

        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk != null) {
            IAndroidTarget[] targets = currentSdk.getTargets();
            int match = -1;
            for (int i = 0 ; i < targets.length; i++) {
                // FIXME: support add-on rendering and check based on project minSdkVersion
                if (targets[i].isPlatform()) {
                    mTargetCombo.add(targets[i].getFullName());
                    mTargetList.add(targets[i]);

                    if (mRenderingTarget != null) {
                        // use equals because the rendering could be from a previous SDK, so
                        // it may not be the same instance.
                        if (mRenderingTarget.equals(targets[i])) {
                            match = mTargetList.indexOf(targets[i]);
                        }
                    } else if (mProjectTarget == targets[i]) {
                        match = mTargetList.indexOf(targets[i]);
                    }
                }
            }

            mTargetCombo.setEnabled(mTargetList.size() > 1);
            if (match == -1) {
                mTargetCombo.deselectAll();

                // the rendering target is the same as the project.
                mRenderingTarget = mProjectTarget;
            } else {
                mTargetCombo.select(match);

                // set the rendering target to the new object.
                mRenderingTarget = mTargetList.get(match);
            }
        }
    }

    /**
     * Loads the list of {@link LayoutDevice} and inits the UI with it.
     */
    private void initDevices() {
        mDeviceList = null;

        Sdk sdk = Sdk.getCurrent();
        if (sdk != null) {
            LayoutDeviceManager manager = sdk.getLayoutDeviceManager();
            mDeviceList = manager.getCombinedList();
        }


        // remove older devices if applicable
        mDeviceCombo.removeAll();
        mDeviceConfigCombo.removeAll();

        // fill with the devices
        if (mDeviceList != null) {
            for (LayoutDevice device : mDeviceList) {
                mDeviceCombo.add(device.getName());
            }
            mDeviceCombo.select(0);

            if (mDeviceList.size() > 0) {
                List<DeviceConfig> configs = mDeviceList.get(0).getConfigs();
                for (DeviceConfig config : configs) {
                    mDeviceConfigCombo.add(config.getName());
                }
                mDeviceConfigCombo.select(0);
                if (configs.size() == 1) {
                    mDeviceConfigCombo.setEnabled(false);
                }
            }
        }

        // add the custom item
        mDeviceCombo.add("Custom...");
    }

    /**
     * Selects a given {@link LayoutDevice} in the device combo, if it is found.
     * @param device the device to select
     * @return true if the device was found.
     */
    private boolean selectDevice(LayoutDevice device) {
        final int count = mDeviceList.size();
        for (int i = 0 ; i < count ; i++) {
            // since device comes from mDeviceList, we can use the == operator.
            if (device == mDeviceList.get(i)) {
                mDeviceCombo.select(i);
                return true;
            }
        }

        return false;
    }

    /**
     * Selects a config by name.
     * @param name the name of the config to select.
     */
    private void selectConfig(String name) {
        final int count = mDeviceConfigCombo.getItemCount();
        for (int i = 0 ; i < count ; i++) {
            String item = mDeviceConfigCombo.getItem(i);
            if (name.equals(item)) {
                mDeviceConfigCombo.select(i);
                return;
            }
        }
    }

    /**
     * Called when the selection of the device combo changes.
     * @param recomputeLayout
     */
    private void onDeviceChange(boolean recomputeLayout) {
        // because changing the content of a combo triggers a change event, respect the
        // mDisableUpdates flag
        if (mDisableUpdates > 0) {
            return;
        }

        String newConfigName = null;

        int deviceIndex = mDeviceCombo.getSelectionIndex();
        if (deviceIndex != -1) {
            // check if the user is asking for the custom item
            if (deviceIndex == mDeviceCombo.getItemCount() - 1) {
                onCustomDeviceConfig();
                return;
            }

            // get the previous config, so that we can look for a close match
            if (mState.device != null) {
                int index = mDeviceConfigCombo.getSelectionIndex();
                if (index != -1) {
                    FolderConfiguration oldConfig = mState.device.getFolderConfigByName(
                            mDeviceConfigCombo.getItem(index));

                    LayoutDevice newDevice = mDeviceList.get(deviceIndex);

                    newConfigName = getClosestMatch(oldConfig, newDevice.getConfigs());
                }
            }

            mState.device = mDeviceList.get(deviceIndex);
        } else {
            mState.device = null;
        }

        fillConfigCombo(newConfigName);

        computeCurrentConfig();

        if (recomputeLayout) {
            onDeviceConfigChange();
        }
    }

    /**
     * Handles a user request for the {@link ConfigManagerDialog}.
     */
    private void onCustomDeviceConfig() {
        ConfigManagerDialog dialog = new ConfigManagerDialog(getShell());
        dialog.open();

        // save the user devices
        Sdk.getCurrent().getLayoutDeviceManager().save();

        // Update the UI with no triggered event
        mDisableUpdates++;

        try {
            LayoutDevice oldCurrent = mState.device;

            // but first, update the device combo
            initDevices();

            // attempts to reselect the current device.
            if (selectDevice(oldCurrent)) {
                // current device still exists.
                // reselect the config
                selectConfig(mState.configName);

                // reset the UI as if it was just a replacement file, since we can keep
                // the current device (and possibly config).
                adaptConfigSelection(false /*needBestMatch*/);

            } else {
                // find a new device/config to match the current file.
                findAndSetCompatibleConfig(false /*favorCurrentConfig*/);
            }
        } finally {
            mDisableUpdates--;
        }

        // recompute the current config
        computeCurrentConfig();

        // force a redraw
        onDeviceChange(true /*recomputeLayout*/);
    }

    /**
     * Attempts to find a close config among a list
     * @param oldConfig the reference config.
     * @param configs the list of config to search through
     * @return the name of the closest config match, or possibly null if no configs are compatible
     * (this can only happen if the configs don't have a single qualifier that is the same).
     */
    private String getClosestMatch(FolderConfiguration oldConfig, List<DeviceConfig> configs) {

        // create 2 lists as we're going to go through one and put the candidates in the other.
        ArrayList<DeviceConfig> list1 = new ArrayList<DeviceConfig>();
        ArrayList<DeviceConfig> list2 = new ArrayList<DeviceConfig>();

        list1.addAll(configs);

        final int count = FolderConfiguration.getQualifierCount();
        for (int i = 0 ; i < count ; i++) {
            // compute the new candidate list by only taking configs that have
            // the same i-th qualifier as the old config
            for (DeviceConfig c : list1) {
                ResourceQualifier oldQualifier = oldConfig.getQualifier(i);

                FolderConfiguration folderConfig = c.getConfig();
                ResourceQualifier newQualifier = folderConfig.getQualifier(i);

                if (oldQualifier == null) {
                    if (newQualifier == null) {
                        list2.add(c);
                    }
                } else if (oldQualifier.equals(newQualifier)) {
                    list2.add(c);
                }
            }

            // at any moment if the new candidate list contains only one match, its name
            // is returned.
            if (list2.size() == 1) {
                return list2.get(0).getName();
            }

            // if the list is empty, then all the new configs failed. It is considered ok, and
            // we move to the next qualifier anyway. This way, if a qualifier is different for
            // all new configs it is simply ignored.
            if (list2.size() != 0) {
                // move the candidates back into list1.
                list1.clear();
                list1.addAll(list2);
                list2.clear();
            }
        }

        // the only way to reach this point is if there's an exact match.
        // (if there are more than one, then there's a duplicate config and it doesn't matter,
        // we take the first one).
        if (list1.size() > 0) {
            return list1.get(0).getName();
        }

        return null;
    }

    /**
     * fills the config combo with new values based on {@link #mState}.device.
     * @param refName an optional name. if set the selection will match this name (if found)
     */
    private void fillConfigCombo(String refName) {
        mDeviceConfigCombo.removeAll();

        if (mState.device != null) {
            int selectionIndex = 0;
            int i = 0;

            for (DeviceConfig config : mState.device.getConfigs()) {
                mDeviceConfigCombo.add(config.getName());

                if (config.getName().equals(refName)) {
                    selectionIndex = i;
                }
                i++;
            }

            mDeviceConfigCombo.select(selectionIndex);
            mDeviceConfigCombo.setEnabled(mState.device.getConfigs().size() > 1);
        }
    }

    /**
     * Called when the device config selection changes.
     */
    private void onDeviceConfigChange() {
        // because changing the content of a combo triggers a change event, respect the
        // mDisableUpdates flag
        if (mDisableUpdates > 0) {
            return;
        }

        if (computeCurrentConfig() && mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    /**
     * Call back for language combo selection
     */
    private void onLocaleChange() {
        // because mLocaleList triggers onLocaleChange at each modification, the filling
        // of the combo with data will trigger notifications, and we don't want that.
        if (mDisableUpdates > 0) {
            return;
        }

        if (computeCurrentConfig() &&  mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    private void onDockChange() {
        if (computeCurrentConfig() &&  mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    private void onDayChange() {
        if (computeCurrentConfig() &&  mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    /**
     * Call back for api level combo selection
     */
    private void onRenderingTargetChange() {
        // because mApiCombo triggers onApiLevelChange at each modification, the filling
        // of the combo with data will trigger notifications, and we don't want that.
        if (mDisableUpdates > 0) {
            return;
        }

        // tell the listener a new rendering target is being set. Need to do this before updating
        // mRenderingTarget.
        if (mListener != null && mRenderingTarget != null) {
            mListener.onRenderingTargetPreChange(mRenderingTarget);
        }

        int index = mTargetCombo.getSelectionIndex();
        mRenderingTarget = mTargetList.get(index);

        boolean computeOk = computeCurrentConfig();

        // force a theme update to reflect the new rendering target.
        // This must be done after computeCurrentConfig since it'll depend on the currentConfig
        // to figure out the theme list.
        updateThemes();

        // since the state is saved in computeCurrentConfig, we need to resave it since theme
        // change could have impacted it.
        saveState();

        if (mListener != null && mRenderingTarget != null) {
            mListener.onRenderingTargetPostChange(mRenderingTarget);
        }

        if (computeOk &&  mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    /**
     * Saves the current state and the current configuration
     *
     * @see #saveState()
     */
    private boolean computeCurrentConfig() {
        saveState();

        if (mState.device != null) {
            // get the device config from the device/config combos.
            int configIndex = mDeviceConfigCombo.getSelectionIndex();
            String name = mDeviceConfigCombo.getItem(configIndex);
            FolderConfiguration config = mState.device.getFolderConfigByName(name);

            // replace the config with the one from the device
            mCurrentConfig.set(config);

            // replace the locale qualifiers with the one coming from the locale combo
            int index = mLocaleCombo.getSelectionIndex();
            if (index != -1) {
                ResourceQualifier[] localeQualifiers = mLocaleList.get(index);

                mCurrentConfig.setLanguageQualifier(
                        (LanguageQualifier)localeQualifiers[LOCALE_LANG]);
                mCurrentConfig.setRegionQualifier(
                        (RegionQualifier)localeQualifiers[LOCALE_REGION]);
            }

            index = mDockCombo.getSelectionIndex();
            if (index == -1) {
                index = 0; // no selection = 0
            }
            mCurrentConfig.setDockModeQualifier(new DockModeQualifier(DockMode.getByIndex(index)));

            index = mNightCombo.getSelectionIndex();
            if (index == -1) {
                index = 0; // no selection = 0
            }
            mCurrentConfig.setNightModeQualifier(
                    new NightModeQualifier(NightMode.getByIndex(index)));

            // replace the API level by the selection of the combo
            index = mTargetCombo.getSelectionIndex();
            if (index == -1) {
                index = mTargetList.indexOf(mProjectTarget);
            }
            if (index != -1) {
                IAndroidTarget target = mTargetList.get(index);

                if (target != null) {
                    mCurrentConfig.setVersionQualifier(
                            new VersionQualifier(target.getVersion().getApiLevel()));
                }
            }

            // update the create button.
            checkCreateEnable();

            return true;
        }

        return false;
    }

    private void onThemeChange() {
        saveState();

        int themeIndex = mThemeCombo.getSelectionIndex();
        if (themeIndex != -1) {
            String theme = mThemeCombo.getItem(themeIndex);

            if (theme.equals(THEME_SEPARATOR)) {
                mThemeCombo.select(0);
            }

            if (mListener != null) {
                mListener.onThemeChange();
            }
        }
    }

    /**
     * Returns whether the given <var>style</var> is a theme.
     * This is done by making sure the parent is a theme.
     * @param value the style to check
     * @param styleMap the map of styles for the current project. Key is the style name.
     * @return True if the given <var>style</var> is a theme.
     */
    private boolean isTheme(ResourceValue value, Map<String, ResourceValue> styleMap) {
        if (value instanceof StyleResourceValue) {
            StyleResourceValue style = (StyleResourceValue)value;

            boolean frameworkStyle = false;
            String parentStyle = style.getParentStyle();
            if (parentStyle == null) {
                // if there is no specified parent style we look an implied one.
                // For instance 'Theme.light' is implied child style of 'Theme',
                // and 'Theme.light.fullscreen' is implied child style of 'Theme.light'
                String name = style.getName();
                int index = name.lastIndexOf('.');
                if (index != -1) {
                    parentStyle = name.substring(0, index);
                }
            } else {
                // remove the useless @ if it's there
                if (parentStyle.startsWith("@")) {
                    parentStyle = parentStyle.substring(1);
                }

                // check for framework identifier.
                if (parentStyle.startsWith("android:")) {
                    frameworkStyle = true;
                    parentStyle = parentStyle.substring("android:".length());
                }

                // at this point we could have the format style/<name>. we want only the name
                if (parentStyle.startsWith("style/")) {
                    parentStyle = parentStyle.substring("style/".length());
                }
            }

            if (parentStyle != null) {
                if (frameworkStyle) {
                    // if the parent is a framework style, it has to be 'Theme' or 'Theme.*'
                    return parentStyle.equals("Theme") || parentStyle.startsWith("Theme.");
                } else {
                    // if it's a project style, we check this is a theme.
                    value = styleMap.get(parentStyle);
                    if (value != null) {
                        return isTheme(value, styleMap);
                    }
                }
            }
        }

        return false;
    }

    private void checkCreateEnable() {
        mCreateButton.setEnabled(mEditedConfig.equals(mCurrentConfig) == false);
    }

    /**
     * Checks whether the current edited file is the best match for a given config.
     * <p/>
     * This tests against other versions of the same layout in the project.
     * <p/>
     * The given config must be compatible with the current edited file.
     * @param config the config to test.
     * @return true if the current edited file is the best match in the project for the
     * given config.
     */
    private boolean isCurrentFileBestMatchFor(FolderConfiguration config) {
        ResourceFile match = mResources.getMatchingFile(mEditedFile.getName(),
                ResourceFolderType.LAYOUT, config);

        if (match != null) {
            return match.getFile().equals(mEditedFile);
        } else {
            // if we stop here that means the current file is not even a match!
            AdtPlugin.log(IStatus.ERROR, "Current file is not a match for the given config.");
        }

        return false;
    }

    /**
     * Resets the configuration chooser to reflect the given file configuration. This is
     * intended to be used by the "Show Included In" functionality where the user has
     * picked a non-default configuration (such as a particular landscape layout) and the
     * configuration chooser must be switched to a landscape layout. This method will
     * trigger a model change.
     * <p>
     * This will NOT trigger a redraw event!
     * <p>
     * FIXME: We are currently setting the configuration file to be the configuration for
     * the "outer" (the including) file, rather than the inner file, which is the file the
     * user is actually editing. We need to refine this, possibly with a way for the user
     * to choose which configuration they are editing. And in particular, we should be
     * filtering the configuration chooser to only show options in the outer configuration
     * that are compatible with the inner included file.
     *
     * @param file the file to be configured
     */
    public void resetConfigFor(IFile file) {
        setFile(file);
        mEditedConfig = null;
        onXmlModelLoaded();
    }
}

