/*
 * SPDX-FileCopyrightText: © Vegard IT GmbH (https://vegardit.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.util;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;

import net.sf.jstuff.core.Strings;
import net.sf.jstuff.core.validation.Args;

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
public abstract class MavenUtils {

   public static Artifact dependencyToArtifact(final Dependency dependency, final RepositorySystem repositorySystem) {
      final Artifact artifact;
      if (Strings.isEmpty(dependency.getClassifier())) {
         artifact = repositorySystem.createArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), dependency
            .getType());
      } else {
         artifact = repositorySystem.createArtifactWithClassifier(dependency.getGroupId(), dependency.getArtifactId(), dependency
            .getVersion(), dependency.getType(), dependency.getClassifier());
      }
      artifact.setScope(dependency.getScope());
      return artifact;
   }

   public static void resolveArtifact( //
      final Artifact artifact, //
      final RepositorySystem repositorySystem, //
      final List<ArtifactRepository> remoteRepositories, //
      final ArtifactRepository localRepository //
   ) throws MojoExecutionException {
      Args.notNull("artifact", artifact);
      if (artifact.isResolved())
         return;

      final ArtifactResolutionResult result = repositorySystem.resolve( //
         new ArtifactResolutionRequest() //
            .setArtifact(artifact) //
            .setRemoteRepositories(remoteRepositories) //
            .setLocalRepository(localRepository) //
      );
      if (result.hasExceptions())
         throw new MojoExecutionException("Could not resolve artifact: " + artifact, result.getExceptions().get(0));
   }

   public static Artifact resolveArtifact( //
      final Dependency dependency, //
      final RepositorySystem repositorySystem, //
      final List<ArtifactRepository> remoteRepositories, //
      final ArtifactRepository localRepository //
   ) throws MojoExecutionException {
      final Artifact artifact = dependencyToArtifact(dependency, repositorySystem);
      resolveArtifact(artifact, repositorySystem, remoteRepositories, localRepository);
      return artifact;
   }

   public static String toString(final MavenProject project) {
      if (project == null)
         return null;

      if (project.getFile() == null)
         return project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getPackaging() + ":" + project.getVersion()
            + " @ <unkown location>";

      return project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getPackaging() + ":" + project.getVersion() + " @ "
         + project.getFile().getAbsolutePath();
   }

   public static Set<Artifact> withoutRuntimeAndTestScoped(final Set<Artifact> artifacts) {
      return artifacts.stream().filter(a -> !Artifact.SCOPE_RUNTIME.equals(a.getScope()) && !Artifact.SCOPE_TEST.equals(a.getScope()))
         .collect(Collectors.toSet());
   }
}
