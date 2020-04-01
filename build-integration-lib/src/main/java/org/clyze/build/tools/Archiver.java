package org.clyze.build.tools;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.*;
import org.clyze.client.Message;
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

    /** Filter out unsupported directives. */
    final static boolean FILTER_UNSUPPORTED_DIRECTIVES = false;

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
     * @param messages a list of messages to populate
     * @return         the original file (if no unsupported directives were found) or a
     *                 new file without the offending directives
     * @throws         IOException if the new file could not be written
     */
    private static File deleteUnsupportedDirectives(File conf, List<Message> messages) throws IOException {
        boolean allSupported = true;
        LinkedList<String> lines = new LinkedList<>();
        try (BufferedReader txtReader = new BufferedReader(new FileReader(conf))) {
            String line;
            while ((line = txtReader.readLine()) != null) {
                boolean supportedLine = true;
                for (String d : UNSUPPORTED_DIRECTIVES) {
                    if (line.contains(d)) {
                        Message.warn(messages, "WARNING: file " + conf.getCanonicalPath() + " contains unsupported directive: " + line);
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
     * @param messages            a list of messages to populate
     * @param projectDir          the path of the project
     * @param disablingConfPath   the path of the disabling configuration
     * @param printConfigPath     a file (path) containing all configuration for sanity check
     *
     * @throws                    IOException if unsupported directives could not be filtered out
     */
    public static void zipConfigurations(List<File> configurationFiles, File confZip, List<Message> messages, String projectDir, String disablingConfPath, String printConfigPath) throws IOException {
        final String SEP = File.separator;
        final String GRADLE_CACHE = ".gradle" + SEP + "caches" + SEP + "transforms";
        Set<String> entryNamesProcessed = new HashSet<>();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(confZip))) {
            for (File conf : configurationFiles) {
                if (!conf.exists()) {
                    Message.debug(messages, "WARNING: file does not exist: " + conf);
                    continue;
                }
                String path = conf.getCanonicalPath();
                // Massage entry names.
                String entryName;
                if (path.equals(disablingConfPath)) {
                    // Don't add our "disabling rules" file to the archive.
                    continue;
                    // entryName = "DISABLING_RULES";
                } else if (projectDir != null && path.startsWith(projectDir))
                    entryName = stripRootPrefix(path.substring(projectDir.length()));
                else if (path.contains(GRADLE_CACHE)) {
                    String[] parts = path.split(SEP.equals("\\") ? "\\\\" : "/");
                    entryName = path.contains("META-INF") ?
                        parts[parts.length-3] + SEP + parts[parts.length-2] + SEP + parts[parts.length-1] :
                        parts[parts.length-2] + SEP + parts[parts.length-1];
                } else
                    entryName = stripRootPrefix(path);
                // Avoid duplicate entry names by keeping the first one.
                if (entryNamesProcessed.contains(entryName)) {
                    Message.warn(messages, "WARNING: duplicate configuration entry: " + entryName);
                    continue;
                } else
                    entryNamesProcessed.add(entryName);
                out.putNextEntry(new ZipEntry(entryName));
                if (FILTER_UNSUPPORTED_DIRECTIVES)
                    conf = deleteUnsupportedDirectives(conf, messages);
                byte[] data = Files.readAllBytes(conf.toPath());
                out.write(data, 0, data.length);
                out.closeEntry();
            }
        }

        if (printConfigPath != null) {
            checkConfigurationsArchive(confZip, new File(printConfigPath), disablingConfPath, messages);
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

    /**
     * Construct a list of file paths corresponding to the contents of
     * a resource directory in the program JAR.
     *
     * @param cl           the class loader to use for loading the resource
     * @param resourceDir  the resource directory
     * @return             a list of paths to the extracted directory contents
     */
    public static List<String> getUnpackedResources(ClassLoader cl, String resourceDir) {
        URL dirURL = cl.getResource(resourceDir);
        if (dirURL == null)
            return null;

        List<String> ret = new LinkedList<>();
        try {
            JarURLConnection jarConnection = (JarURLConnection) dirURL.openConnection();
            ZipFile jar = jarConnection.getJarFile();
            File tmpDir = Files.createTempDirectory("resources").toFile();
            for (ZipEntry entry : Collections.list(jar.entries())) {
                String name = entry.getName();
                if (name.equals(resourceDir) || !name.startsWith(resourceDir))
                    continue;
                name = name.substring(resourceDir.length());
                File outFile = new File(tmpDir, name);
                copyZipEntryToFile(jar, entry, outFile);
                ret.add(outFile.getCanonicalPath());
            }
            return ret;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Extracts a ZIP entry and writes it to a file.
     *
     * @param zip   the ZIP file
     * @param entry the ZIP entry
     * @param f     the output file
     */
    private static void copyZipEntryToFile(ZipFile zip, ZipEntry entry, File f) throws IOException {
        // System.out.println(zip + ":" + entry + " -> " + f);
        try (InputStream is = zip.getInputStream(entry);
             OutputStream os = new BufferedOutputStream(new FileOutputStream(f))){
            byte[] buffer = new byte[4096];
            int readCount;
            while ((readCount = is.read(buffer)) > 0) {
                os.write(buffer, 0, readCount);
            }
        }
    }

    /**
     * Check the completeness of the configurations archive to be uploaded.
     *
     * @param confZip            the configurations archive
     * @param printConfigFile    the file containing the reference configuration
     * @param disablingConfPath  the path to the "disabling configuration" (or null)
     * @param messages           a list to be populated with messages (instead
     *                           of writing to the console)
     */
    public static void checkConfigurationsArchive(File confZip, File printConfigFile,
                                                  String disablingConfPath,
                                                  List<Message> messages) {
        if (!printConfigFile.exists()) {
            Message.warn(messages, "Cannot check configuration completeness, file missing: " + printConfigFile);
            return;
        }
        try {
            Message.debug(messages, "Checking configuration completeness...");
            ZipFile zipFile = new ZipFile(confZip);
            List<String> rules = new LinkedList<>();
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                rules.add(fromInputStreamToString(zipFile.getInputStream(entry)));
            }
            if (disablingConfPath != null) {
                File dc = new File(disablingConfPath);
                if (dc.exists())
                    rules.add(fromInputStreamToString(new FileInputStream(dc)));
            }
            // Sort by longest-string-first, to avoid mishandling
            // rules being subset of each other.
            rules.sort(((String s1, String s2) -> s2.length() - s1.length()));
            String totalRules = fromInputStreamToString(new FileInputStream(printConfigFile));
            for (String r : rules) {
                int idx = totalRules.indexOf(r);
                if (idx < 0)
                    Message.warn(messages, "Bundled rules not found in total configuration: " + r);
                else
                    totalRules = totalRules.substring(0, idx) + totalRules.substring(idx + r.length());
            }
            String diff = totalRules.trim();
            if (diff.length() != 0)
                Message.warn(messages, "Configurations check, rules not uploaded: '" + diff + "'");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static String fromInputStreamToString(InputStream is) throws IOException {
        Path tmpFile = File.createTempFile("rules", ".tmp").toPath();
        Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
        return new String(Files.readAllBytes(tmpFile));
    }
}
