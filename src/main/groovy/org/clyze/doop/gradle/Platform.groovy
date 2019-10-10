package org.clyze.doop.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.bundling.Jar
import groovy.transform.CompileStatic

/**
 * The Java platform used: plain Java or Android. Each platform is
 * handled by a different gradle plugin ('java' vs. 'android'), so
 * different tasks exist and the Doop plugin must examine different
 * metadata on each platform.
 */

@CompileStatic
interface Platform {
  void copyCompilationSettings(Project project, JavaCompile task)
  void markMetadataToFix(Project project)
  void createScavengeDependency(Project project, JavaCompile scavengeTask)
  void gatherSources(Project project, Jar sourcesJarTask)
  void configureCodeJarTask(Project project)
  String jarTaskName()
  List<String> inputFiles(Project project)
  List<String> libraryFiles(Project project)
  String getClasspath(Project project)
  String getProjectName(Project project)
  boolean mustRunAgain()
  void cleanUp()
  // True if the metadata processor runs in a separate Gradle task,
  // false if the processor is integrated in an existing task.
  boolean explicitScavengeTask()
}
