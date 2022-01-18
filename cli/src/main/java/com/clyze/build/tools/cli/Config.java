package com.clyze.build.tools.cli;

import com.clyze.client.web.AuthToken;
import com.clyze.client.web.PostOptions;
import com.clyze.build.tools.Conventions;
import com.clyze.build.tools.Settings;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.commons.cli.*;

/**
 * This class represents configuration settings (either from defaults or from
 * command-line options).
 */
public class Config {
    private static final String DEFAULT_STACK = Conventions.JVM_STACK;
    /** The default Java platform needed by the server for deep analysis. */
    public static final String DEFAULT_JAVA_PLATFORM = "java_8";
    /** The default Android platform needed by the server for deep analysis. */
    public static final String DEFAULT_ANDROID_PLATFORM = "android_25_fulljars";

    private static final String BUCK_DEFAULT_TRACE_FILE = "buck-out/log/build.trace";
    private static final String DEFAULT_JSON_DIR = "json";

    public static final String OPT_AUTODETECT_SOURCES = "autodetect-sources";
    private static final String OPT_PROGUARD_BINARY = "proguard-binary";
    public static final String OPT_BUILD_TOOL = "build-tool";
    private static final String OPT_PLATFORM = "platform";
    private static final String OPT_STACK = "stack";
    private static final String OPT_DRY = "dry";
    private static final String OPT_SERVER = "server";
    private static final String OPT_PORT = "port";
    private static final String OPT_BASE_PATH = "server-base-path";
    private static final String OPT_DEP_SOURCES = "include-dep-sources";
    private static final String OPT_USERNAME = "user";
    private static final String OPT_TOKEN = "api-key";
    private static final String OPT_CACHE_DIR = "cache-dir";
    private static final String OPT_DIR = "dir";
    private static final String OPT_PUBLIC = "public";

    final boolean help;
    final boolean debug;
    final boolean includeDepSources;
    final String buildTool;
    final String platform;
    final String jsonDir;
    final String traceFile;
    final List<String> codeFiles;
    final Collection<String> sourceDirs;
    final String cacheDir;
    final String currentDir;
    final boolean makePublic;

    /**
     * Returns the directory where the created snapshot will be cached.
     * @return   the directory (or null when no directory set)
     */
    public File getCacheDir() {
        if (cacheDir != null)
            return new File(cacheDir);
        return null;
    }

    public Collection<String> getSourceDirs() {
        return sourceDirs;
    }

    public List<String> getConfigurations() {
        return configurations;
    }

    final List<String> configurations;

    public String getJsonDir() {
        return jsonDir;
    }

    public String getTraceFile() {
        return traceFile;
    }

    public String getProguard() {
        return proguard;
    }

    final String proguard;

    public boolean isAutodetectSources() {
        return autodetectSources;
    }

    final boolean autodetectSources;
    final Options options;
    final PostOptions postOptions = new PostOptions();

    Config(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        this.options = createOptions();
        CommandLine cmd = parser.parse(options, args);

        this.help = cmd.hasOption("h");
        this.debug = cmd.hasOption("debug");
        this.includeDepSources = cmd.hasOption(OPT_DEP_SOURCES);
        this.buildTool = optValOrDefault(cmd, OPT_BUILD_TOOL, null);
        this.platform = optValOrDefault(cmd, OPT_PLATFORM, null);
        this.autodetectSources = cmd.hasOption(OPT_AUTODETECT_SOURCES);
        this.jsonDir = optValOrDefault(cmd, "json-dir", DEFAULT_JSON_DIR);
        this.traceFile = optValOrDefault(cmd, "trace", BUCK_DEFAULT_TRACE_FILE);
        this.proguard = optValOrDefault(cmd, OPT_PROGUARD_BINARY, "/proguard.jar");
        this.sourceDirs = optValsOrDefault(cmd, "s", null);
        this.codeFiles = optValsOrDefault(cmd, "c", null);
        this.configurations = optValsOrDefault(cmd, "configuration", null);
        this.cacheDir = optValOrDefault(cmd, OPT_CACHE_DIR, null);
        this.makePublic = cmd.hasOption(OPT_PUBLIC);

        // Set post options.
        this.postOptions.host = optValOrDefault(cmd, OPT_SERVER, Conventions.DEFAULT_HOST);
        String username = optValOrDefault(cmd, OPT_USERNAME, Conventions.DEFAULT_USERNAME);
        this.postOptions.owner = username;
        this.postOptions.authToken = new AuthToken(username, optValOrDefault(cmd, OPT_TOKEN, null));
        this.postOptions.project = optValOrDefault(cmd, "project", Conventions.DEFAULT_PROJECT);
        this.postOptions.stacks = optValsOrDefault(cmd, OPT_STACK, Collections.singletonList(DEFAULT_STACK));
        this.postOptions.dry = cmd.hasOption(OPT_DRY);
        this.currentDir = optValOrDefault(cmd, OPT_DIR, null);
    }

    private static Options createOptions() {
        Options opts = new Options();
        String SOURCE_DIR_S = "s";
        String SOURCE_DIR_L = "source-dir";

        Option codeOpt = new Option("c", "code", true, "An application code file to include (e.g., a file in APK format).");
        codeOpt.setArgName("FILE");
        opts.addOption(codeOpt);

        Option configOpt = new Option(null, "configuration", true, "(Buck) The configuration file(s) to use. If not specified, the trace file will be read to autodetect configuration (via option --"+ OPT_PROGUARD_BINARY +").");
        configOpt.setArgName("FILE");
        opts.addOption(configOpt);

        Option hostOpt = new Option(null, OPT_SERVER, true, "The server host (default: "+Conventions.DEFAULT_HOST+").");
        hostOpt.setArgName("HOST");
        opts.addOption(hostOpt);

        Option portOpt = new Option(null, OPT_PORT, true, "The server port (default: "+getDefaultPort()+").");
        portOpt.setArgName("PORT");
        opts.addOption(portOpt);

        Option serverBaseOpt = new Option(null, OPT_BASE_PATH, true, "The server host (default: '"+Conventions.DEFAULT_BASE_PATH+"').");
        serverBaseOpt.setArgName("BASE");
        opts.addOption(serverBaseOpt);

        Option jsonDirOpt = new Option(null, "json-dir", true, "The JSON metadata output directory (default: "+DEFAULT_JSON_DIR+").");
        jsonDirOpt.setArgName("DIR");
        opts.addOption(jsonDirOpt);

        Option sourceDirOpt = new Option(SOURCE_DIR_S, SOURCE_DIR_L, true, "Add source directory to process.");
        sourceDirOpt.setArgName("DIR");
        opts.addOption(sourceDirOpt);

        Option projectOpt = new Option(null, "project", true, "The project (default: "+Conventions.DEFAULT_PROJECT+").");
        projectOpt.setArgName("NAME");
        opts.addOption(projectOpt);

        Option stackOpt = new Option(null, OPT_STACK, true, "The stack (default: " + DEFAULT_STACK + ", valid values: ["+Conventions.ANDROID_STACK+", "+Conventions.JVM_STACK+"].");
        stackOpt.setArgName("STACK");
        opts.addOption(stackOpt);

        Option traceOpt = new Option(null, "trace", true, "(Buck) The Buck trace file (default: "+ BUCK_DEFAULT_TRACE_FILE +").");
        traceOpt.setArgName("FILE");
        opts.addOption(traceOpt);

        Option userOpt = new Option(null, OPT_USERNAME, true, "The username (default: "+Conventions.DEFAULT_USERNAME+").");
        userOpt.setArgName("USERNAME");
        opts.addOption(userOpt);

        Option tokenOpt = new Option(null, OPT_TOKEN, true, "The API key to use for authentication.");
        tokenOpt.setArgName("TOKEN");
        opts.addOption(tokenOpt);

        Option cacheOpt = new Option(null, OPT_CACHE_DIR, true, "A directory to use for caching the snapshot before sending it to the server.");
        cacheOpt.setArgName("DIR");
        opts.addOption(cacheOpt);

        opts.addOption("p", "post", false, "Posts the bundle to the server.");
        opts.addOption("h", "help", false, "Show this help text.");
        opts.addOption(null, OPT_PROGUARD_BINARY, true, "(Buck) The location of the proguard binary.");
        opts.addOption(null, OPT_AUTODETECT_SOURCES, false, "(Buck) Attempt to automatically detect source directories. Companion to option --" + SOURCE_DIR_L + ".");
        opts.addOption(new Option(null, "debug", false, "Enable debug mode."));
        opts.addOption(new Option(null, OPT_DRY, false, "Enable dry mode."));
        opts.addOption(new Option(null, OPT_DEP_SOURCES, false, "Include sources from dependencies."));
        opts.addOption(null, OPT_PUBLIC, false, "If a new project is created, make it public.");

        Option buildToolOpt = new Option("b", OPT_BUILD_TOOL, true, "The build tool to use. Valid values: " + BuildTool.validValues());
        buildToolOpt.setArgName("TOOL");
        opts.addOption(buildToolOpt);

        Option platformOpt = new Option("p", OPT_PLATFORM, true, "The Java platform to use. Valid values: java_8, java_11. Default: " + DEFAULT_JAVA_PLATFORM);
        platformOpt.setArgName("PLATFORM");
        opts.addOption(platformOpt);

        Option dirOpt = new Option("d", OPT_DIR, true, "The project root directory. Default is current directory.");
        dirOpt.setArgName("DIR");
        opts.addOption(dirOpt);

        return opts;
    }

    private static String optValOrDefault(CommandLine cmd, String id, String defaultValue) {
        return cmd.hasOption(id) ? cmd.getOptionValue(id) : defaultValue;
    }

    private static List<String> optValsOrDefault(CommandLine cmd, String id, List<String> defaultVal) {
        return cmd.hasOption(id) ? Arrays.asList(cmd.getOptionValues(id)) : defaultVal;
    }

    private static String getDefaultPort() {
        String port = Settings.getDefaultPort();
        if (port != null)
            return port;
        return Conventions.DEFAULT_PORT;
    }

    public void printUsage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(100);
        formatter.printHelp("clyze-cli [OPTION]...", options);
    }

    /**
     * Returns the options that control posting snapshots to the server.
     * @return   an options object
     */
    public PostOptions getPostOptions() {
        return this.postOptions;
    }

    public String getPlatform() {
        return this.platform;
    }

    public boolean includesDepSources() {
        return this.includeDepSources;
    }

    public boolean isDebug() {
        return this.debug;
    }

    public List<String> getCodeFiles() {
        return this.codeFiles;
    }
}
