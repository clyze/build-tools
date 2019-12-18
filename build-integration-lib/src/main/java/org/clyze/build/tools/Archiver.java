package org.clyze.build.tools;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.zeroturnaround.zip.*;

import static org.clyze.build.tools.Conventions.msg;

class Archiver {

    /**
     * Zips one or more directories as an archive, removing the
     * directory prefix from the entries.
     *
     * @param dirs         one or more directories
     * @param archive      the archive to create
     * @return             true if some individual file could not be added
     * @throws IOException when a major error occurred
     */
    public static boolean zipTree(Collection<File> dirs, File archive) throws IOException {
        Collection<ZipEntrySource> files = new LinkedList<>();

        boolean error = false;
        for (File dir : dirs) {
            String dirPath;
            try {
                dirPath = dir.getCanonicalPath();
            } catch (IOException ex) {
                System.err.println(msg("Could not process directory: " + dir));
                throw ex;
            }
            List<File> fs = Files.walk(dir.toPath()).filter(Files::isRegularFile).map(Path::toFile).collect(Collectors.toList());
            for (File f : fs) {
                try {
                    String fPath = f.getCanonicalPath();
                    if (fPath.startsWith(dirPath))
                        fPath = fPath.substring(dirPath.length());
                    if (fPath.startsWith(File.separator))
                        fPath = fPath.substring(File.separator.length());
                    files.add(new FileSource(fPath, f));
                } catch (IOException ex) {
                    System.err.println("Could not process file " + f + ": " + ex.getMessage());
                    error = true;
                }
            }
        }
        ZipUtil.pack(files.toArray(new ZipEntrySource[0]), archive);
        return error;
    }
}
