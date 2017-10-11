package org.clyze.doop.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.bundling.Jar

/**
 * The Java platform used: plain Java or Android. Each platform is
 * handled by a different gradle plugin ('java' vs. 'android'), so
 * different tasks exist and the Doop plugin must examine different
 * metadata on each platform.
 */

interface Platform {
  void copyCompilationSettings(Project project, Task task)
  void markMetadataToFix(Project project)
  void createScavengeDependency(Project project, JavaCompile scavengeTask)
  void gatherSources(Project project, Jar sourcesJarTask)
  void configureCodeJarTask(Project project)
  String jarTaskName()
  List<String> inputFiles(Project project)
  String getClasspath(Project project)
  String getProjectName(Project project)
  boolean mustRunAgain()
}
