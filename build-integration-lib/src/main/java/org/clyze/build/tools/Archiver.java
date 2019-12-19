package org.clyze.build.tools;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.stream.Collectors;
import org.zeroturnaround.zip.*;

import static org.clyze.build.tools.Conventions.msg;

class Archiver {

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


}
