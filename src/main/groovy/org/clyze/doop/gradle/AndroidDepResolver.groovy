package org.clyze.doop.gradle

import org.gradle.api.Project
import static org.clyze.doop.gradle.AndroidPlatform.*

class AndroidDepResolver {

    private Project project

    // Some dependencies are ignored.
    static final List ignoredGroups = [
        "com.android.support.test.espresso",
        "junit"
    ]

    // Group of Android dependencies that are resolved locally, via
    // the installed Android SDK.
    static final List localAndroidDeps = [
        "com.android.support",
        "com.android.support.constraint"
    ]

    public AndroidDepResolver(Project p) {
        project = p
    }

    public Set<String> resolveDependency(String group, String name, String version) {
        if (ignoredGroups.contains(group)) {
            println "Ignoring dependency group: ${group}"
            return null
        }

        Set<String> ret = [] as Set
        String extDepsDir = getExtDepsDir()
        String depDir = "${extDepsDir}/${group}/${name}/${version}"
        String classesJar = "${depDir}/classes.jar"
        String pom = "${depDir}/${name}-${version}.pom"

        // If the dependency exists, use it.
        if ((new File(classesJar)).exists()) {
            println "Using dependency ${group}:${name}:${version}: ${classesJar}"
        } else {
            // Otherwise, resolve the dependency.
            try {
                // Generate subdirectory to contain the dependency.
                (new File(depDir)).mkdirs()

                if (localAndroidDeps.contains(group)) {
                    resolveAndroidDep(depDir, group, name, version, classesJar, pomLocal)
                } else {
                    // TODO: pom.xml for external dependencies
                    resolveExtDep(depDir, group, name, version, classesJar)
                }
            } catch (Exception ex) {
                ex.printStackTrace()
                throwRuntimeException("AndroidPlatform: Cannot resolve dependency ${group}:${name}:${version}")
            }
        }

        ret << classesJar

        // throwRuntimeException("TODO: use ${pom}")
        // TODO: resolveAndroid/ExtDep should also download the pom.xml (and assume that it is empty when not found, showing just a warning)
        // ret << getPomDependencies(project, "path/of/pom.xml")
        // TODO: read .pom file of dependency
        if ((new File(pom)).exists()) {
            println "Reading ${pom}..."
            def xml = new XmlSlurper().parse(new File(pom))
            xml.dependencies.children().each { dep ->
                String scope = dep?.scope
                if (scope == "compile") {
                    println "Recursively resolving dependency: ${dep.artifactId}"
                    ret.addAll(resolveDependency(dep.groupId.text(), dep.artifactId.text(), dep.version.text()))
                } else {
                    println "Ignoring ${scope} dependency: ${dep.artifactId}"
                }
            }
        } else {
            println "Warning: no pom file found for dependency: ${name}"
        }
        return ret

    }
    
    // Decompress AAR and find its classes.jar.
    private void unpackClassesJarFromAAR(File localAAR, String classesJar) {
        boolean classesJarFound = false
        def zipFile = new java.util.zip.ZipFile(localAAR)
        zipFile.entries().each {
            if (it.getName() == 'classes.jar') {
                File cj = new File(classesJar)
                cj.newOutputStream() << zipFile.getInputStream(it)
                println "Resolved dependency: ${classesJar}"
                classesJarFound = true
            }
        }
        if (!classesJarFound) {
            String aarPath = localAAR.getCanonicalPath()
            throwRuntimeException("No classes.jar found in ${aarPath}")
        }
    }

    // Resolves an Android dependency by finding its .aar/.jar in the
    // local Android SDK installation. Parameter 'classesJar' is the
    // name of the .jar file that will contain the classes of the
    // dependency after this method finishes.
    private void resolveAndroidDep(String depDir, String group, String name,
                                   String version, String classesJar, String pom) {
        String sdkHome = findSDK(project)
        String groupPath = group.replaceAll('\\.', '/')

        // Possible locations of .aar/.jar archives in the local
        // Android SDK installation.
        String path1 = "${sdkHome}/extras/android/m2repository/${groupPath}/${name}/${version}"
        String path2 = "${sdkHome}/extras/m2repository/${groupPath}/${name}/${version}"
        String path3 = "${sdkHome}/extras/android/m2repository/${groupPath}/${name}/${version}"
        String path4 = "${sdkHome}/extras/m2repository/${groupPath}/${name}/${version}"

        String pomPath = null
        File aarPath1 = new File("${path1}/${name}-${version}.aar")
        File aarPath2 = new File("${path2}/${name}-${version}.aar")
        String jarPath3 = "${path3}/${name}-${version}.jar"
        String jarPath4 = "${path4}/${name}-${version}.jar"

        if (aarPath1.exists()) {
            unpackClassesJarFromAAR(aarPath1, classesJar)
            pomPath = path1
        } else if (aarPath2.exists()) {
            unpackClassesJarFromAAR(aarPath2, classesJar)
            pomPath = path2
        } else if ((new File(jarPath3)).exists()) {
            copyFile(jarPath3, classesJar)
            pomPath = path3
        } else if ((new File(jarPath4)).exists()) {
            copyFile(jarPath4, classesJar)
            pomPath = path4
        } else {
            throwRuntimeException("Cannot find Android dependency: ${group}:${name}:${version}, tried: ${aarPath1}, ${aarPath2}, ${jarPath3}")
        }
        copyFile("${pomPath}/${name}-${version}.pom", pom)
        println "Resolved Android artifact ${group}:${name}:${version}"
    }

    private static void copyFile(String src, String dst) {
        File srcFile = new File(src)
        if (srcFile.exists()) {
            println "Copying ${src} -> ${dst}"
            (new File(dst)).newOutputStream() << srcFile.newInputStream()
        } else {
            throwRuntimeException("File to copy does not exist: ${src}")
        }
    }

    // TODO: .pom handling.
    private void resolveExtDep(String depDir, String group, String name,
                               String version, String classesJar) {

        // Download AAR file.
        File localAAR = new File("${depDir}/${name}-${version}.aar")
        String aarURL = genMavenURL(group, name, version)
        println "Downloading ${aarURL}..."
        localAAR.newOutputStream() << new URL(aarURL).openStream()

        unpackClassesJarFromAAR(localAAR, classesJar)
    }

    private static String genMavenURL(String group, String name, String version) {
        String groupPath = group.replaceAll('\\.', '/')
        return "http://repo1.maven.org/maven2/${groupPath}/${name}/${version}/${name}-${version}.aar"
    }

    private String getExtDepsDir() {
        String subprojectName = getSubprojectName(project.extensions.doop)
        String dirName = "${project.rootDir}/${subprojectName}/build/extdeps"
        File extDepsDir = new File(dirName)
        if (!extDepsDir.exists()) {
            extDepsDir.mkdir()
        }
        return dirName
    }

}
