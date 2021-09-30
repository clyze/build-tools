package com.clyze.build.tools.cli;

import com.clyze.client.web.PostState;
import java.io.File;

public class Maven extends BuildTool {
    public Maven(File currentDir) {
        super(currentDir);
    }

    @Override
    public String getName() {
        return "maven";
    }

    @Override
    public PostState generatePostState(Config config) {
        throw new UnsupportedOperationException("Maven post state is not yet supported.");
    }
}
