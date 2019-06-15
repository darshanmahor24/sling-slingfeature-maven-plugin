/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.feature.maven.mojos;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.io.IOUtils;
import org.apache.sling.feature.io.json.FeatureJSONWriter;
import org.apache.sling.feature.maven.FeatureConstants;
import org.apache.sling.feature.maven.ProjectHelper;

/**
 * Attach the feature as a project artifact.
 */
@Mojo(name = "attach-features",
      defaultPhase = LifecyclePhase.PACKAGE,
      requiresDependencyResolution = ResolutionScope.TEST,
      threadSafe = true
    )
public class AttachFeaturesMojo extends AbstractFeatureMojo {
    /**
     * Attach test features
     */
    @Parameter(name = "attachTestFeatures",
            defaultValue = "false")
    private boolean attachTestFeatures;

    /**
     * Create reference file
     */
    @Parameter(name = "createReferenceFile", defaultValue = "false")
    private boolean createReferenceFile;

    /**
     * Classifier for the reference file (if any)
     */
    @Parameter(name = "referenceFileClassifier")
    private String referenceFileClassifier;

    private void attach(final Feature feature,
            final String classifier)
    throws MojoExecutionException {
        // write the feature
        final File outputFile = new File(this.getTmpDir(), classifier == null ? "feature.json" : "feature-" + classifier + ".json");
        outputFile.getParentFile().mkdirs();

        try ( final Writer writer = new FileWriter(outputFile)) {
            FeatureJSONWriter.write(writer, feature);
        } catch (final IOException e) {
            throw new MojoExecutionException("Unable to write feature " + feature.getId().toMvnId() + " to " + outputFile, e);
        }

        // if this project is a feature, it's the main artifact
        if ( project.getPackaging().equals(FeatureConstants.PACKAGING_FEATURE)
             && classifier == null) {
            project.getArtifact().setFile(outputFile);
        } else {
            // otherwise attach it as an additional artifact
            projectHelper.attachArtifact(project, FeatureConstants.PACKAGING_FEATURE,
                    classifier, outputFile);
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        ProjectHelper.checkPreprocessorRun(this.project);
        final List<String> featureUrls = new ArrayList<>();
        this.attachClassifierFeatures(ProjectHelper.getFeatures(this.project).values(), featureUrls);

        if ( this.attachTestFeatures ) {
            this.attachClassifierFeatures(ProjectHelper.getTestFeatures(this.project).values(), featureUrls);
        }
        if (this.createReferenceFile) {
            this.createReferenceFile(featureUrls);
        }
    }

    private void createReferenceFile(final List<String> featureUrls) throws MojoExecutionException {
        if (featureUrls.isEmpty()) {
            getLog().warn(
                    "Create reference file is enabled but no features are attached. Skipping reference file generation.");
        } else {
            String fileName = "references";
            if (this.referenceFileClassifier != null) {
                fileName = fileName.concat("-").concat(this.referenceFileClassifier);
            }
            fileName = fileName.concat(IOUtils.EXTENSION_REF_FILE);
            final File outputFile = new File(this.getTmpDir(), fileName);
            outputFile.getParentFile().mkdirs();

            try (final Writer w = new FileWriter(outputFile)) {
                for (final String url : featureUrls) {
                    w.write(url);
                    w.write('\n');
                }
            } catch (final IOException e) {
                throw new MojoExecutionException("Unable to write feature reference file to " + outputFile, e);
            }
            projectHelper.attachArtifact(project, "ref", this.referenceFileClassifier, outputFile);
        }
    }

    /**
     * Attach all features
     * @throws MojoExecutionException
     */
    void attachClassifierFeatures(final Collection<Feature> features, final List<String> featureUrls)
            throws MojoExecutionException {
        for (final Feature f : features) {
            attach(f, f.getId().getClassifier());
            featureUrls.add(f.getId().toMvnUrl());
        }
    }
}
