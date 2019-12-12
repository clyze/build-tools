package org.clyze.buck;

import java.nio.file.*;
import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.zeroturnaround.zip.*;

import static org.clyze.buck.BundlerUtil.*;

class Sources {

    public static Collection<SourceFile> getSources(Collection<String> sourceDirs,
                                                    boolean autodetectSources) {
        Collection<SourceFile> sourceFiles = new LinkedList<>();

        // Option 1: resolve sources in directories given in the command line.
        if (sourceDirs == null) {
            String nsErr = "Warning: No sources were explicitly given.";
            if (!autodetectSources)
                nsErr += " Consider using option: --" + Config.AUTODETECT_SOURCES_OPT;
            logError(nsErr);
        } else {
            println("Source directories:");
            sourceDirs.forEach(sd -> registerSourcesInDir(sd, sourceFiles));
        }

        // Option 2: autodetect sources (heuristic).
        if (autodetectSources)
            Sources.autodetectSourceDirs(sourceFiles);

        logDebug("Source files:");
        sourceFiles.forEach(fs -> logDebug(fs.toString()));
        return filterDuplicateSources(sourceFiles);
    }

    private static void registerSourcesInDir(String dirPath, Collection<SourceFile> sourceFiles) {
        println("Gathering sources in directory: " + dirPath);
        try {
            File dir = new File(dirPath);
            String dirPathCanonical = dir.getCanonicalPath();
            Files.walk(dir.toPath())
                .filter(Sources::isSourceFile)
                .forEach(p -> registerJavaSourceInDir(dirPathCanonical, p, sourceFiles));
        } catch (IOException ex) {
            logDebug("Could not process source directory: " + dirPath);
        }
    }

    private static void registerJavaSourceInDir(String dirPath, Path p, Collection<SourceFile> sourceFiles) {
        File f = p.toFile();
        try {
            String pStr = f.getCanonicalPath();
            if (pStr.startsWith(dirPath)) {
                String entry = pStr.substring(dirPath.length() + File.separator.length(), pStr.length());
                sourceFiles.add(new SourceFile(entry, f));
            }
        } catch (IOException ex) {
            logDebug("Could not process source " + p + ": " + ex.getMessage());
        }
    }

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
        logDebug("Delete " + targetArchive + ": " + del);
        String targetArchivePath;
        try {
            FileUtils.touch(targetArchive);
            targetArchivePath = targetArchive.getCanonicalPath();
            println("Packing sources to file: " + targetArchivePath);
            ZipUtil.pack(sourceFiles.toArray(new FileSource[0]), targetArchive);
        } catch (IOException ex) {
            logError("Error creating sources archive: " + targetArchive);
            ex.printStackTrace();
        }
    }

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
                .forEach(p -> registerJavaSourceDir(p, sourceFiles));
        } catch(IOException ex) {
            logError("Could not autodect source directories in current path: " + ex.getMessage());
        }
    }

    private static void registerJavaSourceDir(Path p, Collection<SourceFile> sourceFiles) {
        File sourceFile = p.toFile();
        String packageDir;
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
            String parentDir = sourceFile.getParent();
            if (packageName == null)
                packageDir = parentDir;
            else {
                String packagePath = packageName.replaceAll("\\.", File.separator);
                if (parentDir.endsWith(packagePath)) {
                    packageDir = parentDir.substring(0, parentDir.length() - packagePath.length());
                    // Separate modification here, so that absolute paths that
                    // coincide with package paths do not cause a crash.
                    if (packageDir.endsWith(File.separator))
                        packageDir = packageDir.substring(0, packageDir.length() - File.separator.length());
                } else {
                    logError("Warning: Could not determine package directory structure for file " + p + ", using parent directory " + parentDir);
                    packageDir = parentDir;
                }
            }
            String entry = (packageName == null ? "" : (packageName.replaceAll("\\.", "/") + "/")) + sourceFile.getName();
            final String DOT_SLASH = "." + File.separator;
            if (entry.startsWith(DOT_SLASH))
                entry = entry.substring(DOT_SLASH.length());
            println("Entry: " + entry + " -> " + sourceFile);
            sourceFiles.add(new SourceFile(entry, sourceFile));
        } catch (IOException ex) {
            logError("Could not register Java source in file: " + p);
            return;
        }
    }
}
