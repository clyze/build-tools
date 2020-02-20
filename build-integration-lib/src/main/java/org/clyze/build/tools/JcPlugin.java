package org.clyze.build.tools;

import java.io.*;
import java.util.List;

/**
 * Functionality related to the javac plugin that generates source
 * code metadata.
 */
public class JcPlugin {

    /**
     * This is a utility class, no public constructor is needed (or
     * should appear in documentation).
     */
    private JcPlugin() {}

    /**
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

    /**
     * Returns the classpath of the bundled javac plugin.
     *
     * @return a list of JAR files
     */
    public static List<String> getJcPluginClasspath() {
        return Archiver.getUnpackedResources(JcPlugin.class.getClassLoader(), "jcplugin/");
    }
}
