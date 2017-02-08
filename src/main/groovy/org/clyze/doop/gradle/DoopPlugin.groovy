package org.clyze.doop.gradle

import org.clyze.doop.web.client.Helper
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile

/**
 * The doop gradle plugin.
 */
class DoopPlugin implements Plugin<Project> {

    static final String DOOP_GROUP = "Doop"
    static final String TASK_SCAVENGE = 'scavenge'
    static final String TASK_JCPLUGIN_ZIP = 'jcpluginZip'
    static final String TASK_CODE_JAR = 'codeJar'
    static final String TASK_SOURCES_JAR = 'sourcesJar'
    static final String TASK_ANALYSE = 'analyse'

    public enum GradlePlugin {
      Java,
      Android
    }

    public static GradlePlugin plugin
    
    @Override
    void apply(Project project) {

        //verify that the appropriate plugins have been applied
        if (project.plugins.hasPlugin('java')) {
          plugin = GradlePlugin.Java
        }
        else if (project.plugins.hasPlugin('android') || project.plugins.hasPlugin('com.android.application')) {
          plugin = GradlePlugin.Android
        }
        else {
          throw new RuntimeException('One of the java/android/com.android.application plugins should be applied before Doop')
        }

        //require java 1.8 or higher
        if (!JavaVersion.current().isJava8Compatible()) {
            throw new RuntimeException("The Doop plugin requires Java 1.8 or higher")
        }

        //create the doop extension
        project.extensions.create('doop', DoopExtension)

        //set the default values
        configureDefaults(project)

        //configure the tasks
        configureScavengeTask(project)
        configureJCPluginZipTask(project)
        configureSourceJarTask(project)
        configureAnalyseTask(project)
        configureCodeJarTask(project)

        //update the project's artifacts
        project.artifacts {
            archives project.tasks.findByName('sourcesJar')
        }
    }

    private void configureDefaults(Project project) {
        project.extensions.doop.projectName = project.name
        project.extensions.doop.projectVersion = project.version?.toString()
        project.extensions.doop.scavengeOutputDir = project.file("build/scavenge")
        project.extensions.doop.analysis.options = Helper.createDefaultOptions()
    }

    private void configureScavengeTask(Project project) {
        JavaCompile task = project.tasks.create(TASK_SCAVENGE, JavaCompile)
        task.description = 'Scavenges the source files of the project for the Doop analysis'
        task.group = DOOP_GROUP

        //copy the project's Java compilation settings
        switch (plugin) {
        case GradlePlugin.Java:
          JavaCompile projectDefaultTask = project.tasks.findByName("compileJava")
          task.classpath = projectDefaultTask.classpath
          task.source = projectDefaultTask.source
          break
        case GradlePlugin.Android:
          def tName = "assemble"
          for (def set1 : project.android.sourceSets) {
            if (set1.name == "main") {
              task.source = set1.java.sourceFiles
            }
          }
          if (task.source == null) {
            throw new RuntimeException("Could not find sourceSet for task " + tName + ".")
          }
          task.classpath = project.files()
          break
        }

        //our custom settings
        File dest = project.extensions.doop.scavengeOutputDir
        def buildScriptConf = project.getBuildscript().configurations.getByName(ScriptHandler.CLASSPATH_CONFIGURATION)
        //TODO: Filter-out not required jars
        String processorPath = buildScriptConf.collect().join(File.pathSeparator)
        task.destinationDir = new File(dest as File, "classes")
        File jsonOutput = new File(dest as File, "json")
        task.options.compilerArgs = ['-processorpath', processorPath, '-Xplugin:TypeInfoPlugin ' + jsonOutput]
        if (plugin == GradlePlugin.Android) {
            // In Android, we augent the classpath in order to find
            // the Android API and other needed code. To find what is
            // needed to add to the classpath, we must scan metadata
            // such as the SDK version or the compile dependencies
            // (e.g. to find uses of the support libraries). Thus the
            // code below doesn't run now; it runs after all tasks
            // have been configured ("afterEvaluate").
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
				   "${appBuildHome}/intermediates/classes/${buildType}/com/example/android/camera2basic",
                                   "${project.rootDir}/${subprojectName}/R-class"
                                  ]

                def deps = []
                project.configurations.each { conf ->
                    // println "    Configuration: ${conf.name}"
                    conf.allDependencies.each { dep ->
                        def group = dep.group
                            if (group == "com.android.support") {
                                def name = dep.name
                                def version = dep.version
                                // println("Found dependency: " + group + ", " + name + ", " + version)
                                deps << "${appBuildHome}/intermediates/exploded-aar/${group}/${name}/${version}/jars/classes.jar"
                            }
                            else
                                throw new RuntimeException("DoopPlugin error: cannot handle dependency from group ${group}")
                    }
                }
                androidJars.addAll(deps.toSet().toList())
                // Check if all parts of the new classpath exist.
                androidJars.each {
                    if (!(new File(it)).exists())
                        println("DoopPlugin warning: classpath entry to add does not exist: " + it)
                }
                task.options.compilerArgs << "-cp"
                task.options.compilerArgs << androidJars.join(":")
                println(task.options.compilerArgs)

                // Update location of class files for JAR task.
                Jar jarTask = project.tasks.findByName(TASK_CODE_JAR)
                jarTask.from("${appBuildHome}/intermediates/classes/${buildType}")

            }
        }

        task.doFirst {
            jsonOutput.mkdirs()
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
            return sdkDir
        }
        else
            throw new RuntimeException("Please set a correct 'sdk.dir' location in file 'local.properties'.")
    }

    private void configureJCPluginZipTask(Project project) {
        Zip task = project.tasks.create(TASK_JCPLUGIN_ZIP, Zip)
        task.description = 'Zips the output files of the scavenge task'
        task.group = DOOP_GROUP

        task.dependsOn project.tasks.findByName(TASK_SCAVENGE)

        task.archiveName = 'jcplugin.zip'
        task.destinationDir = project.extensions.doop.scavengeOutputDir
        File jsonOutput = new File(project.extensions.doop.scavengeOutputDir as File, "json")
        task.from jsonOutput
    }

    private void configureSourceJarTask(Project project) {
        Jar task = project.tasks.create(TASK_SOURCES_JAR, Jar)
        task.description = 'Generates the sources jar'
        task.group = DOOP_GROUP

        switch (plugin) {
        case GradlePlugin.Java:
          task.dependsOn project.tasks.findByName('classes')
          break
        case GradlePlugin.Android:
          // This creates a circular dependency.
          // task.dependsOn project.getTasks().findByPath('assemble')
          break
        }
        task.classifier = 'sources'

        switch (plugin) {
        case GradlePlugin.Java:
          task.from project.sourceSets.main.allSource
          break
        case GradlePlugin.Android:
          task.from "src/main/java"
          break
        }
    }

    // Analogous to configureSourceJarTask, needed for Android, where
    // no JAR task exist in the Android gradle plugin.
    private void configureCodeJarTask(Project project) {
        if (plugin == GradlePlugin.Android) {
            Jar task = project.tasks.create(TASK_CODE_JAR, Jar)
            task.description = 'Generates the code jar'
            task.group = DOOP_GROUP
        }
    }

    private void configureAnalyseTask(Project project) {
        AnalyseTask task = project.tasks.create(TASK_ANALYSE, AnalyseTask)
        task.description = 'Starts the Doop analysis of the project'
        task.group = DOOP_GROUP

        switch (plugin) {
        case GradlePlugin.Java:
          task.dependsOn project.tasks.findByName('jar'),
                         project.tasks.findByName(TASK_SOURCES_JAR),
                         project.tasks.findByName(TASK_JCPLUGIN_ZIP)
          break
        case GradlePlugin.Android:
          task.dependsOn project.getTasks().findByPath('assemble'),
                         project.getTasks().findByPath(TASK_SOURCES_JAR),
                         project.getTasks().findByPath(TASK_JCPLUGIN_ZIP)
          break
        }
    }
}
