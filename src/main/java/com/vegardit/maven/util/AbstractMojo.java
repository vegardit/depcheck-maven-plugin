/*
 * SPDX-FileCopyrightText: © Vegard IT GmbH (https://vegardit.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;

import net.sf.jstuff.core.Strings;
import net.sf.jstuff.core.validation.Args;

/**
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
public abstract class AbstractMojo implements Mojo {

   private static final Log DEFAULT_LOG = new SystemStreamLog();

   public static final String DIVIDER = Strings.repeat('=', 73);

   private static final ConcurrentMap<String, AtomicInteger> SYNCHRONIZERS = new ConcurrentHashMap<>();

   @Parameter(defaultValue = "false")
   protected boolean skip;

   @Parameter(defaultValue = "false")
   protected boolean verbose;

   protected Log log = DEFAULT_LOG;

   @Parameter(defaultValue = "${project}", readonly = true)
   protected MavenProject mvnCurrentProject;

   @Parameter(defaultValue = "${mojoExecution}", readonly = true)
   protected MojoExecution mvnExecution;

   @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
   protected ArtifactRepository mvnLocalRepo;

   /**
    * all projects currently in the reactor
    */
   @Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
   protected List<MavenProject> mvnReactorProjects;

   @Component
   protected RepositorySystem mvnRepoSystem;

   @Parameter(defaultValue = "${session}", readonly = true)
   protected MavenSession mvnSession;

   @Override
   public final void execute() throws MojoExecutionException, MojoFailureException {
      if (isDirectExecution()) {
         verbose = "true".equals(System.getProperty("verbose"));
      }

      if (skip) {
         log.info("Skipping execution as requested by configuration.");
         return;
      }

      final String executionId = getClass().getName() + ":" + mvnExecution.getExecutionId() + ":" + mvnExecution.getGoal();

      final AtomicInteger synchronizer = SYNCHRONIZERS.computeIfAbsent(executionId, key -> new AtomicInteger(mvnReactorProjects.size()));
      try {
         executeOnEachProject();
      } finally {
         if (synchronizer.decrementAndGet() == 0) {
            SYNCHRONIZERS.remove(executionId);
            executeAfterLastProject();
         }
      }
   }

   @SuppressWarnings("unused")
   protected void executeAfterLastProject() throws MojoExecutionException, MojoFailureException {
   }

   @SuppressWarnings("unused")
   protected void executeOnEachProject() throws MojoExecutionException, MojoFailureException {
   }

   public Set<Artifact> getDirectDependencies() {
      final Set<Artifact> deps = mvnCurrentProject.getDependencies().stream() //
         .map(d -> MavenUtils.dependencyToArtifact(d, mvnRepoSystem)) //
         .collect(Collectors.toSet());

      if (verbose) {
         log.info("getDirectDependencies():");
         deps.stream().sorted().forEach(dep -> log.info(" - " + dep));
      }
      return deps;
   }

   @Override
   public Log getLog() {
      return log;
   }

   public MavenProject getProject() {
      return mvnCurrentProject;
   }

   public List<MavenProject> getReactorProjects() {
      return mvnReactorProjects;
   }

   /**
    * @return a set of indirectly referenced artifacts
    */
   public Set<Artifact> getTransitiveDependencies() {

      final var deps = new HashSet<>(mvnCurrentProject.getArtifacts());
      deps.removeAll(getDirectDependencies());

      if (verbose) {
         log.info("getTransitiveDependencyArtifacts():");
         deps.stream().sorted().forEach(dep -> log.info(" - " + dep));
      }
      return deps;
   }

   /**
    * @return true if the goal was directly executed via Maven CLI
    */
   public boolean isDirectExecution() {
      return mvnExecution.getLifecyclePhase() == null;
   }

   public boolean isSkip() {
      return skip;
   }

   public boolean isVerbose() {
      return verbose;
   }

   public void resolveArtifact(final Artifact artifact) throws MojoExecutionException {
      Args.notNull("artifact", artifact);
      if (artifact.isResolved())
         return;

      MavenUtils.resolveArtifact(artifact, mvnRepoSystem, mvnCurrentProject.getRemoteArtifactRepositories(), mvnLocalRepo);
   }

   public Artifact resolveArtifact(final Dependency dep) throws MojoExecutionException {
      return MavenUtils.resolveArtifact(dep, mvnRepoSystem, mvnCurrentProject.getRemoteArtifactRepositories(), mvnLocalRepo);
   }

   @Override
   public void setLog(final Log log) {
      this.log = log;
   }
}
