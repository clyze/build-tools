package com.clyze.build.tools.cli;

import com.clyze.client.web.PostState;
import java.io.File;

public class Ant extends BuildTool {
    public Ant(File currentDir, Config config) {
        super(currentDir, config);
    }

    @Override
    public String getName() {
        return "ant";
    }

    @Override
    public void populatePostState(PostState ps, Config config) {
        throw new UnsupportedOperationException("Ant post state is not yet supported.");
    }
}
