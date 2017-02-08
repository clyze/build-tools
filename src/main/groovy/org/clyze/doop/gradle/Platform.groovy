package org.clyze.doop.gradle

import org.gradle.api.Project
import org.gradle.api.Task

/**
 * The Java platform used: plain Java or Android. Each patform is
 * handled by a different gradle plugin ('java' vs. 'android'), so
 * different tasks exist and the Doop plugin must examine different
 * metadata on each platform.
 */

interface Platform {
  void copyCompilationSettings(Project project, Task task);
  void fixClasspath(Project project, Task task);
  void createDependency(Project project, Task task);
  void gatherSources(Project project, Task task);
  void configureCodeJarTask(Project project);
  String buildTaskName();
  String jarTaskName();
  Set inputFiles(Project project, File jarArchive);
}