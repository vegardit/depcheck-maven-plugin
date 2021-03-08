/*
 * Copyright 2013-2021 by Vegard IT GmbH (https://vegardit.com) and contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.plugin.depcheck;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;

import net.sf.jstuff.core.reflection.Fields;

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
class DummyArtifact extends DefaultArtifact {

   DummyArtifact() {
      super("dummy", "dummy", "1.0.0", "compile", "jar", null, new DefaultArtifactHandler());
   }

   DummyArtifact withScope(final String scope) {
      setScope(scope);
      return this;
   }

   DummyArtifact withType(final String type) {
      Fields.writeIgnoringFinal(this, "type", type);
      return this;
   }
}
