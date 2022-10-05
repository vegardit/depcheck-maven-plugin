/*
 * SPDX-FileCopyrightText: © Vegard IT GmbH (https://vegardit.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.util;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;

import net.sf.jstuff.core.reflection.Fields;

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
public class DummyArtifact extends DefaultArtifact {

   public DummyArtifact() {
      super("dummy", "dummy", "1.0.0", "compile", "jar", null, new DefaultArtifactHandler());
   }

   public DummyArtifact withScope(final String scope) {
      setScope(scope);
      return this;
   }

   public DummyArtifact withType(final String type) {
      Fields.writeIgnoringFinal(this, "type", type);
      return this;
   }
}
