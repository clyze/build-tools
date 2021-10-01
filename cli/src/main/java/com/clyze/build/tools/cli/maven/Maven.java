package com.clyze.build.tools.cli.maven;

import com.clyze.build.tools.Archiver;
import com.clyze.build.tools.Conventions;
import com.clyze.build.tools.cli.BuildTool;
import com.clyze.build.tools.cli.Config;
import com.clyze.client.web.PostState;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * Integration with the Maven build system.
 */
public class Maven extends BuildTool {
    public Maven(File currentDir, Config config) {
        super(currentDir, config);
    }

    @Override
    public String getName() {
        return "maven";
    }

    @Override
    public void populatePostState(PostState ps, Config config) {
        createSnapshotDir();
        try {
            if (debug)
                System.out.println("Looking for code...");
            String currentDirPath = currentDir.getCanonicalPath();
            boolean jar = gatherCodeJarFromDir(ps, new File(currentDirPath, "target"));
            if (!jar) {
                if (debug)
                    System.out.println("No .jar found, looking for .class files...");
                File targetClassesDir = Paths.get(currentDirPath, "target", "classes").toFile();
                if (targetClassesDir.exists() && targetClassesDir.isDirectory()) {
                    File classesJar = getTmpJarFile("classes");
                    Archiver.zipTree(targetClassesDir, classesJar);
                    ps.addFileInput(Conventions.BINARY_INPUT_TAG, classesJar.getCanonicalPath());
                }
            }

            if (debug)
                System.out.println("Looking for sources...");
            gatherMavenStyleSources(ps);
            gatherGeneratedSources(ps, currentDirPath);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void gatherGeneratedSources(PostState ps, String currentDirPath) throws IOException {
        File generatedSourcesDir = Paths.get(currentDirPath, "target", "generated-sources").toFile();
        gatherSources(ps, generatedSourcesDir, getTmpJarFile("generated-sources"));
    }

    private File getTmpJarFile(String pre) throws IOException {
        File f = File.createTempFile(pre, ".jar");
        f.deleteOnExit();
        return f;
    }
}
