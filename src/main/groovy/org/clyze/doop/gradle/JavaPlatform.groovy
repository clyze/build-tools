package org.clyze.doop.gradle

import groovy.transform.InheritConstructors
import groovy.transform.TypeChecked
import org.clyze.client.SourceProcessor
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

@TypeChecked
@InheritConstructors
class JavaPlatform extends Platform {

    @Override
    void copyCompilationSettings(JavaCompile task) {
        JavaCompile projectDefaultTask = project.tasks.findByName("compileJava") as JavaCompile
        task.classpath = projectDefaultTask.classpath
        Set<File> source = projectDefaultTask.source.getFiles()

        final String COMPILE_TEST_JAVA = "compileTestJava"
        JavaCompile projectTestTask = project.tasks.findByName(COMPILE_TEST_JAVA) as JavaCompile
        if (projectTestTask != null) {
            // We cannot combine the classpaths from the two tasks to create a
            // new classpath (Gradle complains), so we must use 'extraInputs'.
            println "WARNING: adding sources from task ${COMPILE_TEST_JAVA}, please use 'extraInputs' in build.gradle to fix missing classpath entries."
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
    @Override
    void markMetadataToFix() {
        project.afterEvaluate {
            DoopExtension doop = DoopExtension.of(project)

            // Read properties from build.gradle.
            if (!definesRequiredProperties()) {
                project.logger.warn MISSING_PROPERTIES
                return
            }

            String convPath = doop.convertUTF8Dir
            if (convPath != null) {
                println "Converting to UTF-8 in ${convPath}..."
                SourceProcessor sp = new SourceProcessor()
                sp.process(new File(convPath), true)
            }

            List<String> extras = doop.getExtraInputFiles(project.rootDir)
            if (extras != null && extras.size() > 0) {
                String extraCp = extras.join(File.pathSeparator)
                JavaCompile scavengeTask = project.tasks.findByName(DoopPlugin.TASK_SCAVENGE) as JavaCompile
                scavengeTask.options.compilerArgs << "-cp"
                scavengeTask.options.compilerArgs << extraCp
            }

            String sourcesJar = doop.useSourcesJar
            if (sourcesJar != null) {
                println "No setup for '${DoopPlugin.TASK_SOURCES_JAR}' task, using: ${sourcesJar}"
            } else {
                Jar sourcesJarTask = project.tasks.findByName(DoopPlugin.TASK_SOURCES_JAR) as Jar
                sourcesJarTask.dependsOn project.tasks.findByName('classes')
            }
        }
    }

    @Override
    void createScavengeDependency(JavaCompile scavengeTask) {}

    @Override
    void gatherSources(Jar sourcesJarTask) {
        SourceSetContainer sourceSets = JavaAPI.getSourceSets(project)
        sourcesJarTask.from JavaAPI.getMainSources(project)

        final String TEST_SOURCE_SET = "test"
        if (sourceSets.hasProperty(TEST_SOURCE_SET)) {
            println "Also adding sources from ${TEST_SOURCE_SET}"
            sourcesJarTask.from JavaAPI.getTestSources(project)
        }
    }

    // No code JAR task is created, the 'java' gradle plugin already
    // provides 'jar'.
    @Override
    void configureCodeJarTask() {}

    @Override
    String jarTaskName() { return 'jar' }

    @Override
    List<String> getInputFiles() {
        return [getOutputCodeArchive()]
    }

    @Override
    public String getOutputCodeArchive() {
        AbstractArchiveTask jarTask = project.tasks.findByName(jarTaskName()) as AbstractArchiveTask
        if (!jarTask) {
            project.logger.error "Could not find jar task ${jarTaskName()}"
            return [] as List<String>
        }
        return project.file(jarTask.archiveFile).canonicalPath
    }

    @Override
    List<String> getLibraryFiles() {
        List<String> extraInputFiles = DoopExtension.of(project).getExtraInputFiles(project.rootDir)
        println "project configuration type: ${project.configurations.class}"
        List<String> runtimeFiles = JavaAPI.getRuntimeFiles(project)
        return runtimeFiles + extraInputFiles
    }

    @Override
    String getClasspath() {
        Configuration clConf = project.getBuildscript().configurations.getByName(ScriptHandler.CLASSPATH_CONFIGURATION)
        //TODO: Filter-out not required jars
        return clConf.collect().join(File.pathSeparator)
    }

    @Override
    String getProjectName() {
	    return project.name
    }

    @Override
    boolean mustRunAgain() {
        return false
    }

    @Override
    void cleanUp() { }

    // In Java mode, always use an explicit "scavenge" Gradle task.
    @Override
    boolean explicitScavengeTask() {
        return true
    }

    @Override
    void configureConfigurationsTask() {
        println "WARNING: the configurations task is not yet implemented."
    }
}
