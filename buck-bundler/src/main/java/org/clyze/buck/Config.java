package org.clyze.buck;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.apache.commons.cli.*;
import org.clyze.build.tools.Conventions;
import org.clyze.build.tools.Settings;

class Config {
    private static final String DEFAULT_TRACE_FILE = "buck-out/log/build.trace";
    private static final String DEFAULT_JSON_DIR = "json";

    public static final String AUTODETECT_SOURCES_OPT = "autodetect-sources";
    private static final String PROGUARD_BINARY_OPT = "proguard-binary";

    final boolean help;
    final boolean post;
    final int port;
    final String username;
    final String password;
    final String project;
    final String profile;
    final String jsonDir;
    final String traceFile;
    final String host;
    final String javacPluginPath;
    final List<String> codeFiles;
    final Collection<String> sourceDirs;
    final List<String> configurations;
    final String proguard;
    final boolean autodetectSources;

    Config(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(opts(), args);

        this.help = cmd.hasOption("h");
        this.post = cmd.hasOption("p");
        this.autodetectSources = cmd.hasOption(AUTODETECT_SOURCES_OPT);
        this.host = optValOrDefault(cmd, "host", Conventions.DEFAULT_HOST);
        this.port = Integer.parseInt(optValOrDefault(cmd, "port", getDefaultPort()));
        this.username = optValOrDefault(cmd, "username", Conventions.DEFAULT_USERNAME);
        this.password = optValOrDefault(cmd, "password", Conventions.DEFAULT_PASSWORD);
        this.project = optValOrDefault(cmd, "project", Conventions.DEFAULT_PROJECT);
        this.profile = optValOrDefault(cmd, "profile", Conventions.DEFAULT_PROFILE);
        this.jsonDir = optValOrDefault(cmd, "json-dir", DEFAULT_JSON_DIR);
        this.traceFile = optValOrDefault(cmd, "trace", DEFAULT_TRACE_FILE);
        this.javacPluginPath = optValOrDefault(cmd, "j", null);
        this.proguard = optValOrDefault(cmd, PROGUARD_BINARY_OPT, "/proguard.jar");

        this.sourceDirs = optVals(cmd, "s");
        this.codeFiles = optVals(cmd, "c");
        this.configurations = optVals(cmd, "configuration");
    }

    private static Options opts() {
        Options opts = new Options();
        String SOURCE_DIR_S = "s";
        String SOURCE_DIR_L = "source-dir";

        opts.addOption("c", "code", true, "An appliction code file to bundle (e.g., a file in APK format).");
        opts.addOption("j", "jcplugin", true, "The path to the javac plugin to use for Java sources.");
        opts.addOption(SOURCE_DIR_S, SOURCE_DIR_L, true, "Add source directory to bundle.");
        opts.addOption("p", "post", false, "Posts the bundle to the server.");
        opts.addOption("h", "help", false, "Show this help text.");
        opts.addOption(null, "host", true, "The server host (default: "+Conventions.DEFAULT_HOST+").");
        opts.addOption(null, "port", true, "The server port (default: "+getDefaultPort()+").");
        opts.addOption(null, "username", true, "The username (default: "+Conventions.DEFAULT_USERNAME+").");
        opts.addOption(null, "password", true, "The username (default: "+Conventions.DEFAULT_PASSWORD+").");
        opts.addOption(null, "project", true, "The project (default: "+Conventions.DEFAULT_PROJECT+").");
        opts.addOption(null, "profile", true, "The profile (default: "+Conventions.DEFAULT_PROFILE+").");
        opts.addOption(null, "trace", true, "The Buck trace file (default: "+DEFAULT_TRACE_FILE+").");
        opts.addOption(null, "json-dir", true, "The JSON metadata output directory (default: "+DEFAULT_JSON_DIR+").");
        opts.addOption(null, PROGUARD_BINARY_OPT, true, "The location of the proguard binary.");
        opts.addOption(null, "configuration", true, "The configuration file(s) to use. If not specified, the trace file will be read to autodetect configuration (via option '"+PROGUARD_BINARY_OPT+"').");
        opts.addOption(null, AUTODETECT_SOURCES_OPT, false, "Attempt to automatically detect source directories. Companion to options '" + SOURCE_DIR_S + "'/'" + SOURCE_DIR_L + "'");
        return opts;
    }

    private static String optValOrDefault(CommandLine cmd, String id, String defaultValue) {
        return cmd.hasOption(id) ? cmd.getOptionValue(id) : defaultValue;
    }

    private static List<String> optVals(CommandLine cmd, String id) {
        return cmd.hasOption(id) ? Arrays.asList(cmd.getOptionValues(id)) : null;
    }

    static void showUsage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("buck-bundler", opts());
    }

    private static String getDefaultPort() {
        String port = Settings.getDefaultPort();
        if (port != null)
            return port;
        return Conventions.DEFAULT_PORT;
    }
}
