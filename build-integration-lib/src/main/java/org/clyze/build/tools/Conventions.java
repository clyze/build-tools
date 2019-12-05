package org.clyze.build.tools;

public class Conventions {
    public static final String CLUE_BUNDLE_DIR     = ".clue-bundle";
    public static final String METADATA_FILE       = "metadata.zip";
    public static final String CONFIGURATIONS_FILE = "configurations.zip";
    public static final String SOURCES_FILE        = "sources.jar";
    public static final String DEFAULT_HOST        = "localhost";
    public static final String DEFAULT_PORT        = "8001";
    public static final String DEFAULT_USERNAME    = "user";
    public static final String DEFAULT_PASSWORD    = "user123";
    public static final String DEFAULT_PROJECT     = "scrap";
    public static final String DEFAULT_PROFILE     = "apiTargetAndroid";
    public static final String BUNDLE_ID           = "bundle";

    public static final String getR8AndroidPlatform(String apiLevel) {
        return "android_" + apiLevel + "_stubs";
    }
}
