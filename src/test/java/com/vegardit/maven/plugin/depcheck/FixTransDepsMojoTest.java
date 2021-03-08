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

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
public class FixTransDepsMojoTest {

   @Rule
   public MojoRule rule = new MojoRule();

   @Rule
   public TestResources resources = new TestResources();

   private FixTransDepsMojo getMojo(final String testProject) throws Exception {
      final File projectCopy = resources.getBasedir(testProject);
      final FixTransDepsMojo mojo = (FixTransDepsMojo) rule.lookupMojo(FixTransDepsMojo.MAVEN_GOAL, new File(projectCopy, "pom.xml"));
      assertThat(mojo).isNotNull();
      return mojo;
   }

}
