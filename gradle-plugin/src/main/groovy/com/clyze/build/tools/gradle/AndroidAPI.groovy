package com.clyze.build.tools.gradle

import org.clyze.signing.Signer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree

import static com.clyze.build.tools.Conventions.msg

/**
 * This class provides access to the Android Gradle API (including
 * internals) without a compile-time dependency.
 */
class AndroidAPI {
    static void forEachSourceFile(Project project, def closure) {
        for (def set1 : project.android.sourceSets) {
            println msg("Set: ${set1.class}")
            if (set1.name == "main") {
                FileTree srcFiles = set1.java.sourceFiles
                closure(srcFiles)
            }
        }
    }

    static void forEachClasspathEntry(Project project, def closure) {
        project.android.getBootClasspath().collect {
            closure(it.canonicalPath)
        }
    }

    static List<String> getOutputs(Project project, String buildType, String flavor) {
        List<String> ret = [] as List<String>
        iterateOverSpecificVariants project, buildType, flavor, { variant ->
            variant.outputs.each { output ->
                ret.add output.outputFile.canonicalPath
            }
        }
        return ret
    }

    /**
     * Get the test configurations, so that they can be excluded from the
     * build. Since testing only uses a build type, no flavor parameter is needed.
     *
     * @param project    the current project
     * @param buildType  the build type used for testing
     * @return the set of test configurations
     */
    static Set<File> getTestConfigurations(Project project, String buildType) {
        Set<File> ret = [] as Set<File>
        project.android.buildTypes.each { bt ->
            if (bt.name == buildType) {
                bt.testProguardFiles.each { t ->
                    if (t instanceof File) {
                        ret.add(t)
                    } else if (t) {
                        project.logger.warn msg("WARNING: testProguardFile ${t} could not be excluded from build.")
                    }
                }
            }
        }
        return ret
    }

    static List<String> getOutputs(Project project, Task packageTask) {
        return packageTask.outputs.files
            .findAll { AUtils.isAppCodeArtifact(it.name) || it.name.endsWith('.aar') }
            .collect { it.canonicalPath }
            .toList() as List<String>
    }

    /*
     * Iterate over repackaging transforms of a build type.
     *
     * @param project      the current project
     * @param variantName  the variant name (for example, flavor name + build type)
     * @param closure      an action to perform on each matching transform
     */
    static void forEachRepackageTransform(Project project, String variantName, def closure) {
        Class transformTask = AUtils.getInternalClass(project, "com.android.build.gradle.internal.pipeline.TransformTask")
        Class pgTransform1 = AUtils.getInternalClass(project, "com.android.build.gradle.internal.transforms.ProguardConfigurable")
        Class pgTransform2 = AUtils.getInternalClass(project, "com.android.build.gradle.internal.tasks.ProguardTask")
        Class pgTransform3 = AUtils.getInternalClass(project, "com.android.build.gradle.internal.tasks.R8Task")

        project.logger.debug msg("Variant filter: ${variantName}")
        project.tasks.each {
            project.logger.debug "Checking task: ${it} (${it.class} extends ${it.class.superclass})"
            try {
                if (transformTask?.isInstance(it)) {
                    if (it.transform && pgTransform1?.isInstance(it.transform) &&
                        it.variantName == variantName) {
                        project.logger.info msg("Processing configuration files in transform: ${it}")
                        FileCollection pros = it.transform.getAllConfigurationFiles()
                        closure(pros)
                    } else
                        project.logger.debug msg("Ignoring transform task: ${it} (variant: ${it.variantName})")
                } else if (pgTransform2?.isInstance(it) || pgTransform3?.isInstance(it)) {
                    if (it.variantName == variantName) {
                        project.logger.info msg("Processing configuration files in transform: ${it}")
                        FileCollection pros = it.getConfigurationFiles()
                        closure(pros)
                    } else
                        project.logger.debug msg("Ignoring transform task: ${it} (variant: ${it.variantName})")
                }
            } catch (Throwable t) {
                println msg("Error reading task ${it.transform}: ${t.message}")
            }
        }
    }

    static void iterateOverVariants(Project project, Closure cl) {
        Platform.getInterestingProjects(project).forEach { p ->
            try {
                p.android.applicationVariants.all { variant ->
                    cl(variant)
                }
            } catch (all) {
                project.logger.debug msg("Could not process variants for ${p}: ${all.message}")
            }
        }
    }

    static void iterateOverSpecificVariants(Project project, String buildType,
                                            String flavor, Closure cl) {
        iterateOverVariants project, { variant ->
            if ((variant.buildType.name == buildType) &&
                (!flavor || variant.flavorName == flavor)) {
                cl(variant)
            }
        }
    }

    static Set<String> getBuildTypes(Project project) {
        Set<String> bTypes = new HashSet<>()
        try {
            iterateOverVariants project, { variant ->
                bTypes.add(variant.buildType.name)
            }
        } catch (Throwable t) {
            // Just print the error message, without crashing. The
            // code above can fail but should only be used for warnings.
            t.printStackTrace()
        }
        return bTypes
    }

    static Set<String> getFlavors(Project project) {
        Set<String> pFlavors = new HashSet<>()
        try {
            iterateOverVariants project, { variant ->
                String fName = variant.flavorName
                if (fName && fName != "") {
                    pFlavors.add(fName)
                }
            }
        } catch (Throwable t) {
            // Just print the error message, without crashing. The
            // code above can fail but should only be used for warnings.
            t.printStackTrace()
        }
        return pFlavors
    }

    static boolean isMinifyEnabled(Project project, String buildType,
                                   boolean reportError) {
        boolean ret = false
        try {
            iterateOverVariants project, { variant ->
                // println msg("Examining: ${variant.buildType.name}")
                if (variant.buildType.name == buildType) {
                    ret = variant.buildType.minifyEnabled
                }
            }
        } catch (Throwable t) {
            project.logger.error msg("ERROR: failed to read 'minifyEnabled' property for build type '${buildType}': ${t.message}")
            // Just print the error message, without crashing. The
            // code above can fail but should only be used for warnings.
            t.printStackTrace()
        }
        if (reportError) {
            project.logger.warn msg("WARNING: build type '${buildType}' has no 'minifyEnabled' property.")
        }
        return ret
    }

    static String getCompileSdkVersion(Project project) {
        String compileSdkVersion = project.android.getCompileSdkVersion() as String
        if (compileSdkVersion) {
            project.logger.debug msg("compileSdkVersion = ${compileSdkVersion}")
        } else {
            project.logger.debug msg("Could not determine compileSdkVersion.")
        }
        return stripPrefix("android-", compileSdkVersion)
    }

    static String stripPrefix(String pre, String s) {
        return s.startsWith(pre) ? s.substring(pre.size()) : s
    }

    static String getShrinkResources(Project project, String buildType) {
        String ret = "false"
        iterateOverVariants project, { variant ->
            if (variant.buildType.name == buildType && variant.buildType.shrinkResources) {
                ret = "true"
            }
        }
        return ret
    }

    static Object getSigningConfiguration(Project project, String signingConfigName) {
        Class<?> c = AUtils.getInternalClass(project, 'com.android.build.gradle.internal.dsl.SigningConfig')
        Object sc = project.android.signingConfigs?.getByName(signingConfigName)
        return (sc && c?.isInstance(sc)) ? sc : null
    }

    /**
     * Signs a file using a named signing configuration defined in the Gradle
     * build file.
     *
     * @param project            the current project
     * @param signingConfigName  the name of the signing configuration
     * @param f                  the file to sign
     */
    static void signWithConfig(Project project, String signingConfigName, File f) {
        project.logger.info msg("Signing using configuration: ${signingConfigName}")
        Object sc = getSigningConfiguration(project, signingConfigName)
        if (sc) {
            project.logger.info msg("Found signing configuration")
            String androidSdkHome = System.getenv('ANDROID_SDK')
            if (!androidSdkHome) {
                project.logger.error "ERROR: set environment variable ANDROID_SDK."
                return
            }

            List<String> messages = []
            String signedFile = Signer.signWithApksigner(androidSdkHome, f.parentFile, f.name, messages, true, null, null, Boolean.toString(sc.v1SigningEnabled), Boolean.toString(sc.v2SigningEnabled), null, sc.storeFile.canonicalPath, sc.storePassword, sc.keyAlias, sc.keyPassword)
            messages.each { project.logger.debug msg(it) }

            println msg("Signed file: ${signedFile}")
        } else if (signingConfigName)
            project.logger.error msg("ERROR: could not read signing configuration ${signingConfigName}")
    }
}
