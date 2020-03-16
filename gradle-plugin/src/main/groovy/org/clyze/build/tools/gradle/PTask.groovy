package org.clyze.build.tools.gradle

import groovy.transform.TypeChecked

/**
 * The tasks created by the plugin.
 */
@TypeChecked
enum PTask {
    SCAVENGE('scavenge'),
    JCPLUGIN_ZIP('jcpluginZip'),
    SOURCES_JAR('sourcesJar'),
    POST_BUNDLE('postBundle'),
    REPLAY_POST('replay'),
    /** The task that gathers all optimization directive configurations. */
    CONFIGURATIONS('configurations'),
    REPACKAGE('repackage'),
    REPACKAGE_TEST('repackageTest'),
    /** The task that creates the bundle for posting. */
    CREATE_BUNDLE('createBundle'),
    /** Android-only: the name of the Gradle plugin task that will
     *  generate the code input for the server. */
    ANDROID_CODE_ARCHIVE('codeApk'),

    final String name

    PTask(String name) {
        this.name = name
    }
}
