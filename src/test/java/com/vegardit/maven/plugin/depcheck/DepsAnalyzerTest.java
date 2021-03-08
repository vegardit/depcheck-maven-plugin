/*
 * Copyright 2013-2021 by Vegard IT GmbH (https://vegardit.com) and contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.plugin.depcheck;

import static org.assertj.core.api.Assertions.*;

import java.io.File;

import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
public class DepsAnalyzerTest {

   @Rule
   public MojoRule rule = new MojoRule();

   @Rule
   public TestResources resources = new TestResources();

   private CheckDepsMojo getMojo(final String testProject) throws Exception {
      final File projectCopy = resources.getBasedir(testProject);
      final CheckDepsMojo mojo = (CheckDepsMojo) rule.lookupMojo(CheckDepsMojo.MAVEN_GOAL, new File(projectCopy, "pom.xml"));
      assertThat(mojo).isNotNull();
      return mojo;
   }

   @Test
   public void testIsAnonymousInnerClass() throws Exception {
      final DepsAnalyzer da = new DepsAnalyzer(getMojo("test-project"));

      assertThat(da.isAnonymousInnerClass(null)).isFalse();
      assertThat(da.isAnonymousInnerClass("")).isFalse();
      assertThat(da.isAnonymousInnerClass("java.lang.Object")).isFalse();
      assertThat(da.isAnonymousInnerClass("java.lang.Object.Foo")).isFalse();
      assertThat(da.isAnonymousInnerClass("java.lang.Object.1Foo")).isFalse();
      assertThat(da.isAnonymousInnerClass("java.lang.Object.1")).isTrue();
   }

   @Test
   public void testIsArtifactWithClasses() throws Exception {
      final DepsAnalyzer da = new DepsAnalyzer(getMojo("test-project"));

      assertThat(da.isArtifactWithClasses(new DummyArtifact().withType("ejb"))).isTrue();
      assertThat(da.isArtifactWithClasses(new DummyArtifact().withType("jar"))).isTrue();
      assertThat(da.isArtifactWithClasses(new DummyArtifact().withType("war"))).isTrue();
      assertThat(da.isArtifactWithClasses(new DummyArtifact().withType("ear"))).isFalse();
      assertThat(da.isArtifactWithClasses(new DummyArtifact().withType("pom"))).isFalse();
   }
}
