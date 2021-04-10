/*
 * Copyright 2010-2021 by Sebastian Thomschke and contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.plugin.depcheck;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.objectweb.asm.Opcodes;

import com.vegardit.maven.util.AbstractMojo;
import com.vegardit.maven.util.MavenUtils;
import com.vegardit.maven.util.Pluralized;

import net.sf.jstuff.core.Strings;
import net.sf.jstuff.core.collection.Enumerations;
import net.sf.jstuff.core.collection.tuple.Tuple2;
import net.sf.jstuff.core.validation.Args;

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
public class DepsAnalyzer {

   public static class ScanResult {
      public final MavenProject project;
      public final Map<Artifact, Set<String>> usedClassesOfTransitiveDependencies = new HashMap<>();
      public final Set<Artifact> unusedDirectDependencies = new HashSet<>();

      public ScanResult(final MavenProject project) {
         this.project = project;
      }

      public Set<Artifact> getUsedTransitiveDependencies() {
         return usedClassesOfTransitiveDependencies.keySet();
      }

      public boolean hasDirectlyUsedTransitiveDependencies() {
         return !usedClassesOfTransitiveDependencies.isEmpty();
      }

      public boolean hasUnusedDirectDependencies() {
         return !unusedDirectDependencies.isEmpty();
      }
   }

   private final boolean isVerbose;
   private final Log log;
   private final AbstractMojo mojo;
   private final MavenProject project;

   public DepsAnalyzer(final AbstractMojo mojo) {
      Args.notNull("mojo", mojo);

      this.mojo = mojo;
      log = mojo.getLog();
      project = mojo.getProject();
      isVerbose = mojo.isVerbose();
   }

   /**
    * @return key = class name, value = declaring artifact
    */
   private Map<String, Artifact> getClassesDeclaredByTransitiveDependencies() throws MojoExecutionException {

      final Map<String, Artifact> classesDeclaredByTransitiveDependencies = new HashMap<>();
      for (final Artifact transDep : MavenUtils.withoutRuntimeAndTestScoped(mojo.getTransitiveDependencies())) {
         if (!isArtifactWithClasses(transDep)) {
            if (isVerbose) {
               log.info("Ignoring transitive dependency artifact: " + transDep);
            }
            continue;
         }

         if (isVerbose) {
            log.info("Resolving transitive dependency: " + transDep);
         }
         mojo.resolveArtifact(transDep);

         final File jarFile = transDep.getFile();
         if (jarFile == null) {
            continue;
         }

         try {
            if (isVerbose) {
               log.info("Collecting declared classes of transitive dependency: " + transDep);
            }
            final Set<String> classesDeclaredByArtifactr = scanArtifactForDeclaredClasses(transDep);
            for (final String declaredClass : classesDeclaredByArtifactr) {
               classesDeclaredByTransitiveDependencies.put(declaredClass, transDep);
            }
         } catch (final IOException ex) {
            throw new MojoExecutionException("Analyzing transitive dependency " + transDep + " failed with: " + ex.getMessage(), ex);
         }
      }
      return classesDeclaredByTransitiveDependencies;
   }

   private MavenProject getReactorProject(final Artifact artifact) {
      for (final MavenProject p : mojo.getReactorProjects()) {
         if (p.getGroupId().equals(artifact.getGroupId()) //
            && p.getArtifactId().equals(artifact.getArtifactId()) //
            && p.getVersion().equals(artifact.getVersion()) //
         )
            return p;
      }
      throw new NoSuchElementException("No project in reactor found matching " + artifact);
   }

   boolean isAnonymousInnerClass(final String className) {
      // inner classes are enumerated in class files TheClass.1, TheClass.2 etc.
      return Strings.isNumeric(Strings.substringAfterLast(className, "."));
   }

   boolean isArtifactWithClasses(final Artifact artifact) {
      final String type = artifact.getType();
      return "jar".equals(type) || "war".equals(type) || "ejb".equals(type);
   }

   private boolean isReactorProject(final Artifact artifact) {
      return mojo.getReactorProjects().stream().anyMatch(p -> //
      p.getGroupId().equals(artifact.getGroupId()) //
         && p.getArtifactId().equals(artifact.getArtifactId())//
         && p.getVersion().equals(artifact.getVersion()) //
      );
   }

   public ScanResult scan(final boolean checkForUnusedDependencies, final boolean checkForUsedTransitiveDependencies) throws MojoExecutionException {
      final ScanResult result = new ScanResult(project);

      if (!isArtifactWithClasses(project.getArtifact()))
         return result;

      /*
       * collect classes declared by the current project
       */
      final String logMsg = "Analyzing classes found in " + project.getBuild().getOutputDirectory();
      log.info(logMsg + "...");
      final Set<String> referencedClasses; // classes referenced by current project
      try {
         referencedClasses = scanDirectoryForDeclaredAndReferencedClasses(Paths.get(project.getBuild().getOutputDirectory())).get2();
      } catch (final IOException ex) {
         throw new MojoExecutionException(logMsg + " failed with: " + ex.getClass().getSimpleName() + " - " + ex.getMessage(), ex);
      }
      log.info(" => References to " + Pluralized.classes(referencedClasses.size(), "external") + " found.");

      /*
       * collect classes declared by direct dependencies
       */
      log.info("Analyzing classes of direct dependencies...");
      final Set<Artifact> directDeps = MavenUtils.withoutRuntimeAndTestScoped(mojo.getDirectDependencies());
      int directDepsClassCount = 0;
      for (final Artifact directDep : directDeps) {

         if (!isArtifactWithClasses(directDep)) {
            if (isVerbose) {
               log.info("Ignoring direct dependency: " + directDep);
            }
            continue;
         }

         if (isVerbose) {
            log.info("Resolving direct dependency: " + directDep);
         }
         mojo.resolveArtifact(directDep);

         try {
            if (isVerbose) {
               log.info("Collecting declared classes of direct dependency: " + directDep);
            }
            final Set<String> classesDeclaredByArtifact = scanArtifactForDeclaredClasses(directDep);
            directDepsClassCount += classesDeclaredByArtifact.size();
            boolean isDependencyUsed = false;

            // remove classes declared in direct dependencies from the referenced classes collection
            // this way we will only have transitively referenced classes in that collection at the end
            for (final Iterator<String> it = referencedClasses.iterator(); it.hasNext();) {
               final String referencedClass = it.next();
               if (classesDeclaredByArtifact.contains(referencedClass)) {
                  isDependencyUsed = true;

                  if (isVerbose) {
                     log.info(" - referenced class found: " + referencedClass);
                  }
                  it.remove();
               }
            }

            if (checkForUnusedDependencies && !isDependencyUsed) {
               result.unusedDirectDependencies.add(directDep);
            }
         } catch (final Exception ex) {
            throw new MojoExecutionException("Analyzing dependency " + directDep + " failed with: " + ex.getMessage(), ex);
         }
      }
      log.info(" => Found " + Pluralized.dependencies(directDeps.size(), "direct") + " with " + Pluralized.classes(directDepsClassCount) + ".");
      if (checkForUnusedDependencies) {
         if (result.hasUnusedDirectDependencies()) {
            log.warn(" => " + Pluralized.dependencies(result.unusedDirectDependencies.size(), "potentially unused") + " found:");
            result.unusedDirectDependencies.forEach(unusedDep -> log.warn("    |-> " + unusedDep));
         } else {
            log.info(" => No unused dependencies found.");
         }
      }

      /*
       * early exit if all referenced classes are declared by direct dependencies
       */
      if (referencedClasses.isEmpty())
         return result;

      /*
       * analyze the JAR files of transitively referenced artifacts
       */
      if (checkForUsedTransitiveDependencies) {
         log.info("Analyzing classes of transitive dependencies...");

         final Map<String, Artifact> classesDeclaredByTransitiveDependencies = getClassesDeclaredByTransitiveDependencies();
         log.info(" => Found " + Pluralized.dependencies(classesDeclaredByTransitiveDependencies.size(), "transitive") + ".");

         if (!classesDeclaredByTransitiveDependencies.isEmpty()) {
            // iterate over the transitively referenced classes and try to find the corresponding artifact
            for (final String referencedClass : referencedClasses) {
               final Artifact transDepArtifact = classesDeclaredByTransitiveDependencies.get(referencedClass);
               if (transDepArtifact == null) {
                  if (isVerbose) {
                     log.info("No transitive dependency declares referenced class: " + referencedClass);
                  }
               } else {
                  result.usedClassesOfTransitiveDependencies //
                     .computeIfAbsent(transDepArtifact, key -> new HashSet<>()) //
                     .add(referencedClass);
               }
            }
         }

         if (result.usedClassesOfTransitiveDependencies.isEmpty()) {
            log.info(" => No references to classes of transitive dependencies found.");
         } else {
            final int usedTransClasses = result.usedClassesOfTransitiveDependencies.size();
            final int usedTransDeps = result.getUsedTransitiveDependencies().size();
            log.warn(" => References to " + Pluralized.classes(usedTransClasses) + " of " + Pluralized.dependencies(usedTransDeps, "transitive") + " found.");
         }
      }

      return result;
   }

   /**
    * @return the fully qualified name of all declared classes e.g. com.acme.MyClass
    */
   private Set<String> scanArtifactForDeclaredClasses(final Artifact artifactWithJar) throws IOException {

      if (isReactorProject(artifactWithJar)) {
         final MavenProject project = getReactorProject(artifactWithJar);
         return scanDirectoryForDeclaredAndReferencedClasses(Paths.get(project.getBuild().getOutputDirectory())).get1();
      }

      if (artifactWithJar.getFile() == null)
         return Collections.emptySet();

      try (JarFile jar = new JarFile(artifactWithJar.getFile())) {
         final Set<String> classesDeclaredInJar = new HashSet<>();
         // iterate over the jar's entries
         for (final JarEntry jarEntry : Enumerations.toIterable(jar.entries())) {

            // ignore directory nodes
            if (jarEntry.isDirectory()) {
               continue;
            }

            final String fileName = jarEntry.getName();

            // ignore non-class files
            if (!fileName.endsWith(".class")) {
               continue;
            }

            String className = fileName;
            className = Strings.substringBeforeLast(className, ".class");
            className = Strings.replaceChars(className, '/', '.');
            className = Strings.replaceChars(className, '$', '.');

            if (isAnonymousInnerClass(className)) {
               continue;
            }

            // if (isVerbose) log.info("Found class: " + className);
            classesDeclaredInJar.add(className);
         }
         if (isVerbose) {
            log.info(" => Found " + Pluralized.classes(classesDeclaredInJar.size()) + " in inspected JAR file");
         }
         return classesDeclaredInJar;
      }
   }

   /**
    * Scans a target directory for Java class files, parses them and extracts information about declared and referenced external classes.
    *
    * @param classDirectory directory containing java class files (*.class)
    * @return declaredClasses fully qualified name of all declared/referenced classes e.g. com.acme.MyClass
    */
   private Tuple2<Set<String>, Set<String>> scanDirectoryForDeclaredAndReferencedClasses(final Path classDirectory) throws IOException {

      if (isVerbose) {
         log.info("Analyzing output directory: " + classDirectory);
      }

      if (!Files.exists(classDirectory)) {
         log.warn("Analyzed output directory [" + classDirectory + "] does not exist.");
         return Tuple2.create(Collections.emptySet(), Collections.emptySet());
      }

      final Set<String> declaredClasses = new HashSet<>(); // classes declared by current project
      final Set<String> referencedClasses = new HashSet<>(); // classes referenced by current project

      Files.walkFileTree(classDirectory, new SimpleFileVisitor<Path>() {
         @Override
         public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            // only consider Java class files
            if (!file.toString().endsWith(".class"))
               return FileVisitResult.CONTINUE;

            if (isVerbose) {
               log.info(" -> Analyzing class file: " + file);
            }

            try (InputStream classByteCode = new BufferedInputStream(Files.newInputStream(file))) {
               new AbstractClassAnalyzer(Opcodes.ASM9) {
                  @Override
                  protected void onClassName(final String nameOfReferencedClass) {
                     declaredClasses.add(nameOfReferencedClass);
                  }

                  @Override
                  protected void onClassReference(final String nameOfReferencedClass) {
                     if (!isAnonymousInnerClass(nameOfReferencedClass)) {
                        referencedClasses.add(nameOfReferencedClass);
                     }
                  }
               }.scan(classByteCode);
            }

            return FileVisitResult.CONTINUE;
         }
      });

      /*
       * remove self-declared classes from referenced classes collection
       */
      if (isVerbose) {
         log.info("Filtering out references to self-declared classes");
         for (final Iterator<String> it = referencedClasses.iterator(); it.hasNext();) {
            final String referencedClass = it.next();
            if (declaredClasses.contains(referencedClass)) {
               log.info(" - class: " + referencedClass);
               it.remove();
            }
         }
      } else {
         referencedClasses.removeAll(declaredClasses);
      }
      return Tuple2.create(declaredClasses, referencedClasses);
   }
}
