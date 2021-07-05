package com.clyze.build.tools.gradle

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import com.clyze.build.tools.Conventions
import com.clyze.client.SourceProcessor
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

import static com.clyze.build.tools.Conventions.msg

/**
 * This class controls how the plugin adapts to projects based on the JDK.
 */
@CompileStatic
@InheritConstructors
class JavaPlatform extends Platform {

    /**
     * This copies the compilation settings (sources and classpath) of the
     * Java compilation tasks defined in the project.
     * @param task   the target task to receive the copy
     */
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
            project.logger.warn msg("WARNING: adding sources from task ${COMPILE_TEST_JAVA}, please use 'extraInputs' in build.gradle to fix missing classpath entries.")
            source.addAll(projectTestTask.source.getFiles())
        }

        task.source = project.files(source as List)
    }

    /** Things to do last are:
     *
     * (a) Optional UTF-8 conversion ('convertUTF8Dir' parameter).
     *
     * (b) Feed extra inputs to the scavenge task ('extraInputs'
     *     parameter).
     *
     * (c) Set up 'sourcesJar' task according to 'useSourcesJar' parameter.
     *
     */
    @Override
    void markMetadataToFix() {
        project.afterEvaluate {
            Extension ext = Extension.of(project)

            // Read properties from build.gradle.
            if (!definesRequiredProperties()) {
                project.logger.warn MISSING_PROPERTIES
                return
            }

            String convPath = ext.convertUTF8Dir
            if (convPath != null) {
                project.logger.info msg("Converting to UTF-8 in ${convPath}...")
                SourceProcessor sp = new SourceProcessor()
                sp.process(new File(convPath), true)
            }

            List<String> extras = ext.getExtraInputFiles(project.rootDir)
            if (extras != null && extras.size() > 0) {
                String extraCp = extras.join(File.pathSeparator)
                JavaCompile scavengeTask = project.tasks.findByName(PTask.SCAVENGE.name) as JavaCompile
                scavengeTask.options.compilerArgs << "-cp"
                scavengeTask.options.compilerArgs << extraCp
            }

            if (ext.sources) {
                configureSourceTasks()
                String sourcesJar = ext.useSourcesJar
                if (sourcesJar != null) {
                    project.logger.info msg("No setup for '${PTask.SOURCES_JAR.name}' task, using: ${sourcesJar}")
                } else {
                    Jar sourcesJarTask = project.tasks.findByName(PTask.SOURCES_JAR.name) as Jar
                    sourcesJarTask.dependsOn project.tasks.findByName('classes')
                }
            }
        }
    }

    /**
     * Dummy implementation of createScavengeDependency().
     * @param scavengeTask  the scavenge task (if enabled)
     */
    @Override
    void createScavengeDependency(JavaCompile scavengeTask) {}

    /**
     * Gathers the sources from the project "source sets".
     * @param sourcesJarTask   the "source archive" task to use
     *                         for gathering the sources
     */
    @Override
    void gatherSources(Jar sourcesJarTask) {
        SourceSetContainer sourceSets = JavaAPI.getSourceSets(project)
        sourcesJarTask.from JavaAPI.getMainSources(project)

        final String TEST_SOURCE_SET = "test"
        if (sourceSets.hasProperty(TEST_SOURCE_SET)) {
            project.logger.info msg("Also adding sources from ${TEST_SOURCE_SET}")
            sourcesJarTask.from JavaAPI.getTestSources(project)
        }
    }

    /**
     * No code JAR task is created, the 'java' Gradle plugin already
     * provides 'jar'.
     */
    @Override
    void configureCodeTask() {}

    /**
     * The Gradle task that generates the code input of the snapshot.
     * @return the name of the Gradle task
     */
    @Override
    String codeTaskName() { return 'jar' }

    @Override
    List<String> getInputFiles() {
        return [getOutputCodeArchive()]
    }

    @Override
    String getOutputCodeArchive() {
        AbstractArchiveTask jarTask = project.tasks.findByName(codeTaskName()) as AbstractArchiveTask
        if (!jarTask) {
            project.logger.error msg("Could not find jar task ${codeTaskName()}")
            return [] as List<String>
        }
        return project.file(jarTask.archiveFile).canonicalPath
    }

    @Override
    Set<String> getLibraryFiles() {
        Set<String> extraInputFiles = Extension.of(project).getExtraInputFiles(project.rootDir) as Set<String>
        Set<String> runtimeFiles = JavaAPI.getRuntimeFiles(project)
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
    void cleanUp() { }

    /**
     * Do not use an explicit "scavenge" Gradle task. NOTE: if this
     * returns true, then the classpath passed to javac should contain
     * the metadata processor JAR.
     */
    @Override
    boolean explicitScavengeTask() {
        return false
    }

    /**
     * Dummy implementation, since the 'java' Gradle plugin does not
     * integrate with rule files.
     */
    @Override
    protected void readConfigurationFiles() {
        if (!getRepackageExt().configurationFiles) {
            project.logger.warn msg("WARNING: configurations are not gathered automatically. Use option 'configurationFiles' to set configuration inputs manually.")
        }
        zipConfigurations()
    }

    /**
     * Dummy implementation of injectConfiguration().
     * @param conf           the configuration to inject
     * @param errorMessage   message to show on failure
     */
    @Override
    protected void injectConfiguration(File conf, String errorMessage) {
        project.logger.debug msg("WARNING: special configuration injection is not yet supported for Java projects.")
    }

    /**
     * Filter for Java project code artifacts.
     * @param filename    a file name
     * @return true if the file is a code artifact, false otherwise
     */
    @Override
    boolean isCodeArtifact(String filename) {
        return filename.toLowerCase().endsWith('.jar')
    }

    @Override
    protected String getDefaultProfile() {
        return Conventions.JVM_STACK
    }

    @Override
    protected String getDefaultAutomatedRepackagingProfile() {
        return Conventions.DEFAULT_JAVA_CLYZE_PROFILE
    }
}
