package com.clyze.build.tools.gradle

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath

import static com.clyze.build.tools.Conventions.msg

@CompileStatic
class AUtils {

    static boolean isAppCodeArtifact(String n) {
        return n.endsWith('.apk') || n.endsWith('.aab')
    }

    /** Get an internal class from the Android Gradle plugin.
     *
     * @param project the current project
     * @param s       the name of the class
     * @return        the Class object (or null if class was not found)
     */
    static Class getInternalClass(Project project, String s) {
        try {
            Class c = Class.forName(s)
            project.logger.debug msg("Using AGP internal class: ${s}")
            return c
        } catch (ClassNotFoundException ex) {
            project.logger.debug msg("WARNING: class ${s} not found in Android Gradle plugin: ${ex.message}")
            return null
        }
    }

    static List<URI> getAsURIs(ClassLoader cLoader) {
        ClassPath cp = ClasspathUtil.getClasspath(cLoader)
        return cp.getAsURIs()
    }

    static String stripPrefix(String pre, String s) {
        return s.startsWith(pre) ? s.substring(pre.size()) : s
    }
}
