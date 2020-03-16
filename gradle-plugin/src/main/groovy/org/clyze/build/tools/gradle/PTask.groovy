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
    CREATE_BUNDLE('createBundle')

    final String name

    PTask(String name) {
        this.name = name
    }

    /**
     * Helper method to filter user-submitted tasks.
     *
     * @param  s   a task name
     * @return     true if the task looks like a plugin task
     */
    static boolean taskNameMatches(String s) {
        return values().any { s.endsWith(it.name) }
    }
}
