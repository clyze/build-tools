package org.clyze.build.tools;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

class JcPlugin {

    /*
     * Returns the metadata plugin artifact, so that it can be used
     * with javac invocations.
     *
     * @return the path to the javac metadata plugin
     */
    public static String getJcPluginArtifact() {
        String JCPLUGIN_VERSION_FILE = "jcplugin.version";
        ClassLoader cl = JcPlugin.class.getClassLoader();
        try (BufferedReader txtReader = new BufferedReader(new InputStreamReader(cl.getResourceAsStream(JCPLUGIN_VERSION_FILE)))) {
            return txtReader.readLine();
        } catch (Exception ex) {
            throw new RuntimeException("Could not read resource " + JCPLUGIN_VERSION_FILE + ": " + ex.getMessage());
        }
    }
}
