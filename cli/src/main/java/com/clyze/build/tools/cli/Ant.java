package com.clyze.build.tools.cli;

import com.clyze.client.web.PostState;
import java.io.File;

public class Ant extends BuildTool {
    public Ant(File currentDir) {
        super(currentDir);
    }

    @Override
    public String getName() {
        return "ant";
    }

    @Override
    public PostState generatePostState(Config config) {
        throw new UnsupportedOperationException("Ant post state is not yet supported.");
    }
}
