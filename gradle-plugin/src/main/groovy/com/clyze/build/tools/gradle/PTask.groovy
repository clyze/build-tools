package com.clyze.build.tools.gradle

import groovy.transform.CompileStatic

/**
 * The tasks created by the plugin.
 */
@CompileStatic
enum PTask {
    SCAVENGE('scavenge'),
    JCPLUGIN_ZIP('jcpluginZip'),
    SOURCES_JAR('sourcesJar'),
    POST_SNAPSHOT('postSnapshot'),
    REPLAY_POST('replay'),
    /** The task that gathers all optimization directive configurations. */
    CONFIGURATIONS('configurations'),
    REPACKAGE('repackage'),
    REPACKAGE_TEST('repackageTest'),
    /** The task that creates the snapshot for posting. */
    CREATE_SNAPSHOT('createSnapshot'),
    /** Android-only: the name of the Gradle plugin task that will
     *  generate the code input for the server. */
    ANDROID_CODE_ARCHIVE('codeApk'),

    final String name

    PTask(String name) {
        this.name = name
    }

    static boolean nonSourceTaskMatches(List<String> tasks) {
        Collection<String> names = values().findAll { it != SOURCES_JAR }.collect { it -> it.name }
        return tasks.find { String name -> names.contains(name) } != null
    }
}
