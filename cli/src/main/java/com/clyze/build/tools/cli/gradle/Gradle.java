package com.clyze.build.tools.cli.gradle;

import com.clyze.build.tools.Settings;
import com.clyze.build.tools.cli.BuildTool;
import com.clyze.build.tools.cli.Config;
import com.clyze.client.web.PostState;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * Integration with the Gradle build system.
 */
public class Gradle extends BuildTool {

    public Gradle(File currentDir, Config config) {
        super(currentDir, config);
    }

    @Override
    public String getName() {
        return "gradle";
    }

    @Override
    public void populatePostState(PostState ps, Config config) throws IOException {
        gatherCodeJarFromDir(ps, Paths.get(currentDir.getCanonicalPath(), "build", "libs").toFile(), false);
        resolveDependencies(config, ps);
        gatherSourcesFromSrcDir(ps);
    }

    private void resolveDependencies(Config config, PostState ps) {
        String userHomeDir = Settings.getUserHomeDir();
        if (userHomeDir == null) {
            System.out.println("WARNING: no user home directory found, cannot resolve dependencies.");
            return;
        }

        GradleProject project = new GradleProject(currentDir, config, userHomeDir, ps);
        System.out.println("Analyzing dependencies...");
        project.resolveDependencies();
    }
}
