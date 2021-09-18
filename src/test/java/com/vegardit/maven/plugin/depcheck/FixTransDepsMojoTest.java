/*
 * Copyright 2013-2021 by Vegard IT GmbH (https://vegardit.com) and contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.plugin.depcheck;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.repository.RepositorySystem;
import org.junit.Test;

import com.vegardit.maven.util.AbstractMavenTest;

import net.sf.jstuff.core.collection.Sets;

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
public class FixTransDepsMojoTest extends AbstractMavenTest {

   @Test
   public void testConfig() throws Exception {
      final FixTransDepsMojo mojo = getMojo(getSession("empty-project-with-fix-trans-deps"), FixTransDepsMojo.MAVEN_GOAL);
      assertThat(mojo.backupPom).isTrue();
      assertThat(mojo.backupPomPrefix).isEqualTo("foo");
      assertThat(mojo.backupPomSuffix).isEqualTo("bar");
      assertThat(mojo.isSkip()).isTrue();
      assertThat(mojo.isVerbose()).isTrue();
   }

   @Test
   public void testUsedTransDeps() throws Exception {

      final MavenSession sess = getSession("project-with-used-transitive-dep");

      // workaround for mavenProject.getArtifacts() returning null when run as test case
      final RepositorySystem repositorySystem = mojoRule.lookup(RepositorySystem.class);
      sess.getCurrentProject().setArtifacts(Sets.newHashSet(repositorySystem.createArtifact("org.apache.commons", "commons-lang3", "3.12.0",
         "jar")));

      final FixTransDepsMojo mojo = getMojo(sess, FixTransDepsMojo.MAVEN_GOAL);

      mojo.backupPom = true;
      mojo.backupPomPrefix = "foo";
      mojo.backupPomSuffix = "bar";

      final File pom = new File(sess.getCurrentProject().getBasedir(), "pom.xml");
      final String pomOriginalContent = FileUtils.readFileToString(pom, Charset.defaultCharset());

      final File pomBackup = new File(sess.getCurrentProject().getBasedir(), mojo.backupPomPrefix + "pom.xml" + mojo.backupPomSuffix);
      assertThat(pomBackup).doesNotExist();

      assertThat(pomOriginalContent).doesNotContain("<artifactId>commons-lang3</artifactId>");

      mojo.execute();

      assertThat(FileUtils.readFileToString(pom, Charset.defaultCharset())).contains("<artifactId>commons-lang3</artifactId>");
      assertThat(pomBackup).exists();
      assertThat(FileUtils.readFileToString(pomBackup, Charset.defaultCharset())).isEqualTo(pomOriginalContent);
   }
}
