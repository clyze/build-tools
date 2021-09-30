package com.clyze.build.tools.cli.buck;

import java.io.File;

class BuildMetadataConf {
    final String metadata;
    final File configuration;

    BuildMetadataConf(String metadata, File configuration) {
        this.metadata = metadata;
        this.configuration = configuration;
    }
}
