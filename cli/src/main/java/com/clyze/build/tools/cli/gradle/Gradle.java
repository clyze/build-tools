package com.clyze.build.tools.cli.gradle;

import com.clyze.build.tools.Archiver;
import com.clyze.build.tools.Conventions;
import com.clyze.build.tools.Settings;
import com.clyze.build.tools.cli.BuildTool;
import com.clyze.build.tools.cli.Config;
import com.clyze.client.web.PostState;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class Gradle extends BuildTool {

    public Gradle(File currentDir, Config config) {
        super(currentDir, config);
    }

    @Override
    public String getName() {
        return "gradle";
    }

    public void populatePostState(PostState ps, Config config) {
        try {
            File buildLibs = Paths.get(currentDir.getCanonicalPath(), "build", "libs").toFile();
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
                        }
                    }
            }

            resolveDependencies(config, ps);

            boolean mk = new File(Conventions.CLYZE_SNAPSHOT_DIR).mkdirs();
            if (mk)
                System.err.println("Directory " + Conventions.CLYZE_SNAPSHOT_DIR + " created.");
            else
                System.err.println("Directory " + Conventions.CLYZE_SNAPSHOT_DIR + " already exists.");

            File srcDir = new File(currentDir, "src");
            File srcArchive = new File(Conventions.CLYZE_SNAPSHOT_DIR, "sources.zip");
            Archiver.zipTree(srcDir, srcArchive);
            System.out.println("Created source archive: " + srcArchive);
            ps.addFileInput(Conventions.SOURCE_INPUT_TAG, srcArchive.getCanonicalPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
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
