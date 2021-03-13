/*
 * Copyright 2013-2021 by Vegard IT GmbH (https://vegardit.com) and contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.util;

import static org.assertj.core.api.Assertions.*;

import java.io.File;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.apache.maven.project.MavenProject;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import net.sf.jstuff.core.logging.Logger;

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
public abstract class AbstractMavenTest {

   private static final Logger LOG = Logger.create();

   @Rule
   public MojoRule mojoRule = new MojoRule();

   @Rule
   public TestRule watcher = new TestWatcher() {
      @Override
      protected void starting(final Description description) {
         LOG.info("## " + description.getTestClass().getSimpleName() + "." + description.getMethodName() + ": START");
      }

      @Override
      protected void finished(final Description description) {
         LOG.info("## " + description.getTestClass().getSimpleName() + "." + description.getMethodName() + ": FINISHED");
      }
   };

   @Rule
   public TestResources testResources = new TestResources();

   protected <T extends Mojo> T getMojo(final MavenSession session, final String goal) throws Exception {
      final MojoExecution execution = mojoRule.newMojoExecution(goal);
      @SuppressWarnings("unchecked")
      final T mojo = (T) mojoRule.lookupConfiguredMojo(session, execution);
      assertThat(mojo).isNotNull();
      return mojo;
   }

   protected MavenSession getSession(final String testProjectName) throws Exception {
      final File testProjectCopy = testResources.getBasedir(testProjectName);
      final MavenProject project = mojoRule.readMavenProject(testProjectCopy);
      final MavenSession session = mojoRule.newMavenSession(project);

      // to prevent NPE: https://stackoverflow.com/questions/42216442/maven-plugin-testing-harness-session-getlocalrepository-returns-null
      session.getRequest().setLocalRepository(createLocalArtifactRepository());
      return session;
   }

   private ArtifactRepository createLocalArtifactRepository() {
      return new MavenArtifactRepository("local", //
         new File("target/local-repo").toURI().toString(), //
         new DefaultRepositoryLayout(), //
         new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE), //
         new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE) //
      );
   }
}
