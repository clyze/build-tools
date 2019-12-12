package org.clyze.buck;

import java.io.File;
import org.zeroturnaround.zip.FileSource;

class SourceFile extends FileSource {
    private File file;

    SourceFile(String path, File file) {
        super(path, file);
        this.file = file;
    }

    public File getFile() {
        return file;
    }
}
