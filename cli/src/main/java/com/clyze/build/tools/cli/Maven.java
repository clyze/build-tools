package com.clyze.build.tools.cli;

import com.clyze.client.web.PostState;
import java.io.File;

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
        throw new UnsupportedOperationException("Maven post state is not yet supported.");
    }
}
