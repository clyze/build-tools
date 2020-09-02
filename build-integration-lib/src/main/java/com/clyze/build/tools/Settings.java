package com.clyze.build.tools;

import com.google.gson.Gson;
import org.clyze.utils.OS;

import java.io.*;
import java.util.Map;

/**
 * This class gathers information about the installation.
 */
public class Settings {

    /**
     * Finds the home directory of the user.
     * @return   the home path
     */
    private static String getUserHomeDir() {
        String homeDir = System.getProperty("user.home");
        if (homeDir == null)
            homeDir = System.getenv("HOME");
        if (homeDir == null)
            System.err.println(Conventions.msg("Could not determine home directory"));
        return homeDir;
    }

    /**
     * Builds the installation "home" directory.
     * @param root   the top level directory
     * @return       the installation "home" directory
     */
    private static File getAppHomeDir(String root) {
        File dir = new File(new File(root, "clyze-desktop"), "home");
        if (!dir.exists())
            System.err.println(Conventions.msg("Could not find directory: " + dir));
        return dir;
    }

    /**
     * Returns the installation directory, containing all application
     * versions. See https://www.electronjs.org/docs/api/app#appgetpathname and
     * https://bitbucket.org/clyze/clyze/commits/c60329629288e176f43b6951f93a8df73e596726
     *
     * @return the installation directory
     */
    public static File getAppDirectory() {
        if (OS.isLinux()) {
            String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfigHome != null)
                return getAppHomeDir(xdgConfigHome);
            else
                return getAppHomeDir(getUserHomeDir() + "/.config");
        } else if (OS.getMacOS()) {
            return getAppHomeDir(getUserHomeDir() + "/Library/Application Support");
        } else if (OS.getWin()) {
            String appData = System.getenv("APPDATA");
            if (appData == null)
                throw new RuntimeException("Could not determine APPDATA, plase set appropriate environment variable.");
            return getAppHomeDir(appData);
        }
        System.err.println(Conventions.msg("ERROR: could not determine system type."));
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
     * Returns the port used by the installation for posting builds.
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
