package com.clyze.build.tools.cli;

import com.clyze.build.tools.cli.gradle.Gradle;
import com.clyze.client.web.PostState;

import java.io.File;
import java.util.Arrays;
import java.util.List;

abstract public class BuildTool {

    protected final File currentDir;

    protected BuildTool(File currentDir) {
        this.currentDir = currentDir;
    }

    static File getCurrentDir() {
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
            return new Maven(currentDir);
        else if ((new File(currentDir, "build.xml")).exists())
            return new Ant(currentDir);
        else if ((new File(currentDir, "BUCK")).exists())
            return new Buck(currentDir);
        return null;
    }

    static List<String> validValues() {
        return Arrays.asList("ant", "buck", "gradle", "maven");
    }

    static BuildTool get(String name, Config config) {
        switch (name) {
            case "ant":
                return new Ant(getCurrentDir());
            case "buck":
                return new Buck(getCurrentDir());
            case "gradle":
                return new Gradle(getCurrentDir(), config);
            case "maven":
                return new Maven(getCurrentDir());
            default:
                throw new RuntimeException("ERROR: unknown build tool: " + name);
        }
    }

    /** The name of the build tool. */
    public abstract String getName();

    /**
     * Called to generate the PostState object to post to the server.
     * @param config   the configuration to use
     * @return         the object containing all snapshot state
     */
    public abstract PostState generatePostState(Config config);
}
