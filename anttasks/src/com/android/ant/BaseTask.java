/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A base class for the ant task that contains logic for handling dependency files
 */
public abstract class BaseTask extends Task {

    private DependencyGraph mDependencies;
    private String mPreviousBuildType;
    private String mBuildType;

    public void setPreviousBuildType(String previousBuildType) {
        mPreviousBuildType = previousBuildType;
    }

    public void setBuildType(String buildType) {
        mBuildType = buildType;
    }

    protected abstract String getExecTaskName();

    private Set<String> mRestrictTouchedExtensionsTo;

    /**
     * Sets the value of the "restricttouchedextensionsto" attribute.
     * @param touchedExtensions the extensions to check to see if they have been modified.
     *        values should be separated by a colon (:). If left blank or not set, all extensions
     *        will be checked.
     */
    public void setRestrictTouchedExtensionsTo(String restrictTouchedExtensionsTo) {
        mRestrictTouchedExtensionsTo = new HashSet<String>();
        String[] extensions = restrictTouchedExtensionsTo.split(":");
        for (String s : extensions) {
            mRestrictTouchedExtensionsTo.add(s);
        }
    }

    /**
     * Set up the dependency graph by passing it the location of the ".d" file, and the new input
     * paths.
     * @param dependencyFile path to the dependency file to use
     * @param the new input paths for this new compilation.
     * @return true if the dependency graph was successfully initialized
     */
    protected boolean initDependencies(String dependencyFile, List<File> inputPaths) {
        if (mBuildType != null && mBuildType.equals(mPreviousBuildType) == false) {
            // we don't care about deps, we need to execute the task no matter what.
            return true;
        }

        File depFile = new File(dependencyFile);
        if (depFile.exists()) {
            mDependencies = new DependencyGraph(dependencyFile, inputPaths);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Wrapper check to see if we need to execute this task at all
     * @return true if the DependencyGraph reports that our prereqs or targets
     *         have changed since the last run
     */
    protected boolean dependenciesHaveChanged() {
        if (mBuildType != null && mBuildType.equals(mPreviousBuildType) == false) {
            String execName = getExecTaskName();
            if (execName == null) {
                System.out.println(
                        "Current build type is different than previous build: forced task run.");
            } else {
                System.out.println(
                        "Current build type is different than previous build: forced " +
                        execName + " run.");
            }
            return true;
        }

        assert mDependencies != null : "Dependencies have not been initialized";
        return mDependencies.dependenciesHaveChanged(mRestrictTouchedExtensionsTo,
                true /*printStatus*/);
    }

    protected void generateDependencyFile(String depFilePath,
            List<File> inputs, String outputFile) {
        File file = new File(depFilePath);

        try {
            PrintStream ps = new PrintStream(file);

            // write the output file.
            ps.print(outputFile);
            ps.println(" : \\");

            //write the input files
            int count = inputs.size();
            for (int i = 0 ; i < count ; i++) {
                File input = inputs.get(i);
                if (input.isDirectory()) {
                    writeContent(ps, input);
                } else {
                    ps.print(input.getAbsolutePath());
                    ps.println(" \\");
                }
            }

            ps.close();
        } catch (FileNotFoundException e) {
            new BuildException(e);
        }
    }

    private void writeContent(PrintStream ps, File input) {
        File[] files = input.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    writeContent(ps, f);
                } else {
                    ps.print(f.getAbsolutePath());
                    ps.println(" \\");
                }
            }
        }
    }
}
