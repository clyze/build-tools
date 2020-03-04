package org.clyze.build.tools.gradle

/**
 * The names of the tasks created by the plugin.
 */
class Tasks {
    static final String SCAVENGE       = 'scavenge'
    static final String JCPLUGIN_ZIP   = 'jcpluginZip'
    static final String SOURCES_JAR    = 'sourcesJar'
    static final String POST_BUNDLE    = 'postBundle'
    static final String REPLAY_POST    = 'replay'
    /** The task that gathers all optimization directive configurations. */
    static final String CONFIGURATIONS = 'configurations'
    static final String REPACKAGE      = 'repackage'
    static final String REPACKAGE_TEST = 'repackageTest'
    /** The task that creates the bundle for posting. */
    static final String CREATE_BUNDLE  = 'createBundle'
}
