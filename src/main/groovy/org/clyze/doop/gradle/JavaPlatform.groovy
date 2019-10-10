package org.clyze.doop.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.clyze.client.SourceProcessor
import static org.clyze.doop.gradle.DoopPlugin.*

class JavaPlatform implements Platform {

    void copyCompilationSettings(Project project, JavaCompile task) {
        JavaCompile projectDefaultTask = project.tasks.findByName("compileJava") as JavaCompile
        task.classpath = projectDefaultTask.classpath
        Set<File> source = projectDefaultTask.source.getFiles()

        final String COMPILE_TEST_JAVA = "compileTestJava"
        JavaCompile projectTestTask = project.tasks.findByName(COMPILE_TEST_JAVA) as JavaCompile
        if (projectTestTask != null) {
            // We cannot combine the classpaths from the two tasks to create a
            // new classpath (Gradle complains), so we must use 'extraInputs'.
            println "WARNING: adding sources from task ${COMPILE_TEST_JAVA}, please use 'extraInputs' in the build.gradle to fix missing classpath entries."
            source.addAll(projectTestTask.source.getFiles())
        }

        task.source = project.files(source as List)
    }

    /** Things to do last are:
     *
     * - Optional UTF-8 conversion ('convertUTF8Dir' Doop parameter).
     *
     * - Feed extra inputs to the scavenge task ('extraInputs' Doop
     *   parameter).
     *
     * - Set up 'sourcesJar' task according to 'useSourcesJar' parameter.
     *
     */
    void markMetadataToFix(Project project) {
        project.afterEvaluate {
            DoopExtension doop = DoopExtension.of(project)

            String convPath = doop.convertUTF8Dir
            if (convPath != null) {
                println "Converting to UTF-8 in ${convPath}..."
                SourceProcessor sp = new SourceProcessor()
                sp.process(new File(convPath), true)
            }

            List<String> extras = doop.getExtraInputFiles(project.rootDir)
            if (extras != null && extras.size() > 0) {
                String extraCp = extras.join(File.pathSeparator)
                JavaCompile scavengeTask = project.tasks.findByName(TASK_SCAVENGE) as JavaCompile
                scavengeTask.options.compilerArgs << "-cp"
                scavengeTask.options.compilerArgs << extraCp
            }

            String sourcesJar = doop.useSourcesJar
            if (sourcesJar != null) {
                println "No setup for '${TASK_SOURCES_JAR}' task, using: ${sourcesJar}"
            } else {
                Jar sourcesJarTask = project.tasks.findByName(TASK_SOURCES_JAR) as Jar
                sourcesJarTask.dependsOn project.tasks.findByName('classes')
            }
        }
    }

    void createScavengeDependency(Project project, JavaCompile scavengeTask) {}

    void gatherSources(Project project, Jar sourcesJarTask) {
        sourcesJarTask.from project.sourceSets.main.allSource

        final String TEST_SOURCE_SET = "test"
        if (project.sourceSets.hasProperty(TEST_SOURCE_SET)) {
            println "Also adding sources from ${TEST_SOURCE_SET}"
            sourcesJarTask.from project.sourceSets.test.allSource
        }
    }

    // No code JAR task is created, the 'java' gradle plugin already
    // provides 'jar'.
    void configureCodeJarTask(Project project) {}

    String jarTaskName() { return 'jar' }

    List<String> inputFiles(Project project) {
        def jar = project.file(project.tasks.findByName(jarTaskName()).archivePath)
        return [jar.canonicalPath]
    }

    List<String> libraryFiles(Project project) {
        List<String> extraInputFiles = DoopExtension.of(project).getExtraInputFiles(project.rootDir)
        List<String> runtimeFiles = project.configurations.runtime.files.collect { it.canonicalPath }
        return runtimeFiles + extraInputFiles
    }

    String getClasspath(Project project) {
        def buildScriptConf = project.getBuildscript().configurations.getByName(ScriptHandler.CLASSPATH_CONFIGURATION)
        //TODO: Filter-out not required jars
        return buildScriptConf.collect().join(File.pathSeparator)
    }

    String getProjectName(Project project) {
	return project.name
    }

    boolean mustRunAgain() {
        return false
    }

    void cleanUp() { }

    // In Java mode, always use an explicit "scavenge" Gradle task.
    boolean explicitScavengeTask() {
        return true
    }
}
