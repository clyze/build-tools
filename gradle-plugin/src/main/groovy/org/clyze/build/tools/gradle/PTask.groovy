package org.clyze.build.tools.gradle

import groovy.transform.CompileStatic

/**
 * The tasks created by the plugin.
 */
@CompileStatic
enum PTask {
    SCAVENGE('scavenge'),
    JCPLUGIN_ZIP('jcpluginZip'),
    SOURCES_JAR('sourcesJar'),
    POST_BUILD('postBuild'),
    REPLAY_POST('replay'),
    /** The task that gathers all optimization directive configurations. */
    CONFIGURATIONS('configurations'),
    REPACKAGE('repackage'),
    REPACKAGE_TEST('repackageTest'),
    /** The task that creates the build for posting. */
    CREATE_BUILD('createBuild'),
    /** Android-only: the name of the Gradle plugin task that will
     *  generate the code input for the server. */
    ANDROID_CODE_ARCHIVE('codeApk'),

    final String name

    PTask(String name) {
        this.name = name
    }
}
