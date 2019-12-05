package org.clyze.buck;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.clyze.build.tools.Conventions;
import org.clyze.build.tools.JcPlugin;
import org.clyze.utils.JHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;

class BundlerMain {

    public static void main(String[] args) throws ParseException {
        System.out.println("Buck bundler main.");

        System.out.println("Using bundle directory: " + Conventions.CLUE_BUNDLE_DIR);
        new File(Conventions.CLUE_BUNDLE_DIR).mkdirs();

        Options opts = new Options();
        opts.addOption("a", "apk", true, "The APK file to bundle.");
        opts.addOption("j", "jcplugin", true, "The path to the javac plugin to use for Java sources.");
        opts.addOption("s", "source-dir", true, "Add source directory to bundle.");
        opts.addOption("p", "post", false, "Posts the bundle to the server.");

        CommandLineParser parser = new PosixParser();
        CommandLine cmd = parser.parse(opts, args);
        String apk;
        if (cmd.hasOption("a")) {
            apk = cmd.getOptionValue("a");
            System.out.println("APK: " + apk);
        } else {
            System.err.println("Error: No APK was given.");
            showUsage(opts);
            return;
        }
        if (cmd.hasOption("j")) {
            String javacPluginPath = cmd.getOptionValue("j");
            System.out.println("Using javac plugin in path: " + javacPluginPath);
        } else {
            String javacPlugin = JcPlugin.getJcPluginArtifact();
            System.out.println("Using javacPlugin artifact: " + javacPlugin);
        }

        Collection<String> sourceDirs = new HashSet<>();
        if (cmd.hasOption("s")) {
            for (String sourceDir: cmd.getOptionValues("s")) {
                System.out.println("Adding source directory: " + sourceDir);
                sourceDirs.add(sourceDir);
            }
        } else {
            System.err.println("Warning: No sources were given.");
        }

        gatherApk(apk);
        gatherSources(sourceDirs);
        gatherMetadataAndConfigurations();
    }

    private static void showUsage(Options opts) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("buck-bundler", opts);
    }

    /**
     * Copies the APK to the bundle directory.
     *
     * @param apk  the path to the APK archive
     */
    private static void gatherApk(String apk) {
        File target = new File(Conventions.CLUE_BUNDLE_DIR, apk);
        try {
            Files.copy(Paths.get(apk), target.toPath());
        } catch (IOException ex) {
            System.err.println("Failed to copy '" + apk + "' to '" + target + "'");
            ex.printStackTrace();
        }
    }

    private static void gatherSources(Collection<String> sourceDirs) {
        System.out.println("Gathering sources...");
        File sourcesJar = new File(Conventions.CLUE_BUNDLE_DIR, Conventions.SOURCES_FILE);
        sourcesJar.delete();
        String sourcesJarPath;
        try {
            FileUtils.touch(sourcesJar);
            sourcesJarPath = sourcesJar.getCanonicalPath();
        } catch (IOException ex) {
            System.err.println("Error creating sources JAR file: " + sourcesJar);
            ex.printStackTrace();
            return;
        }
        for (String sourceDir : sourceDirs) {
            System.out.println("Reading source directory: " + sourceDir);
            // TODO: this does not handle repeating entries (which will pause
            //  execution to prompt the user in the console and thus get stuck).
            String[] args = new String[] {"jar", "-uf", sourcesJarPath, "-C", sourceDir, "."};
            try {
                JHelper.runWithOutput(args, "JAR");
            } catch (IOException ex) {
                System.err.println("Error adding sources directory contents (" + sourceDir + ") to JAR file '" + sourcesJarPath + "'");
                ex.printStackTrace();
            }
        }
    }

    private static void gatherMetadataAndConfigurations() {
        System.out.println("Gathering metadata and configurations...");
    }


}
