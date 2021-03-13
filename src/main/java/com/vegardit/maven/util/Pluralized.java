/*
 * Copyright 2013-2021 by Vegard IT GmbH (https://vegardit.com) and contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.util;

import net.sf.jstuff.core.Strings;

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
public abstract class Pluralized {

   public static String classes(final int count) {
      return count + " " + Strings.pluralize(count, "class", "classes");
   }

   public static String classes(final int count, final String prefix) {
      return count + " " + prefix + " " + Strings.pluralize(count, "class", "classes");
   }

   public static String dependencies(final int count) {
      return count + " " + Strings.pluralize(count, "dependency", "dependencies");
   }

   public static String dependencies(final int count, final String prefix) {
      return count + " " + prefix + " " + Strings.pluralize(count, "dependency", "dependencies");
   }
}
