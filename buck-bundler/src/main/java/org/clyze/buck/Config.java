package org.clyze.buck;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import org.apache.commons.cli.*;
import org.clyze.build.tools.Conventions;

class Config {
    private static final String DEFAULT_TRACE_FILE = "buck-out/log/build.trace";
    private static final String DEFAULT_JSON_DIR = "json";

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
    final String apk;
    final String javacPluginPath;
    final Collection<String> sourceDirs;
    final String proguard;

    Config(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(opts(), args);

        this.help = cmd.hasOption("h");
        this.post = cmd.hasOption("p");
        this.host = optValOrDefault(cmd, "host", Conventions.DEFAULT_HOST);
        this.port = Integer.parseInt(optValOrDefault(cmd, "port", Conventions.DEFAULT_PORT));
        this.username = optValOrDefault(cmd, "username", Conventions.DEFAULT_USERNAME);
        this.password = optValOrDefault(cmd, "password", Conventions.DEFAULT_PASSWORD);
        this.project = optValOrDefault(cmd, "project", Conventions.DEFAULT_PROJECT);
        this.profile = optValOrDefault(cmd, "profile", Conventions.DEFAULT_PROFILE);
        this.jsonDir = optValOrDefault(cmd, "json-dir", DEFAULT_JSON_DIR);
        this.traceFile = optValOrDefault(cmd, "trace", DEFAULT_TRACE_FILE);
        this.apk = optValOrDefault(cmd, "a", null);
        this.javacPluginPath = optValOrDefault(cmd, "j", null);
        this.proguard = optValOrDefault(cmd, "proguard-binary", "/proguard.jar");

        if (cmd.hasOption("s")) {
            this.sourceDirs = new HashSet<>();
            sourceDirs.addAll(Arrays.asList(cmd.getOptionValues("s")));
        } else
            this.sourceDirs = null;
    }

    private static Options opts() {
        Options opts = new Options();
        opts.addOption("a", "apk", true, "The APK file to bundle.");
        opts.addOption("j", "jcplugin", true, "The path to the javac plugin to use for Java sources.");
        opts.addOption("s", "source-dir", true, "Add source directory to bundle.");
        opts.addOption("p", "post", false, "Posts the bundle to the server.");
        opts.addOption("h", "help", false, "Show this help text.");
        opts.addOption(null, "host", true, "The server host (default: "+Conventions.DEFAULT_HOST+").");
        opts.addOption(null, "port", true, "The server port (default: "+Conventions.DEFAULT_PORT+").");
        opts.addOption(null, "username", true, "The username (default: "+Conventions.DEFAULT_USERNAME+").");
        opts.addOption(null, "password", true, "The username (default: "+Conventions.DEFAULT_PASSWORD+").");
        opts.addOption(null, "project", true, "The project (default: "+Conventions.DEFAULT_PROJECT+").");
        opts.addOption(null, "profile", true, "The profile (default: "+Conventions.DEFAULT_PROFILE+").");
        opts.addOption(null, "trace", true, "The Buck trace file (default: "+DEFAULT_TRACE_FILE+").");
        opts.addOption(null, "json-dir", true, "The JSON metadata output directory (default: "+DEFAULT_JSON_DIR+").");
        opts.addOption(null, "proguard-binary", true, "The location of the proguard binary.");
        return opts;
    }

    private static String optValOrDefault(CommandLine cmd, String id, String defaultValue) {
        return cmd.hasOption(id) ? cmd.getOptionValue(id) : defaultValue;
    }

    static void showUsage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("buck-bundler", opts());
    }

}
