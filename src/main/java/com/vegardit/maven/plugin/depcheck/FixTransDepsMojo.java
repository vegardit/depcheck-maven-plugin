/*
 * SPDX-FileCopyrightText: © Vegard IT GmbH (https://vegardit.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.plugin.depcheck;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.google.common.io.Files;
import com.vegardit.maven.plugin.depcheck.DepsAnalyzer.ScanResult;
import com.vegardit.maven.util.AbstractMojo;
import com.vegardit.maven.util.MavenUtils;

import net.sf.jstuff.core.Strings;

/**
 * This Maven goal performs a byte code analysis of the project's class files to identify directly used
 * transitive dependencies and declares them as direct dependencies in the current project's POM file.
 *
 * <p>
 * <b>Usage:</b> <code>mvn com.vegardit.maven:depcheck-maven-plugin:fix-trans-deps</code>
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

   /**
    * Specifies if a backup of the pom.xml should be created first.
    */
   @Parameter(property = MAVEN_GOAL + ".backupPom", defaultValue = "true")
   boolean backupPom;

   /**
    * Prefix for the backup file name.
    */
   @Parameter(property = MAVEN_GOAL + ".backupPomPrefix", defaultValue = "")
   String backupPomPrefix;

   /**
    * Suffix for the backup file name.
    */
   @Parameter(property = MAVEN_GOAL + ".backupPomSuffix", defaultValue = ".bck")
   String backupPomSuffix;

   private void backupPom(final File pomFile) throws MojoExecutionException {
      final String backupPomFileName = (backupPomPrefix == null ? "" : backupPomPrefix) + pomFile.getName() + (backupPomSuffix == null ? ""
            : backupPomSuffix);
      final var backupPom = new File(pomFile.getParentFile(), backupPomFileName);
      log.debug("Creating backup of " + pomFile + "...");
      try {
         Files.copy(pomFile, backupPom);
      } catch (final Exception ex) {
         throw new MojoExecutionException("Failed to backup POM: " + ex.getMessage(), ex);
      }
   }

   @Override
   protected void executeAfterLastProject() throws MojoExecutionException, MojoFailureException {
      try {
         fixPOMs(REACTOR_RESULTS);
      } finally {
         REACTOR_RESULTS.clear();
      }
   }

   @Override
   protected void executeOnEachProject() throws MojoExecutionException {
      final DepsAnalyzer.ScanResult result = new DepsAnalyzer(this).scan(false, true);
      REACTOR_RESULTS.add(result);
   }

   private boolean isMissingDependenciesTag(final String pomContent) {
      return Pattern.matches("<\\/\\s*dependencies\\s*\\>", pomContent);
   }

   private void fixPOM(final DepsAnalyzer.ScanResult result) throws MojoExecutionException {
      if (!result.hasDirectlyUsedTransitiveDependencies()) {
         log.debug("No direct usage of transitive dependencies found.");
         return;
      }

      final File pomFile = result.project.getFile();

      if (backupPom) {
         backupPom(pomFile);
      }

      /*
       * read pom content as string
       */
      final String pomContent;
      try {
         pomContent = FileUtils.readFileToString(pomFile, Charset.defaultCharset());
      } catch (final IOException ex) {
         throw new MojoExecutionException("Reading POM " + pomFile + " failed: " + ex.getMessage(), ex);
      }

      /*
       * determine indention
       */
      final String indention1 = getIndention(pomContent);
      final String indention2 = indention1 + indention1;
      final String indention3 = indention2 + indention1;

      /*
       * locate insert position
       */
      boolean insertDependenciesTag = isMissingDependenciesTag(pomContent);
      int insertBefore = -1;
      if (!insertDependenciesTag) {
         final Matcher matcher = Pattern.compile("[ \\t]*\\<\\/\\s*dependencies\\s*\\>").matcher(pomContent);
         if (matcher.find()) {
            do {
               insertDependenciesTag = false;
               insertBefore = matcher.start();
            }
            while (matcher.find());
         }
      }
      if (insertBefore == -1) {
         final Matcher matcher = Pattern.compile("[ \\t]*\\<\\/\\s*project\\s*\\>").matcher(pomContent);
         if (matcher.find()) {
            do {
               insertBefore = matcher.start();
            }
            while (matcher.find());
         }
      }
      if (insertBefore == -1)
         throw new MojoExecutionException("Cannot find </dependencies> nor </project> tag in pom file: " + pomFile);

      /*
       * determine new line character used in pom file
       */
      final String newline = pomContent.contains(Strings.CR_LF) ? Strings.CR_LF : "" + Strings.LF;

      /*
       * construct new pom content
       */
      final var pomContentNew = new StringBuilder() //
         .append(pomContent.substring(0, insertBefore)).append(newline);

      if (insertDependenciesTag) {
         pomContentNew.append(indention1).append("<dependencies>").append(newline);
      }

      log.info("  For " + MavenUtils.toString(result.project) + ":");
      for (final Artifact transArt : result.getUsedTransitiveDependencies()) {

         pomContentNew.append(indention2).append("<dependency>").append(newline) //
            .append(indention3).append("<groupId>").append(transArt.getGroupId()).append("</groupId>").append(newline) //
            .append(indention3).append("<artifactId>").append(transArt.getArtifactId()).append("</artifactId>").append(newline) //
            .append(indention3).append("<version>").append(transArt.getVersion()).append("</version>").append(newline); //

         String scope = transArt.getScope();

         if (Strings.isNotEmpty(transArt.getType()) && !"jar".equals(transArt.getType())) {
            pomContentNew.append(indention3).append("<type>").append(transArt.getType()).append("</type>").append(newline);

            // adjust the scope if necessary
            if ("ejb".equalsIgnoreCase(transArt.getType())) {
               scope = "provided";
            }
         }

         if (Strings.isNotEmpty(scope)) {
            pomContentNew.append(indention3).append("<scope>").append(scope).append("</scope>").append(newline);
         }

         pomContentNew.append(indention2).append("</dependency>").append(newline);

         log.info("  |-> added dependency: " + transArt);
      }

      if (insertDependenciesTag) {
         pomContentNew.append(indention1).append("</dependencies>").append(newline);
      }

      pomContentNew.append(pomContent.substring(insertBefore));

      try {
         FileUtils.write(pomFile, pomContentNew, Charset.defaultCharset());
      } catch (final Exception ex) {
         throw new MojoExecutionException("Writing POM failed: " + ex.getMessage(), ex);
      }
   }

   private void fixPOMs(final List<DepsAnalyzer.ScanResult> results) throws MojoExecutionException {
      if (results.stream().noneMatch(ScanResult::hasDirectlyUsedTransitiveDependencies) //
      ) {
         log.info(DIVIDER);
         log.info("No direct usage of transitive dependencies found.");
         return;
      }

      log.info(DIVIDER);
      log.info("The following transitive dependencies were added as direct dependencies to the respective maven projects:");
      log.info("");

      for (final DepsAnalyzer.ScanResult r : results) {
         fixPOM(r);
      }
   }

   private String getIndention(final String pomContent) {
      int indention = 4;
      final Matcher matcher = Pattern.compile("([ \\t]*)<\\s*modelVersion\\s*>").matcher(pomContent);
      if (matcher.find()) {
         indention = matcher.group(1).length();
         if (indention < 1) {
            indention = 4;
         }
      }
      return Strings.repeat(" ", 1 * indention);
   }
}
