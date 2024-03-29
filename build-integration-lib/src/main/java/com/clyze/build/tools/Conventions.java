package com.clyze.build.tools;

import java.io.*;

/**
 * The conventions followed by the server.
 */
public class Conventions {
    /** Tool name. */
    public static final String TOOL_NAME           = "Clyze";
    /** The directory where snapshot contents are gethered for posting. */
    public static final String CLYZE_SNAPSHOT_DIR = ".clyze-snapshot";
    /** The name of the metadata archive to post. */
    public static final String METADATA_FILE       = "metadata.zip";
    /** The name of the configurations archive to post. */
    public static final String CONFIGURATIONS_FILE = "configurations.zip";
    /** The name of the sources archive to post. */
    public static final String SOURCES_FILE        = "sources.jar";
    /** The tag of "application" binary inputs expected by the server. */
    public static final String BINARY_INPUT_TAG    = "app";
    /** The tag of "library" binary inputs expected by the server. */
    public static final String LIBRARY_INPUT_TAG   = "lib";
    /** The tag of source inputs expected by the server. */
    public static final String SOURCE_INPUT_TAG    = "src";
    /** The JVM platform option expected by the server. */
    public static final String JVM_PLATFORM        = "jvm_platform";
    /** The Android platform option expected by the server. */
    public static final String ANDROID_PLATFORM    = "android_platform";
    /** The name of the CodeQL archive to post. */
    public static final String CODEQL_DB_FILE      = "codeql-db.zip";
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
    /** Default server base path. */
    public static final String DEFAULT_BASE_PATH   = "";
    /** Default (server) user. */
    public static final String DEFAULT_USERNAME    = "user";
    /** Default server project. */
    public static final String DEFAULT_PROJECT     = "project";
    /** Default server stack to use for posting Android code. */
    public static final String ANDROID_STACK       = "android";
    /** Default server profile to use for posting Android code (automated repackaging). */
    public static final String DEFAULT_ANDROID_CLYZE_PROFILE = "clyzeAndroid";
    /** Default server stack to use for posting Java (non-Android) code. */
    public static final String JVM_STACK           = "jvm";
    /** Default server profile to use for posting Java code (automated repackaging). */
    public static final String DEFAULT_JAVA_CLYZE_PROFILE = "clyzeJava";
    /** Default snapshot identifier. */
    public static final String SNAPSHOT_ID         = "snapshot";
    /** Server API version. */
    public static final String API_VERSION         = "1.0";
    /** Disabling rules file name. */
    public static final String DISABLING_RULES     = "disabling-rules.txt";
    /** Output configuration file name. */
    public static final String OUTPUT_RULES        = "output-configuration.txt";
    /** The tag of the Clyze configuration file. */
    public static final String CLYZE_RULES_TAG     = "CLYZE_CONFIG";

    /** Warning when conifguration rules could not be disabled. */
    public static final String COULD_NOT_DISABLE_RULES = msg("WARNING: could not disable configuration rules, generated app code may not be suitable for analysis.");

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
     * Returns a file containing special rules that help the plugin.
     *
     * @param dir          the directory that will contain the configuration
     *                     and (optionally) the "printconfiguration" output
     * @param disableOpt   generate "dont" rules that effectively turn
     *                     off shrinking and obfuscation
     * @param printConfig  print all rules via "printconfiguration" rule
     *
     * @return null if error, otherwise a special configuration object
     * that contains a temporary "file" path (to be deleted on JVM
     * exit) and an output "print" configuration path
     */
    public static SpecialConfiguration getSpecialConfiguration(File dir, boolean disableOpt, boolean printConfig) {
        try {
            SpecialConfiguration sc = new SpecialConfiguration();
            sc.file = new File(dir, DISABLING_RULES);
            try (FileWriter fw = new FileWriter(sc.file)) {
                if (disableOpt) {
                    fw.write("-dontshrink\n");
                    fw.write("-dontoptimize\n");
                    fw.write("-dontobfuscate\n");
                }
                if (printConfig) {
                    sc.outputRulesPath = new File(dir, OUTPUT_RULES).getCanonicalPath();
                    fw.write("-printconfiguration " + sc.outputRulesPath + "\n");
                }
            }
            return sc;
        } catch (IOException ex) {
            ex.printStackTrace();
            if (disableOpt)
                System.err.println(COULD_NOT_DISABLE_RULES);
            else if (printConfig)
                System.err.println(msg("ERROR: could not print configuration."));
            return null;
        }
    }

    public static class SpecialConfiguration {
        public File file;
        public String outputRulesPath;
    }

    /**
     * This is a utility class, no public constructor is needed (or
     * should appear in documentation).
     */
    private Conventions() {}
}
