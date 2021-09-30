package com.clyze.build.tools.cli.buck;

import java.io.File;
import org.zeroturnaround.zip.FileSource;

class SourceFile extends FileSource {
    private final File file;

    SourceFile(String path, File file) {
        super(path, file);
        this.file = file;
    }

    public File getFile() {
        return file;
    }
}
