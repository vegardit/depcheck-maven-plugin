/*
 * SPDX-FileCopyrightText: © Vegard IT GmbH (https://vegardit.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.plugin.depcheck;

import static net.sf.jstuff.core.Strings.NEW_LINE;

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.vegardit.maven.plugin.depcheck.DepsAnalyzer.ScanResult;
import com.vegardit.maven.util.AbstractMojo;
import com.vegardit.maven.util.MavenUtils;

/**
 * This Maven goal performs a byte code analysis of the project's class files to
 * to determine if unused direct dependencies are declared and if classes of
 * transitive dependencies are directly referenced.
 *
 * <p>
 * <b>Usage:</b> <code>mvn com.vegardit.maven:depcheck-maven-plugin:check-deps</code>
 * </p>
 *
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
@Mojo( //
   name = CheckDepsMojo.MAVEN_GOAL, //
   defaultPhase = LifecyclePhase.PREPARE_PACKAGE, //
   requiresDependencyCollection = ResolutionScope.COMPILE, //
   threadSafe = true //
)
public final class CheckDepsMojo extends AbstractMojo {

   public static final String MAVEN_GOAL = "check-deps";

   private static final List<DepsAnalyzer.ScanResult> REACTOR_RESULTS = new CopyOnWriteArrayList<>();

   /**
    * Checks for potentially unused direct dependencies.
    */
   @Parameter(property = MAVEN_GOAL + ".checkForUnusedDependencies", defaultValue = "true")
   boolean checkForUnusedDependencies;

   /**
    * Checks for classes used by this project's code that are provided by transitive
    * instead of direct dependencies.
    */
   @Parameter(property = MAVEN_GOAL + ".checkForUsedTransitiveDependencies", defaultValue = "true")
   boolean checkForUsedTransitiveDependencies;

   /**
    * Only relevant for multi-module projects. Specifies if the Maven build shall be aborted
    * when the first module with violations is found or if the build should fail after all
    * modules were scanned.
    */
   @Parameter(property = MAVEN_GOAL + ".failFast", defaultValue = "false")
   boolean failFast;

   /**
    * Specifies if the Maven build should abort in case the project declares potentially unused
    * Maven dependencies.
    */
   @Parameter(property = MAVEN_GOAL + ".failIfUnusedDependencies", defaultValue = "false")
   boolean failIfUnusedDependencies;

   /**
    * Specifies if the Maven build should abort in case the project directly uses classes
    * of transitive dependencies.
    */
   @Parameter(property = MAVEN_GOAL + ".failIfUsedTransitiveDependencies", defaultValue = "false")
   boolean failIfUsedTransitiveDependencies;

   private void assertNoViolations(final List<DepsAnalyzer.ScanResult> results) throws MojoExecutionException {
      final boolean hasUnusedDirectDeps = results.stream().anyMatch(ScanResult::hasUnusedDirectDependencies);
      final boolean hasUsedTransDeps = results.stream().anyMatch(ScanResult::hasDirectlyUsedTransitiveDependencies);
      if (!hasUnusedDirectDeps && !hasUsedTransDeps) {
         log.info(DIVIDER);
         if (checkForUnusedDependencies) {
            log.info("No unused direct dependencies found.");
         } else if (checkForUsedTransitiveDependencies) {
            log.info("No direct usage of transitive dependencies found.");
         }
         return;
      }

      final var sb = new StringBuilder();

      sb.append("The following violations have been detected:").append(NEW_LINE);
      for (final DepsAnalyzer.ScanResult result : results) {
         if (result.hasUnusedDirectDependencies() || result.hasDirectlyUsedTransitiveDependencies()) {
            sb.append(NEW_LINE).append("  For ").append(MavenUtils.toString(result.project)).append(NEW_LINE);
            for (final Artifact unusedDep : result.unusedDirectDependencies) {
               sb.append("  |-> unused direct dependency: ").append(unusedDep).append(NEW_LINE);
            }
            for (final Entry<Artifact, Set<String>> entry : result.usedClassesOfTransitiveDependencies.entrySet()) {
               sb.append("  |-> used transitive dependency: ").append(entry.getKey()) //
                  .append("   (using e.g. ").append(entry.getValue().iterator().next()).append(")").append(NEW_LINE);
            }
         }
      }

      if (hasUnusedDirectDeps && failIfUnusedDependencies || hasUsedTransDeps && failIfUsedTransitiveDependencies)
         throw new MojoExecutionException(NEW_LINE + sb.toString());

      log.warn(sb.toString());
   }

   @Override
   protected void executeAfterLastProject() throws MojoExecutionException {
      try {
         if (!failFast) {
            assertNoViolations(REACTOR_RESULTS);
         }
      } finally {
         REACTOR_RESULTS.clear();
      }
   }

   @Override
   protected void executeOnEachProject() throws MojoExecutionException {
      final DepsAnalyzer.ScanResult result = new DepsAnalyzer(this).scan(checkForUnusedDependencies, checkForUsedTransitiveDependencies);
      if (failFast) {
         assertNoViolations(Arrays.asList(result));
      } else {
         REACTOR_RESULTS.add(result);
      }
   }
}
