package org.clyze.build.tools;

import com.google.gson.Gson;
import java.io.*;
import java.util.Map;

/**
 * This class gathers information about the installation.
 */
public class Settings {
    private static final String C_DIR = ".clyze";

    /**
     * Returns the installation directory, containing all application
     * versions.
     *
     * @return the installation directory
     */
    public static File getAppDirectory() {
        String homeDir = System.getProperty("user.home");
        if (homeDir == null)
            homeDir = System.getenv("HOME");
        if (homeDir == null)
            System.err.println(Conventions.msg("Could not determine home directory"));
        else {
            File cDir = new File(homeDir, C_DIR);
            if (!cDir.exists())
                System.err.println(Conventions.msg("Could not find directory: " + cDir));
            else
                return cDir;
        }
        return null;
    }

    /**
     * Returns the installed application version.
     *
     * @return the version identifier
     */
    public static String getAppVersion() {
        File appDir = getAppDirectory();
        if (appDir != null) {
            File lruVersion = new File(appDir, "lru-version.txt");
            if (lruVersion.exists()) {
                try (BufferedReader txtReader = new BufferedReader(new InputStreamReader(new FileInputStream(lruVersion)))) {
                    return txtReader.readLine();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            System.err.println(Conventions.msg("Could not read version from file " + lruVersion));
        }
        return null;
    }

    /**
     * Returns the installation directory of the current version.
     *
     * @return the installation directory
     */
    public static File getVersionedAppDirectory() {
        File appDir = getAppDirectory();
        String ver = getAppVersion();
        if (appDir != null && ver != null) {
            File vDir = new File(appDir, ver);
            if (vDir.exists())
                return vDir;
        }
        System.err.println(Conventions.msg("Could not find install directory in " + appDir + " for version " + ver));
        return null;
    }

    /**
     * Returns the configuration file of the installation.
     *
     * @return a map representation of the original JSON data
     */
    public static Map<String, Object> getConfig() {
        File vDir = getVersionedAppDirectory();
        if (vDir != null) {
            File sDir = new File(vDir, "settings");
            if (sDir.exists()) {
                File config = new File(sDir, "config.json");
                if (config.exists()) {
                    try {
                        @SuppressWarnings("unchecked") Map<String, Object> json = (new Gson()).fromJson(new InputStreamReader(new FileInputStream(config)), Map.class);
                        return json;
                    } catch (FileNotFoundException ex) {
                        // This should not normally happen, as we just checked if the file exists.
                        ex.printStackTrace();
                    }
                } else {
                    System.err.println(Conventions.msg("Could not find configuration inside directory " + sDir));
                }
            } else
                System.err.println(Conventions.msg("Could not find settings directory inside directory " + vDir));
        }
        return null;
    }

    /**
     * Returns the port used by the installation for posting bundles.
     *
     * @return a string representation of the port
     */
    public static String getDefaultPort() {
        Map<String, Object> conf = Settings.getConfig();
        if (conf != null) {
            Object jsonPort = conf.get("jetty_port");
            if (jsonPort != null)
                if (jsonPort instanceof Double)
                    return ((Double)jsonPort).intValue() + "";
                else if (jsonPort instanceof Float)
                    return ((Float)jsonPort).intValue() + "";
                else
                    return jsonPort + "";
        }
        return null;
    }

    /**
     * This is a utility class, no public constructor is needed (or
     * should appear in documentation).
     */
    private Settings() {}
}
