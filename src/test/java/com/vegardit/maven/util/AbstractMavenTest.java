/*
 * SPDX-FileCopyrightText: © Vegard IT GmbH (https://vegardit.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;

import io.takari.maven.testing.TestMavenRuntime5;
import io.takari.maven.testing.TestResources5;
import net.sf.jstuff.core.logging.Logger;

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
@ExtendWith(AbstractMavenTest.LoggingExtension.class)
public abstract class AbstractMavenTest {

   private static final Logger LOG = Logger.create();

   public static class LoggingExtension implements TestWatcher {

      @Override
      public void testSuccessful(final ExtensionContext context) {
         LOG.info("## " + context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName() + ": FINISHED");
      }

      @Override
      public void testFailed(final ExtensionContext context, final Throwable cause) {
         LOG.error("## " + context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName() + ": FAILED",
            cause);
      }
   }

   @RegisterExtension
   protected final TestResources5 testResources = new TestResources5();

   @RegisterExtension
   protected final TestMavenRuntime5 maven = new TestMavenRuntime5();

   protected <T extends Mojo> T getMojo(final MavenSession session, final String goal) throws Exception {
      final MojoExecution execution = maven.newMojoExecution(goal);
      @SuppressWarnings("unchecked")
      final T mojo = (T) maven.lookupConfiguredMojo(session, execution);
      assertThat(mojo).isNotNull();
      return mojo;
   }

   protected MavenSession getSession(final String testProjectName) throws Exception {
      final File testProjectCopy = testResources.getBasedir(testProjectName);
      final MavenProject project = maven.readMavenProject(testProjectCopy);
      final MavenSession session = maven.newMavenSession(project);

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
