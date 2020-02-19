package org.clyze.build.tools;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.zeroturnaround.zip.*;

import static org.clyze.build.tools.Conventions.msg;

/**
 * Helper class for dealing with archives such as .zip files.
 */
public final class Archiver {

    /**
     * This is a utility class, no public constructor is needed (or
     * should appear in documentation).
     */
    private Archiver() {}

    /**
     * Configuration directives that are not yet supported and should
     * raise a warning.
     */
    final static String[] UNSUPPORTED_DIRECTIVES = new String[] {"-printusage ", "-printseeds ", "-printconfiguration ", "-dump "};

    /**
     * Zips a directory as an archive, removing the directory prefix
     * from the entries.
     *
     * @param dir          the directory
     * @param archive      the archive to create
     * @return             true if some individual file could not be added
     * @throws IOException when a major error occurred
     */
    public static boolean zipTree(File dir, File archive) throws IOException {
        Collection<ZipEntrySource> files = new LinkedList<>();

        boolean error = false;
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
                System.err.println(msg("Could not process file " + f + ": " + ex.getMessage()));
                error = true;
            }
        }
        ZipUtil.pack(files.toArray(new ZipEntrySource[0]), archive);
        return error;
    }

    /**
     * Compresses a number of directories as archives under a target
     * directory. For every directory, its prefix is removed from the
     * entries.
     *
     * @param dirs        a collection of directories
     * @param targetDir   the target directory to use for creating the archives
     * @return            a map from each source directory to its archive
     */
    public static Map<File, File> zipTrees(Collection<File> dirs, File targetDir) {
        Map<File, File> archiveMap = new HashMap<>();
        for (File dir : dirs) {
            try {
                String hash = md5(dir.getCanonicalPath());
                File preTestCodeJar = new File(targetDir, hash + Conventions.TEST_CODE_PRE_JAR);
                zipTree(dir, preTestCodeJar);
                System.out.println(msg("Archiving code [" + dir + "] as [" + preTestCodeJar + "]"));
                archiveMap.put(dir, preTestCodeJar);
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (NoSuchAlgorithmException ex) {
                System.err.println(msg("Error: no MD5 algorithm found."));
            }
        }
        return archiveMap;
    }

    private static String md5(String text) throws NoSuchAlgorithmException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(StandardCharsets.UTF_8.encode(text));
        return String.format("%032x", new BigInteger(1, md5.digest()));
    }

    /**
     * If the file given contains unsupported directives, create a copy without them.
     *
     * @param conf     the configuration file containing the directives
     * @param warnings a list of warnings to populate
     * @return         the original file (if no unsupported directives were found) or a
     *                 new file without the offending directives
     * @throws         IOException if the new file could not be written
     */
    private static File deleteUnsupportedDirectives(File conf, List<String> warnings) throws IOException {
        boolean allSupported = true;
        LinkedList<String> lines = new LinkedList<>();
        try (BufferedReader txtReader = new BufferedReader(new FileReader(conf))) {
            String line;
            while ((line = txtReader.readLine()) != null) {
                if (line == null)
                    continue;
                boolean supportedLine = true;
                for (String d : UNSUPPORTED_DIRECTIVES) {
                    if (line.contains(d)) {
                        warnings.add("WARNING: file " + conf.getCanonicalPath() + " contains unsupported directive " + d);
                        allSupported = false;
                        supportedLine = false;
                    }
                }
                if (supportedLine)
                    lines.add(line + "\n");
            }
        }
        if (!allSupported) {
            File ret = File.createTempFile("rules", ".pro");
            try (FileWriter fw = new FileWriter(ret)) {
                for (String l : lines)
                    fw.write(l);
            }
            return ret;
        } else
            return conf;
    }

    /**
     * Zips a list of configuration files.
     *
     * @param configurationFiles  the input configuration files
     * @param confZip             the output file
     * @param warnings            a list of warnings to populate
     *
     * @throws                    IOException if unsupported directives could not be filtered out
     */
    public static void zipConfigurations(List<File> configurationFiles, File confZip, List<String> warnings) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(confZip))) {
            for (File conf : configurationFiles) {
                if (!conf.exists()) {
                    warnings.add("WARNING: file does not exist: " + conf);
                    continue;
                }
                String entryName = stripRootPrefix(conf.getCanonicalPath());
                out.putNextEntry(new ZipEntry(entryName));
                conf = deleteUnsupportedDirectives(conf, warnings);
                byte[] data = Files.readAllBytes(conf.toPath());
                out.write(data, 0, data.length);
                out.closeEntry();
            }
        }
    }

    /**
     * Strip root directory prefix to make absolute paths relative.
     *
     * @param s   an absolute path
     * @return    the path without its leading root prefix (such as '/' on Linux)
     */
    public static String stripRootPrefix(String s) {
        return s.startsWith(File.separator) ? s.substring(1) : s;
    }
}
