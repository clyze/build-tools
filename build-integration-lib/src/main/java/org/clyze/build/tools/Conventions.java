package org.clyze.build.tools;

import java.io.*;

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
    public static final String DEFAULT_PROJECT     = "project";
    /** Default server profile to use for posting Android code. */
    public static final String DEFAULT_ANDROID_PROFILE = "proAndroid";
    /** Default server profile to use for posting Java (non-Android) code. */
    public static final String DEFAULT_JAVA_PROFILE= "clyzeJava";
    /** Default bundle identifier. */
    public static final String BUNDLE_ID           = "bundle";
    /** Server API version. */
    public static final String API_VERSION         = "1.0";
    /** Disabling rules file name. */
    public static final String DISABLING_RULES     = "disabling-rules.txt";
    /** Output configuration file name. */
    public static final String OUTPUT_RULES        = "output-configuration.txt";

    /** Warning when conifguration rules could not be disabled. */
    public static final String COULD_NOT_DISABLE_RULES = msg("WARNING: could not disable configuration rules, generated .apk may not be suitable for analysis.");

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
