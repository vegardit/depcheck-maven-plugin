/*
 * Copyright 2013-2021 by Vegard IT GmbH (https://vegardit.com) and contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.plugin.depcheck;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.vegardit.maven.plugin.depcheck.DepsAnalyzer.ScanResult;
import com.vegardit.maven.util.AbstractMojo;
import com.vegardit.maven.util.MavenUtils;

import net.sf.jstuff.core.Strings;
import net.sf.jstuff.xml.DOMFile;

/**
 * This Maven goal performs a byte code analysis of the project's class files to identify directly used
 * transitive dependencies and declares them as direct dependencies in the current project's POM file.
 *
 * <p>
 * <b>Usage:</b> <code>mvn de.sebthom.maven:depcheck-maven-plugin:fix-trans-deps</code>
 * </p>
 *
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
@Mojo( //
   name = FixTransDepsMojo.MAVEN_GOAL, //
   defaultPhase = LifecyclePhase.NONE, //
   requiresDependencyCollection = ResolutionScope.COMPILE, //
   threadSafe = true //
)
public final class FixTransDepsMojo extends AbstractMojo {

   public static final String MAVEN_GOAL = "fix-trans-deps";

   private static final List<DepsAnalyzer.ScanResult> REACTOR_RESULTS = new CopyOnWriteArrayList<>();

   @Override
   protected void executeAfterLastProject() throws MojoExecutionException, MojoFailureException {
      fixPOMs(REACTOR_RESULTS);
      REACTOR_RESULTS.clear();
   }

   @Override
   protected void executeOnEachProject() throws MojoExecutionException {
      final DepsAnalyzer.ScanResult result = new DepsAnalyzer(this).scan(false, true);
      REACTOR_RESULTS.add(result);
   }

   private void fixPOMs(final List<DepsAnalyzer.ScanResult> results) throws MojoExecutionException {
      if (!results.stream().anyMatch(ScanResult::hasDirectlyUsedTransitiveDependencies) //
      ) {
         log.info(DIVIDER);
         log.info("No direct usage of transitive dependencies found.");
         return;
      }

      for (final DepsAnalyzer.ScanResult r : results) {
         fixPOM(r);
      }
   }

   private void fixPOM(final DepsAnalyzer.ScanResult result) throws MojoExecutionException {
      if (!result.hasDirectlyUsedTransitiveDependencies()) {
         log.info("No direct usage of transitive dependencies found.");
         return;
      }

      final File pomFile = result.project.getFile();
      final DOMFile xmlPom;
      try {
         xmlPom = new DOMFile(pomFile);
      } catch (final IOException ex) {
         throw new MojoExecutionException("Reading POM " + pomFile + " failed: " + ex.getMessage(), ex);
      }

      if (result.hasDirectlyUsedTransitiveDependencies()) {
         log.info(DIVIDER);
         log.info("The following transitive dependencies were added as direct dependencies to the respective maven projects:\n");

         final Node xmlDep = xmlPom.findNode("/project/dependencies");

         log.info("For " + MavenUtils.toString(result.project) + ":");
         for (final Artifact transArt : result.getUsedTransitiveDependencies()) {
            final Element elemDep = xmlPom.createElement(xmlDep, "dependency");

            final Element elemGroupId = xmlPom.createElement(elemDep, "groupId");
            elemGroupId.setTextContent(transArt.getGroupId());

            final Element elemArtifactId = xmlPom.createElement(elemDep, "artifactId");
            elemArtifactId.setTextContent(transArt.getArtifactId());

            final Element elemVersion = xmlPom.createElement(elemDep, "version");
            elemVersion.setTextContent(transArt.getVersion());

            String scope = transArt.getScope();

            if (Strings.isNotEmpty(transArt.getType())) {
               final Element elemType = xmlPom.createElement(elemDep, "type");
               elemType.setTextContent(transArt.getType());

               // adjust the scope if necessary
               if ("ejb".equalsIgnoreCase(transArt.getType())) {
                  scope = "provided";
               }
            }

            if (Strings.isNotEmpty(scope)) {
               final Element elemScope = xmlPom.createElement(elemDep, "scope");
               elemScope.setTextContent(scope);
            }

            log.info("|-> added dependency: " + transArt);
         }
      }

      try {
         //TODO xmlPom.save();
      } catch (final Exception ex) {
         throw new MojoExecutionException("Writing POM failed: " + ex.getMessage(), ex);
      }
   }
}
