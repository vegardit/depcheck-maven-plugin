/*
 * Copyright 2013-2021 by Vegard IT GmbH (https://vegardit.com) and contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.plugin.depcheck;

import static org.assertj.core.api.Assertions.*;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.repository.RepositorySystem;
import org.junit.Test;

import com.vegardit.maven.util.AbstractMavenTest;

import net.sf.jstuff.core.collection.CollectionUtils;

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
public class CheckDepsMojoTest extends AbstractMavenTest {

   @Test
   public void testConfig() throws Exception {
      final CheckDepsMojo mojo = getMojo(getSession("empty-project-with-check-deps"), CheckDepsMojo.MAVEN_GOAL);

      assertThat(mojo.checkForUnusedDependencies).isTrue();
      assertThat(mojo.checkForUsedTransitiveDependencies).isTrue();
      assertThat(mojo.failFast).isTrue();
      assertThat(mojo.failIfUnusedDependencies).isTrue();
      assertThat(mojo.failIfUsedTransitiveDependencies).isTrue();
      assertThat(mojo.isSkip()).isTrue();
      assertThat(mojo.isVerbose()).isTrue();
   }

   @Test
   public void testFindUnusedDep() throws Exception {
      final CheckDepsMojo mojo = getMojo(getSession("project-with-unused-dep"), CheckDepsMojo.MAVEN_GOAL);

      assertThat(mojo.checkForUnusedDependencies).isTrue();
      assertThat(mojo.failIfUnusedDependencies).isTrue();
      try {
         mojo.execute();
         failBecauseExceptionWasNotThrown(MojoExecutionException.class);
      } catch (final MojoExecutionException ex) {
         assertThat(ex.getMessage()).contains("unused direct dependency: org.apache.commons:commons-lang3:jar");
      }

      mojo.failIfUnusedDependencies = false;
      mojo.checkForUnusedDependencies = true;
      mojo.execute();

      mojo.failIfUnusedDependencies = true;
      mojo.checkForUnusedDependencies = false;
      mojo.execute();
   }

   @Test
   public void testUsedTransDeps() throws Exception {

      final MavenSession sess = getSession("project-with-used-transitive-dep");

      // workaround for mavenProject.getArtifacts() returning null when run as test case
      final RepositorySystem repositorySystem = mojoRule.lookup(RepositorySystem.class);
      sess.getCurrentProject().setArtifacts(CollectionUtils.newHashSet(repositorySystem.createArtifact("org.apache.commons", "commons-lang3", "3.12.0",
         "jar")));

      final CheckDepsMojo mojo = getMojo(sess, CheckDepsMojo.MAVEN_GOAL);
      mojo.checkForUnusedDependencies = false;
      mojo.checkForUsedTransitiveDependencies = true;
      mojo.failIfUsedTransitiveDependencies = true;
      try {
         mojo.execute();
         failBecauseExceptionWasNotThrown(MojoExecutionException.class);
      } catch (final MojoExecutionException ex) {
         assertThat(ex.getMessage()).contains("used transitive dependency: org.apache.commons:commons-lang3:jar");
      }
   }
}
