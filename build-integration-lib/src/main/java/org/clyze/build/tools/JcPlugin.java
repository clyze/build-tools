package org.clyze.build.tools;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.*;

public class JcPlugin {

    /*
     * Returns the metadata plugin artifact, so that it can be
     * integrated as a dependency.
     *
     * @return the artifact id of the javac metadata plugin
     */
    public static String getJcPluginArtifact() {
        String JCPLUGIN_VERSION_FILE = "jcplugin.version";
        ClassLoader cl = JcPlugin.class.getClassLoader();
        InputStream resIs = cl.getResourceAsStream(JCPLUGIN_VERSION_FILE);
        if (resIs == null)
            throw new RuntimeException("Could not read resource " + JCPLUGIN_VERSION_FILE);
        try (BufferedReader txtReader = new BufferedReader(new InputStreamReader(resIs))) {
            return txtReader.readLine();
        } catch (Exception ex) {
            throw new RuntimeException("Could not read resource " + JCPLUGIN_VERSION_FILE + ": " + ex.getMessage());
        }
    }

    public static List<String> getJcPluginClasspath(ClassLoader cl, String resourceDir) {
        URL dirURL = cl.getResource(resourceDir);
        if (dirURL == null)
            return null;

        List<String> ret = new LinkedList<>();
        try {
            JarURLConnection jarConnection = (JarURLConnection) dirURL.openConnection();
            ZipFile jar = jarConnection.getJarFile();
            File tmpDir = Files.createTempDirectory("jcplugin-jars").toFile();
            for (ZipEntry entry : Collections.list(jar.entries())) {
                String name = entry.getName();
                if (name.equals(resourceDir) || !name.startsWith(resourceDir))
                    continue;
                name = name.substring(resourceDir.length());
                File outFile = new File(tmpDir, name);
                copyZipEntryToFile(jar, entry, outFile);
                ret.add(outFile.getCanonicalPath());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
        return ret;
    }

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
}
