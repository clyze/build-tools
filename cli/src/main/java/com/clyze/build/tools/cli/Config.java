package com.clyze.build.tools.cli;

import com.clyze.build.tools.Settings;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.commons.cli.*;
import com.clyze.build.tools.Conventions;
import com.clyze.client.web.PostOptions;

public class Config {
    private static final String DEFAULT_STACK = Conventions.JVM_STACK;
    public static final String DEFAULT_JAVA_PLATFORM = "java_8";
    public static final String DEFAULT_ANDROID_PLATFORM = "android_25_fulljars";
    public static final String DEFAULT_BASE_PATH = "/clue";

    private static final String BUCK_DEFAULT_TRACE_FILE = "buck-out/log/build.trace";
    private static final String DEFAULT_JSON_DIR = "json";

    public static final String OPT_AUTODETECT_SOURCES = "autodetect-sources";
    private static final String OPT_PROGUARD_BINARY = "proguard-binary";
    public static final String OPT_BUILD_TOOL = "build-tool";
    private static final String OPT_PLATFORM = "platform";
    private static final String OPT_STACK = "stack";
    private static final String OPT_DRY = "dry";
    private static final String OPT_BASE_PATH = "server-base-path";
    private static final String OPT_DEP_SOURCES = "include-dep-sources";

    final boolean help;
    final boolean debug;
    final boolean includeDepSources;
    final String buildTool;
    final String platform;
    final String jsonDir;
    final String traceFile;
    final String javacPluginPath;
    final List<String> codeFiles;
    final Collection<String> sourceDirs;
    final List<String> configurations;
    final String proguard;
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
        this.javacPluginPath = optValOrDefault(cmd, "j", null);
        this.proguard = optValOrDefault(cmd, OPT_PROGUARD_BINARY, "/proguard.jar");
        this.sourceDirs = optValsOrDefault(cmd, "s", null);
        this.codeFiles = optValsOrDefault(cmd, "c", null);
        this.configurations = optValsOrDefault(cmd, "configuration", null);

        // Set post options.
        this.postOptions.host = optValOrDefault(cmd, "host", Conventions.DEFAULT_HOST);
        this.postOptions.port = Integer.parseInt(optValOrDefault(cmd, "port", getDefaultPort()));
        this.postOptions.basePath = optValOrDefault(cmd, OPT_BASE_PATH, DEFAULT_BASE_PATH);
        this.postOptions.username = optValOrDefault(cmd, "username", Conventions.DEFAULT_USERNAME);
        this.postOptions.password = optValOrDefault(cmd, "password", Conventions.DEFAULT_PASSWORD);
        this.postOptions.project = optValOrDefault(cmd, "project", Conventions.DEFAULT_PROJECT);
        this.postOptions.stacks = optValsOrDefault(cmd, OPT_STACK, Collections.singletonList(DEFAULT_STACK));
        this.postOptions.dry = cmd.hasOption(OPT_DRY);
    }

    private static Options createOptions() {
        Options opts = new Options();
        String SOURCE_DIR_S = "s";
        String SOURCE_DIR_L = "source-dir";

        opts.addOption("c", "code", true, "An application code file to include (e.g., a file in APK format).");
        opts.addOption("j", "jcplugin", true, "The path to the javac plugin to use for Java sources.");
        opts.addOption(SOURCE_DIR_S, SOURCE_DIR_L, true, "Add source directory to bundle.");
        opts.addOption("p", "post", false, "Posts the bundle to the server.");
        opts.addOption("h", "help", false, "Show this help text.");
        opts.addOption(null, "host", true, "The server host (default: "+Conventions.DEFAULT_HOST+").");
        opts.addOption(null, "port", true, "The server port (default: "+getDefaultPort()+").");
        opts.addOption(null, "username", true, "The username (default: "+Conventions.DEFAULT_USERNAME+").");
        opts.addOption(null, "password", true, "The username (default: "+Conventions.DEFAULT_PASSWORD+").");
        opts.addOption(null, "project", true, "The project (default: "+Conventions.DEFAULT_PROJECT+").");
        opts.addOption(null, OPT_STACK, true, "The stack (default: " + DEFAULT_STACK + ", valid values: ["+Conventions.ANDROID_STACK+", "+Conventions.JVM_STACK+"].");
        opts.addOption(null, "trace", true, "(Buck) The Buck trace file (default: "+ BUCK_DEFAULT_TRACE_FILE +").");
        opts.addOption(null, "json-dir", true, "The JSON metadata output directory (default: "+DEFAULT_JSON_DIR+").");
        opts.addOption(null, OPT_PROGUARD_BINARY, true, "(Buck) The location of the proguard binary.");
        opts.addOption(null, "configuration", true, "(Buck) The configuration file(s) to use. If not specified, the trace file will be read to autodetect configuration (via option '"+ OPT_PROGUARD_BINARY +"').");
        opts.addOption(null, OPT_AUTODETECT_SOURCES, false, "Attempt to automatically detect source directories. Companion to options '" + SOURCE_DIR_S + "' and '" + SOURCE_DIR_L + "'.");
        opts.addOption(new Option(null, "debug", false, "Enable debug mode."));
        opts.addOption(new Option(null, OPT_DRY, false, "Enable dry mode."));
        opts.addOption(new Option(null, OPT_DEP_SOURCES, false, "Include sources from dependencies."));

        Option buildToolOpt = new Option("b", OPT_BUILD_TOOL, true, "The build tool to use. Valid values: " + BuildTool.validValues());
        buildToolOpt.setArgName("TOOL");
        opts.addOption(buildToolOpt);

        Option platformOpt = new Option("p", OPT_PLATFORM, true, "The Java platform to use. Valid values: java_8, java_11. Default: " + DEFAULT_JAVA_PLATFORM);
        platformOpt.setArgName("PLATFORM");
        opts.addOption(platformOpt);

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
}
