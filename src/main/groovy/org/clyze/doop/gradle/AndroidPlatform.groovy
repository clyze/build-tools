package org.clyze.doop.gradle

import groovy.io.FileType
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

class AndroidPlatform implements Platform {

    static final String TASK_CODE_JAR = 'codeJar'
    static final String TASK_ASSEMBLE = 'assemble'

    void copyCompilationSettings(Project project, Task task) {
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

    // Reads properties from local.properties and build.gradle and
    // fills in infromation needed when on Android. The code below is
    // scheduled to run after all tasks have been configured
    // ("afterEvaluate") and does the following:
    //
    // 1. It augments the classpath in order to find the Android API
    // and other needed code. To find what is needed, it scans
    // metadata such as the SDK version or the compile dependencies
    // (e.g. to find uses of the support libraries).
    //
    // 2. It sets the location of the class files needed by the code
    // JAR task.
    //
    // 3. It sets the location of the auto-generated Java sources in
    // the sources JAR task.
    //
    // 4. It creates dynamic dependencies of Doop tasks.
    void markMetadataToFix(Project project, JavaCompile scavengeTask) {
        project.afterEvaluate {
            // Read properties from build.gradle.

            def androidVersion = project.android.compileSdkVersion
            if (androidVersion == null)
                throw new RuntimeException("No android.compileSdkVersion found in build.gradle.")

            DoopExtension doop = project.extensions.doop
            def subprojectName = getSubprojectName(doop)
            def appBuildHome = "${project.rootDir}/${subprojectName}/build"

            def buildType = checkAndGetBuildType(doop)
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
                    if (group == null)
                        return
                    if (group == "com.android.support") {
                        def name = dep.name
                        def version = dep.version
                        // println("Found dependency: " + group + ", " + name + ", " + version)
                        deps << "${appBuildHome}/intermediates/exploded-aar/${group}/${name}/${version}/jars/classes.jar"
                    } else
                        println("AndroidPlatform: ignoring dependency from group ${group}")
                }
            }
            androidJars.addAll(deps.toSet().toList())
            // Check if all parts of the new classpath exist.
            androidJars.each {
                if (!(new File(it)).exists())
                    println("AndroidPlatform warning: classpath entry to add does not exist: " + it)
            }
            scavengeTask.options.compilerArgs << "-cp"
            scavengeTask.options.compilerArgs << androidJars.join(File.pathSeparator)
            // println(scavengeTask.options.compilerArgs)

            // Update location of class files for JAR task.
            Jar jarTask = project.tasks.findByName(TASK_CODE_JAR)
            jarTask.from("${appBuildHome}/intermediates/classes/${buildType}")

            def genSourceDirs = findGeneratedSourceDirs(appBuildHome, buildType)
            Jar sourcesJarTask = project.tasks.findByName(DoopPlugin.TASK_SOURCES_JAR)
            genSourceDirs.each { dir -> sourcesJarTask.from dir}
            scavengeTask.source(genSourceDirs)

            def assembleTaskDep
            switch (buildType) {
                case 'debug':
                    assembleTaskDep = 'assembleDebug'
                    break
                case 'release':
                    assembleTaskDep = 'assembleRelease'
                    break
            }

            // Create dependency on source JAR task in order to create
            // the R.java files.
            sourcesJarTask.dependsOn project.tasks.findByName(assembleTaskDep)
        }
    }

    static String baseName(File file) {
        return file.name.replaceFirst(~/\.[^\.]+$/, '')
    }

    static String extension(String name) {
        return name.substring(name.lastIndexOf('.') + 1, name.size())
    }

    // Add auto-generated Java files (examples are the app's R.java,
    // other R.java files, and classes in android.support packages).
    static List findGeneratedSourceDirs(String appBuildHome, String buildType) {
        def genSourceDirs = []
        def generatedSources = "${appBuildHome}/generated/source"
        new File(generatedSources).eachFile (FileType.DIRECTORIES) { dir ->
            dir.eachFile (FileType.DIRECTORIES) { bPath ->
                if (baseName(bPath) == buildType) {
                    // Add subdirectories containing .java files.
                    def containsJava = false
                    bPath.eachFileRecurse (FileType.FILES) { f ->
                        def fName = f.name
                        if ((!containsJava) && extension(fName) == "java")
                            containsJava = true
                    }
                    if (containsJava) {
                        println "Found generated Java sources in ${bPath}"
                        genSourceDirs << bPath.getAbsolutePath()
                    }
                }
            }
        }
        return genSourceDirs
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
        } else
            throw new RuntimeException("Please set a correct 'sdk.dir' location in file 'local.properties'.")
    }

    void createScavengeDependency(Project project, Task task) {
        task.dependsOn project.tasks.findByName(TASK_ASSEMBLE)
    }

    // This method is empty; the dependency is recorded in
    // markMetadataToFix(). The reason is that 'assemble' creates a
    // circular dependency and thus we use 'assemble{Debug,Release}'.
    void createSourcesJarDependency(Project project, Task task) {
    }

    void gatherSources(Project project, Task task) {
        task.from "src/main/java"
    }

    // Analogous to configureSourceJarTask(), needed for Android,
    // where no JAR task exists in the Android gradle plugin. The task
    // is not fully created here; its inputs are set "afterEvaluate"
    // (see method markMetadataToFix() above).
    void configureCodeJarTask(Project project) {
        Jar task = project.tasks.create(TASK_CODE_JAR, Jar)
        task.description = 'Generates the code jar'
        task.group = DoopPlugin.DOOP_GROUP
        task.dependsOn project.getTasks().findByName(TASK_ASSEMBLE)
    }

    String jarTaskName() { return TASK_CODE_JAR }

    List inputFiles(Project project, File jarArchive) { [jarArchive] }

    String getClasspath(Project project) {
	// Unfortunately, ScriptHandler.CLASSPATH_CONFIGURATION is not
	// a real task in the Android Gradle plugin and we have to use
	// a lower-level way to read the classpath.
	def cLoader = project.buildscript.getClassLoader()
	if (cLoader instanceof URLClassLoader) {
	    URLClassLoader cl = (URLClassLoader)cLoader
	    return cl.getURLs().collect().join(File.pathSeparator).replaceAll('file://', '')
	} else {
	    throw new RuntimeException('AndroidPlatform: cannot get classpath for jcplugin')
	}
    }

    String checkAndGetBuildType(DoopExtension doop) {
	def buildType = doop.buildType
	if (buildType == null) {
	    throw new RuntimeException("Please set doop.buildType to the type of the existing build ('debug' or 'release').")
	} else if ((buildType != 'debug') && (buildType != 'release')) {
	    throw new RuntimeException("Property doop.buildType must be 'debug' or 'release'.")
	}
	return buildType
    }

    String getSubprojectName(DoopExtension doop) {
	if (doop.subprojectName == null)
	    throw new RuntimeException("Please set doop.subprojectName to the name of the app subproject (e.g. 'Application').")
	else
	    return doop.subprojectName
    }
}
