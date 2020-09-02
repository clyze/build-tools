package com.clyze.build.tools.buck;

import java.nio.file.*;
import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.zeroturnaround.zip.*;

class Sources {

    /**
     * Main entry point, takes care of finding source files in the project.
     *
     * @param sourceDirs          the source directories to scan (can be null)
     * @param autodetectSources   if 'true', the current directory will be searched for sources
     * @return                    the source files found
     */
    public static Collection<SourceFile> getSources(Collection<String> sourceDirs,
                                                    boolean autodetectSources) {
        Collection<SourceFile> sourceFiles = new LinkedList<>();

        // Option 1: resolve sources in directories given in the command line.
        if (sourceDirs == null) {
            String nsErr = "WARNING: No sources were explicitly given.";
            if (!autodetectSources)
                nsErr += " Consider using option: --" + Config.AUTODETECT_SOURCES_OPT;
            Util.logError(nsErr);
        } else {
            Util.println("Source directories:");
            sourceDirs.forEach(sd -> registerSourcesInDir(sd, sourceFiles));
        }

        // Option 2: autodetect sources (heuristic).
        if (autodetectSources)
            Sources.autodetectSourceDirs(sourceFiles);

        Util.logDebug("Source files:");
        sourceFiles.forEach(fs -> Util.logDebug(fs.toString()));
        return filterDuplicateSources(sourceFiles);
    }

    private static void registerSourcesInDir(String dirPath, Collection<SourceFile> sourceFiles) {
        Util.println("Gathering sources in directory: " + dirPath);
        try {
            File dir = new File(dirPath);
            String dirPathCanonical = dir.getCanonicalPath();
            Files.walk(dir.toPath())
                .filter(Sources::isSourceFile)
                .forEach(p -> registerJavaSourceInDir(dirPathCanonical, p, sourceFiles));
        } catch (IOException ex) {
            Util.logDebug("Could not process source directory: " + dirPath);
        }
    }

    /**
     * Registers all Java sources found under a directory. A Maven-style layout
     * is assumed, i.e., class a.b.C should be in path "dirPath/a/b/C.java".
     *
     * @param dirPath      the (top) source directory
     * @param p            the path of the source file to register
     * @param sourceFiles  the collection of files to use for registering the source file
     */
    private static void registerJavaSourceInDir(String dirPath, Path p, Collection<SourceFile> sourceFiles) {
        File f = p.toFile();
        try {
            String pStr = f.getCanonicalPath();
            if (pStr.startsWith(dirPath)) {
                String entry = pStr.substring(dirPath.length() + File.separator.length());
                sourceFiles.add(new SourceFile(entry, f));
            }
        } catch (IOException ex) {
            Util.logDebug("Could not process source " + p + ": " + ex.getMessage());
        }
    }

    /**
     * Recognizes sources files. Currently, only Java files are supported.
     *
     * @param p    the path of the source file
     * @return     'true' if the file should be processed as a source file
     */
    private static boolean isSourceFile(Path p) {
        return Files.isRegularFile(p) && p.toString().endsWith(".java");
    }

    /**
     * Compresses a collection of files to a file in ZIP format.
     *
     * @param sourceFiles   the collection of input files
     * @param targetArchive the archive to create (if it already exists, it is overwritten)
     */
    public static void packSources(Collection<SourceFile> sourceFiles,
                                   File targetArchive) {
        boolean del = targetArchive.delete();
        Util.logDebug("Delete " + targetArchive + ": " + del);
        String targetArchivePath;
        try {
            FileUtils.touch(targetArchive);
            targetArchivePath = targetArchive.getCanonicalPath();
            Util.println("Packing sources to file: " + targetArchivePath);
            ZipUtil.pack(sourceFiles.toArray(new FileSource[0]), targetArchive);
        } catch (IOException ex) {
            Util.logError("Error creating sources archive: " + targetArchive);
            ex.printStackTrace();
        }
    }

    /**
     * Filters duplicate source file entries, i.e., those entries that would go
     * to the same path in the "sources" archive. Duplicate entries are merged
     * by keeping the last entry.
     *
     * @param sourceFiles  the input source file entries
     * @return             the filtered source file entries
     */
    private static Collection<SourceFile> filterDuplicateSources(Collection<SourceFile> sourceFiles) {
        Map<String, File> map = new HashMap<>();
        // Merging happens here: only the last file is remembered.
        sourceFiles.forEach(fs -> map.put(fs.getPath(), fs.getFile()));
        Collection<SourceFile> ret = new LinkedList<>();
        map.forEach((String p, File f) -> ret.add(new SourceFile(p, f)));
        return ret;
    }

    /**
     * Find all source files, starting from the current directory. The
     * files found are registered with an accompanying package prefix
     * (suitable for compression in ZIP/JAR format). Currently, only
     * .java files are supported.
     *
     * @param sourceFiles  a collection of files to use for registering a source file
     */
    private static void autodetectSourceDirs(Collection<SourceFile> sourceFiles) {
        try {
            Files.walk(Paths.get("."))
                .filter(Sources::isSourceFile)
                .forEach(p -> autoregisterSource(p, sourceFiles));
        } catch(IOException ex) {
            Util.logError("Could not autodect source directories in current path: " + ex.getMessage());
        }
    }

    /**
     * Registers a source file in the sources collection that will drive the
     * generation of the "sources" archive. This is a heuristic that tries to
     * find the "package" statement in the sources and reconstruct an
     * appropriate archive entry for the source file.
     *
     * @param p             the source file (supported langauges: Java)
     * @param sourceFiles  a collection of files to use for registering a source file
     */
    private static void autoregisterSource(Path p, Collection<SourceFile> sourceFiles) {
        File sourceFile = p.toFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(sourceFile))) {
            final String PACKAGE_PREFIX = "package ";
            String packageName = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(PACKAGE_PREFIX)) {
                    int semiIndex = line.indexOf(";");
                    if (semiIndex != -1)
                        packageName = line.substring(PACKAGE_PREFIX.length(), semiIndex).trim();
                }
            }
            String entry = (packageName == null ? "" : (packageName.replaceAll("\\.", "/") + "/")) + sourceFile.getName();
            final String DOT_SLASH = "." + File.separator;
            if (entry.startsWith(DOT_SLASH))
                entry = entry.substring(DOT_SLASH.length());
            Util.println("Entry: " + entry + " -> " + sourceFile);
            sourceFiles.add(new SourceFile(entry, sourceFile));
        } catch (IOException ex) {
            Util.logError("Could not register Java source in file: " + p);
        }
    }
}
