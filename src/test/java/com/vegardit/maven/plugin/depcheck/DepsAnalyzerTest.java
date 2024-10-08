/*
 * SPDX-FileCopyrightText: © Vegard IT GmbH (https://vegardit.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.plugin.depcheck;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vegardit.maven.util.AbstractMavenTest;
import com.vegardit.maven.util.DummyArtifact;

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
class DepsAnalyzerTest extends AbstractMavenTest {

   @Test
   void testIsAnonymousInnerClass() throws Exception {
      final var da = new DepsAnalyzer(getMojo(getSession("empty-project-with-check-deps"), CheckDepsMojo.MAVEN_GOAL));

      assertThat(da.isAnonymousInnerClass(null)).isFalse();
      assertThat(da.isAnonymousInnerClass("")).isFalse();
      assertThat(da.isAnonymousInnerClass("java.lang.Object")).isFalse();
      assertThat(da.isAnonymousInnerClass("java.lang.Object.Foo")).isFalse();
      assertThat(da.isAnonymousInnerClass("java.lang.Object.1Foo")).isFalse();
      assertThat(da.isAnonymousInnerClass("java.lang.Object.1")).isTrue();
   }

   @Test
   void testIsArtifactWithClasses() throws Exception {
      final var da = new DepsAnalyzer(getMojo(getSession("empty-project-with-check-deps"), CheckDepsMojo.MAVEN_GOAL));

      assertThat(da.isArtifactWithClasses(new DummyArtifact().withType("ejb"))).isTrue();
      assertThat(da.isArtifactWithClasses(new DummyArtifact().withType("jar"))).isTrue();
      assertThat(da.isArtifactWithClasses(new DummyArtifact().withType("war"))).isTrue();
      assertThat(da.isArtifactWithClasses(new DummyArtifact().withType("ear"))).isFalse();
      assertThat(da.isArtifactWithClasses(new DummyArtifact().withType("pom"))).isFalse();
   }
}
