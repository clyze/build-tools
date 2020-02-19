package org.clyze.build.tools;

/**
 * The conventions followed by the server.
 */
public class Conventions {
    /** Tool name. */
    public static final String TOOL_NAME           = "Clyze";
    /** The directory where bundle contents are gethered for posting. */
    public static final String CLUE_BUNDLE_DIR     = ".clue-bundle";
    /** The name of the metadata archive to post. */
    public static final String METADATA_FILE       = "metadata.zip";
    /** The name of the configurations archive to post. */
    public static final String CONFIGURATIONS_FILE = "configurations.zip";
    /** The name of the sources archive to post. */
    public static final String SOURCES_FILE        = "sources.jar";
    /** The name of the test code directory to use for gathering code. */
    public static final String TEST_CODE_DIR       = "test-code";
    /** The name of the code archive to post (for optimization). */
    public static final String TEST_CODE_PRE_JAR   = "-pre.jar";
    /** The name of the code archive to receive (after optimization). */
    public static final String TEST_CODE_POST_JAR  = "-post.jar";
    /** Default server host name. */
    public static final String DEFAULT_HOST        = "localhost";
    /** Default server port. */
    public static final String DEFAULT_PORT        = "8001";
    /** Default (server) user. */
    public static final String DEFAULT_USERNAME    = "user";
    /** Default (server) user password. */
    public static final String DEFAULT_PASSWORD    = "user123";
    /** Default server project. */
    public static final String DEFAULT_PROJECT     = "scrap";
    /** Default server profile to use for posing. */
    public static final String DEFAULT_PROFILE     = "apiTargetAndroid";
    /** Default bundle identifier. */
    public static final String BUNDLE_ID           = "bundle";
    /** Default file to record metadata when posting a bundle. */
    public static final String POST_METADATA       = "post-metadata.txt";

    /**
     * Given an API level, compute the platform name that must be
     * given to the platform manager to resolve a platform.
     *
     * @param apiLevel   the API level
     * @return           a platform identifier
     */
    public static String getR8AndroidPlatform(String apiLevel) {
        return "android_" + apiLevel + "_stubs";
    }

    /**
     * Helper method to prefix messages with the tool name.
     *
     * @param s    a message
     * @return     message line
     */
    public static String msg(String s) {
        return "[" + TOOL_NAME + "] " + s;
    }

    /**
     * This is a utility class, no public constructor is needed (or
     * should appear in documentation).
     */
    private Conventions() {}
}
