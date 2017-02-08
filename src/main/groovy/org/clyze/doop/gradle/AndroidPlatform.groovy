package org.clyze.doop.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar

public class AndroidPlatform implements Platform {

    static final String TASK_CODE_JAR = 'codeJar'
    static final String TASK_ASSEMBLE = 'assemble'

    public void copyCompilationSettings(Project project, Task task) {
        def tName = TASK_ASSEMBLE
        for (def set1 : project.android.sourceSets) {
            if (set1.name == "main") {
                task.source = set1.java.sourceFiles
            }
        }
        if (task.source == null) {
            throw new RuntimeException("Could not find sourceSet for task " + tName + ".")
        }
        task.classpath = project.files()
    }

    // In Android, we augent the classpath in order to find the
    // Android API and other needed code. To find what is needed to
    // add to the classpath, we must scan metadata such as the SDK
    // version or the compile dependencies (e.g. to find uses of the
    // support libraries). Thus the code below doesn't run now; it
    // runs after all tasks have been configured ("afterEvaluate").
    // This method also affects the code JAR task.
    public void fixClasspath(Project project, Task task) {
        project.afterEvaluate {
            // Read properties from build.gradle.

            def androidVersion = project.android.compileSdkVersion
            if (androidVersion == null)
                throw new RuntimeException("No android.compileSdkVersion found in buid.gradle.")

                DoopExtension doop = project.extensions.doop
                def subprojectName = doop.subprojectName
                if (subprojectName == null)
                    throw new RuntimeException("Please set doop.subprojectName to the name of the app subproject (e.g. 'Application').")
                def appBuildHome = "${project.rootDir}/${subprojectName}/build"

                def buildType = doop.buildType
                if (buildType == null)
                    throw new RuntimeException("Please set doop.buildType to the type of the existing build ('debug' or 'release').")

                def annotationsVersion = doop.annotationsVersion
                if (annotationsVersion == null)
                    throw new RuntimeException("Please set doop.annotationsVersion to the version of the annotations package used (e.g. '24.1.1').")

                // Find locations of the Android SDK and the project build path.
                def androidSdkHome = findSDK(project)
                // Add to classpath: android.jar/layoutlib.jar (core
                // OS API), the annotations JAR, and the location of
                // R*.class files.
                def androidJars = ["${androidSdkHome}/platforms/${androidVersion}/android.jar",
                                   "${androidSdkHome}/platforms/${androidVersion}/data/layoutlib.jar",
                                   "${androidSdkHome}/extras/android/m2repository/com/android/support/support-annotations/${annotationsVersion}/support-annotations-${annotationsVersion}.jar",
                                   "${appBuildHome}/intermediates/classes/${buildType}"]

                def deps = []
                project.configurations.each { conf ->
                    // println "Configuration: ${conf.name}"
                    conf.allDependencies.each { dep ->
                        def group = dep.group
                        if (group == "com.android.support") {
                            def name = dep.name
                            def version = dep.version
                            // println("Found dependency: " + group + ", " + name + ", " + version)
                            deps << "${appBuildHome}/intermediates/exploded-aar/${group}/${name}/${version}/jars/classes.jar"
                        }
                        else
                            throw new RuntimeException("AndroidPlatform error: cannot handle dependency from group ${group}")
                    }
                }
                androidJars.addAll(deps.toSet().toList())
                // Check if all parts of the new classpath exist.
                androidJars.each {
                    if (!(new File(it)).exists())
                        println("AndroidPlatform warning: classpath entry to add does not exist: " + it)
                }
                task.options.compilerArgs << "-cp"
                task.options.compilerArgs << androidJars.join(":")
                // println(task.options.compilerArgs)

                // Update location of class files for JAR task.
                Jar jarTask = project.tasks.findByName(TASK_CODE_JAR)
                jarTask.from("${appBuildHome}/intermediates/classes/${buildType}")

        }
    }

        // Find the location of the Android SDK. Assumes it is given as
    // entry 'sdk.dir' in file 'local.properties' of the project.
    private String findSDK(Project project) {
        def rootDir = project.rootDir
        def localProperties = new File(rootDir, "local.properties")
        if (localProperties.exists()) {
            Properties properties = new Properties()
            localProperties.withInputStream { instr ->
              properties.load(instr)
            }
            def sdkDir = properties.getProperty('sdk.dir')
            // println("Android SDK = " + sdkDir)
	    if (!(new File(sdkDir)).exists())
		println("AndroidPlatform warning: Android SDK directory does not exist: " + sdkDir)
            return sdkDir
        }
        else
            throw new RuntimeException("Please set a correct 'sdk.dir' location in file 'local.properties'.")
    }

    public void createDependency(Project project, Task task) {
        // This creates a circular dependency.
        // task.dependsOn project.getTasks().findByPath('assemble')
    }

    public void gatherSources(Project project, Task task) {
        task.from "src/main/java"
    }

    // Analogous to configureSourceJarTask(), needed for Android,
    // where no JAR task exists in the Android gradle plugin. The task
    // is not fully created here; its inputs are set "afterEvaluate"
    // (see method fixClasspath() above).
    public void configureCodeJarTask(Project project) {
        Jar task = project.tasks.create(TASK_CODE_JAR, Jar)
        task.description = 'Generates the code jar'
        task.group = DoopPlugin.DOOP_GROUP
    }

    public String buildTaskName() { return TASK_ASSEMBLE; }

    public String jarTaskName() { return TASK_CODE_JAR; }

    public Set inputFiles(Project project, File jarArchive) {
        return [jarArchive] as Set;
    }
}
