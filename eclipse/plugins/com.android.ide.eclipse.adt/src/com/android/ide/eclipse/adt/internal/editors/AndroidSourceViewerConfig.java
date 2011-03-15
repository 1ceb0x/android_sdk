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

package com.android.ide.eclipse.adt.internal.editors;


import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.formatter.IContentFormatter;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.wst.sse.core.text.IStructuredPartitions;
import org.eclipse.wst.xml.core.text.IXMLPartitions;
import org.eclipse.wst.xml.ui.StructuredTextViewerConfigurationXML;
import org.eclipse.wst.xml.ui.internal.contentassist.XMLContentAssistProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base Source Viewer Configuration for Android resources.
 */
@SuppressWarnings("restriction") // XMLContentAssistProcessor
public class AndroidSourceViewerConfig extends StructuredTextViewerConfigurationXML {

    /** Content Assist Processor to use for all handled partitions. */
    private IContentAssistProcessor mProcessor;

    public AndroidSourceViewerConfig(IContentAssistProcessor processor) {
        super();
        mProcessor = processor;
    }

    @Override
    public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
        return super.getContentAssistant(sourceViewer);
    }

    /**
     * Returns the content assist processors that will be used for content
     * assist in the given source viewer and for the given partition type.
     *
     * @param sourceViewer the source viewer to be configured by this
     *        configuration
     * @param partitionType the partition type for which the content assist
     *        processors are applicable
     * @return IContentAssistProcessors or null if should not be supported
     */
    @Override
    protected IContentAssistProcessor[] getContentAssistProcessors(
            ISourceViewer sourceViewer, String partitionType) {
        ArrayList<IContentAssistProcessor> processors = new ArrayList<IContentAssistProcessor>();
        if (partitionType == IStructuredPartitions.UNKNOWN_PARTITION ||
            partitionType == IStructuredPartitions.DEFAULT_PARTITION ||
            partitionType == IXMLPartitions.XML_DEFAULT) {
            processors.add(mProcessor);
        }

        IContentAssistProcessor[] others = super.getContentAssistProcessors(sourceViewer,
                partitionType);
        if (others != null && others.length > 0) {
            for (IContentAssistProcessor p : others) {
                // Builtin Eclipse WTP code completion assistant? If so,
                // wrap it with our own filter which hides some unwanted completions.
                if (p instanceof XMLContentAssistProcessor) {
                    processors.add(new FilteringContentAssistProcessor(p));
                } else {
                    processors.add(p);
                }
            }
        }

        if (processors.size() > 0) {
            return processors.toArray(new IContentAssistProcessor[processors.size()]);
        } else {
            return null;
        }
    }

    @Override
    public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
        // TODO text hover for android xml
        return super.getTextHover(sourceViewer, contentType);
    }

    @Override
    public IAutoEditStrategy[] getAutoEditStrategies(
            ISourceViewer sourceViewer, String contentType) {
        // TODO auto edit strategies for android xml
        return super.getAutoEditStrategies(sourceViewer, contentType);
    }

    @Override
    public IContentFormatter getContentFormatter(ISourceViewer sourceViewer) {
        // TODO content formatter for android xml
        return super.getContentFormatter(sourceViewer);
    }

    @Override
    protected Map<String, ?> getHyperlinkDetectorTargets(final ISourceViewer sourceViewer) {
        @SuppressWarnings("unchecked")
        Map<String, ?> targets = super.getHyperlinkDetectorTargets(sourceViewer);
        // If we want to look up more context in our HyperlinkDetector via the
        // getAdapter method, we should place an IAdaptable object into the map here.
        targets.put("com.android.ide.eclipse.xmlCode", null); //$NON-NLS-1$
        return targets;
    }

    /**
     * A delegating {@link IContentAssistProcessor} whose purpose is to filter out some
     * default Eclipse XML completions which are distracting in Android XML files
     */
    private class FilteringContentAssistProcessor implements IContentAssistProcessor {
        private IContentAssistProcessor mDelegate;

        public FilteringContentAssistProcessor(IContentAssistProcessor delegate) {
            super();
            mDelegate = delegate;
        }

        public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
            ICompletionProposal[] result = mDelegate.computeCompletionProposals(viewer, offset);
            if (result == null) {
                return null;
            }

            List<ICompletionProposal> proposals =
                new ArrayList<ICompletionProposal>(result.length);
            for (ICompletionProposal proposal : result) {
                String replacement = proposal.getDisplayString();
                if (replacement.charAt(0) == '"' &&
                        replacement.charAt(replacement.length() - 1) == '"') {
                    // Filter out attribute values. In Android XML files (where there is no DTD
                    // etc) the default Eclipse XML code completion simply provides the
                    // existing value as a completion. This is often misleading, since if you
                    // for example have a typo, completion will show your current (wrong)
                    // value as a valid completion.
                } else if (replacement.contains("Namespace")  //$NON-NLS-1$
                        || replacement.contains("Schema")) {  //$NON-NLS-1$
                    // Eclipse adds in a number of namespace and schema related completions which
                    // are not usually applicable in our files.
                } else {
                    proposals.add(proposal);
                }
            }

            if (proposals.size() == result.length) {
                return result;
            } else {
                return proposals.toArray(new ICompletionProposal[proposals.size()]);
            }
        }

        public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
            return mDelegate.computeContextInformation(viewer, offset);
        }

        public char[] getCompletionProposalAutoActivationCharacters() {
            return mDelegate.getCompletionProposalAutoActivationCharacters();
        }

        public char[] getContextInformationAutoActivationCharacters() {
            return mDelegate.getContextInformationAutoActivationCharacters();
        }

        public IContextInformationValidator getContextInformationValidator() {
            return mDelegate.getContextInformationValidator();
        }

        public String getErrorMessage() {
            return mDelegate.getErrorMessage();
        }
    }
}
