package com.clyze.build.tools.cli.ant;

import com.clyze.build.tools.cli.BuildTool;
import com.clyze.build.tools.cli.Config;
import com.clyze.client.web.PostState;
import java.io.File;
import java.io.IOException;

/**
 * Integration with the Ant build system.
 */
public class Ant extends BuildTool {
    public Ant(File currentDir, Config config) {
        super(currentDir, config);
    }

    @Override
    public String getName() {
        return "ant";
    }

    @Override
    public void populatePostState(PostState ps, Config config) throws IOException {
        String currentDirPath = currentDir.getCanonicalPath();
        gatherCodeFromTargetDir(ps, currentDirPath, true);
        gatherSourcesFromSrcDir(ps);
    }
}
