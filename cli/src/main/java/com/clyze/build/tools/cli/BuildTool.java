package com.clyze.build.tools.cli;

import com.clyze.build.tools.Archiver;
import com.clyze.build.tools.Conventions;
import com.clyze.build.tools.cli.buck.Buck;
import com.clyze.build.tools.cli.gradle.Gradle;
import com.clyze.build.tools.cli.maven.Maven;
import com.clyze.client.web.PostState;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Common functionality shared by all build tools.
 */
abstract public class BuildTool {

    /** The current working directory (where the CLI tool is invoked). */
    protected final File currentDir;
    /** Debugging mode. */
    protected final boolean debug;

    protected BuildTool(File currentDir, Config config) {
        this.currentDir = currentDir;
        this.debug = config.isDebug();
    }

    private static File getCurrentDir() {
        String currentPath = System.getProperty("user.dir");
        if (currentPath == null)
            throw new RuntimeException("ERROR: current directory is null.");
        return new File(currentPath);
    }

    static BuildTool detect(Config config) {
        File currentDir = getCurrentDir();
        if (!currentDir.exists())
            throw new RuntimeException("ERROR: current directory is invalid: " + currentDir);
        if ((new File(currentDir, "build.gradle")).exists())
            return new Gradle(currentDir, config);
        else if ((new File(currentDir, "pom.xml")).exists())
            return new Maven(currentDir, config);
        else if ((new File(currentDir, "build.xml")).exists())
            return new Ant(currentDir, config);
        else if ((new File(currentDir, "BUCK")).exists())
            return new Buck(currentDir, config);
        return null;
    }

    public static List<String> validValues() {
        return Arrays.asList("ant", "buck", "gradle", "maven");
    }

    public static BuildTool get(String name, Config config) {
        switch (name) {
            case "ant":
                return new Ant(getCurrentDir(), config);
            case "buck":
                return new Buck(getCurrentDir(), config);
            case "gradle":
                return new Gradle(getCurrentDir(), config);
            case "maven":
                return new Maven(getCurrentDir(), config);
            default:
                throw new RuntimeException("ERROR: unknown build tool: " + name);
        }
    }

    /** The name of the build tool. */
    public abstract String getName();

    /**
     * Called to generate the PostState object to post to the server.
     * @param ps       the object to populate
     * @param config   the configuration to use
     */
    public abstract void populatePostState(PostState ps, Config config);

    protected void createSnapshotDir() {
        boolean mk = new File(Conventions.CLYZE_SNAPSHOT_DIR).mkdirs();
        if (mk)
            System.err.println("Directory " + Conventions.CLYZE_SNAPSHOT_DIR + " created.");
        else
            System.err.println("Directory " + Conventions.CLYZE_SNAPSHOT_DIR + " already exists.");
    }

    protected void gatherMavenStyleSources(PostState ps) throws IOException {
        File srcDir = new File(currentDir, "src");
        File srcArchive = new File(Conventions.CLYZE_SNAPSHOT_DIR, "sources.zip");
        gatherSources(ps, srcDir, srcArchive);
    }

    protected void gatherSources(PostState ps, File srcDir, File srcArchive) throws IOException {
        if (srcDir.exists() && srcDir.isDirectory()) {
            Archiver.zipTree(srcDir, srcArchive);
            System.out.println("Created source archive: " + srcArchive);
            ps.addFileInput(Conventions.SOURCE_INPUT_TAG, srcArchive.getCanonicalPath());
        }
    }

    protected boolean gatherCodeJarFromDir(PostState ps, File buildLibs) throws IOException {
        boolean foundFiles = false;
        if (buildLibs.exists()) {
            File[] files = buildLibs.listFiles();
            if (files != null)
                for (File file : files) {
                    String name = file.getName();
                    if (name.startsWith(currentDir.getName()) && name.endsWith(".jar")) {
                        String jarFile = file.getCanonicalPath();
                        if (debug)
                            System.out.println("Found code file: " + jarFile);
                        ps.addFileInput(Conventions.BINARY_INPUT_TAG, jarFile);
                        foundFiles = true;
                    }
                }
        }
        return foundFiles;
    }
}
