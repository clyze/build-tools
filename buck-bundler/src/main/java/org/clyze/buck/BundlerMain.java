package org.clyze.buck;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FileUtils;
import org.clyze.build.tools.Conventions;
import org.clyze.build.tools.JcPlugin;
import org.clyze.client.web.Helper;
import org.clyze.client.web.PostState;
import org.clyze.utils.JHelper;

import static org.clyze.build.tools.Conventions.msg;

class BundlerMain {

    private static List<String> cachedJcpluginClasspath = null;

    public static void main(String[] args) {

        Config conf;
        try {
            conf = new Config(args);
        } catch (Exception ex) {
            ex.printStackTrace();
            Config.showUsage();
            return;
        }

        if (conf.help) {
            Config.showUsage();
            return;
        }

        String apk = conf.apk;
        if (apk != null)
            println("APK: " + apk);
        else {
            logError("Error: No APK was given.");
            Config.showUsage();
            return;
        }
        String javacPluginPath = conf.javacPluginPath;
        if (javacPluginPath != null)
            println("Using javac plugin in path: " + javacPluginPath);
        else {
            String javacPlugin = JcPlugin.getJcPluginArtifact();
            println("Using javac plugin artifact: " + javacPlugin);
        }

        Collection<String> sourceDirs = conf.sourceDirs;
        if (sourceDirs == null)
            logError("Warning: No sources were given.");

        println("Using bundle directory: " + Conventions.CLUE_BUNDLE_DIR);
        new File(Conventions.CLUE_BUNDLE_DIR).mkdirs();

        String bundleApk = gatherApk(apk);
        Collection<String> sourceJars = gatherSources(sourceDirs);
        BundleMetadataConf bmc = null;
        try {
            bmc = gatherMetadataAndConfigurations(conf.traceFile, conf.jsonDir, conf.proguard);
        } catch (IOException ex) {
            logError("Error gathering metadata/configurations, will try to continue...");
            ex.printStackTrace();
        }

        if (conf.post)
            postBundle(bundleApk, sourceJars, bmc, conf);
    }

    /**
     * Copies the APK to the bundle directory.
     *
     * @param apk  the path to the APK archive
     * @return     the path of the APK inside the bundle directory (null on failure)
     */
    private static String gatherApk(String apk) {
        File target = new File(Conventions.CLUE_BUNDLE_DIR, new File(apk).getName());
        try {
            Files.copy(Paths.get(apk), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return target.getCanonicalPath();
        } catch (IOException ex) {
            logError("Failed to copy '" + apk + "' to '" + target + "'");
            ex.printStackTrace();
        }
        return null;
    }

    private static Collection<String> gatherSources(Collection<String> sourceDirs) {
        println("Gathering sources...");
        File sourcesJar = new File(Conventions.CLUE_BUNDLE_DIR, Conventions.SOURCES_FILE);
        sourcesJar.delete();
        String sourcesJarPath;
        try {
            FileUtils.touch(sourcesJar);
            sourcesJarPath = sourcesJar.getCanonicalPath();
        } catch (IOException ex) {
            logError("Error creating sources JAR file: " + sourcesJar);
            ex.printStackTrace();
            return null;
        }
        for (String sourceDir : sourceDirs) {
            println("Reading source directory: " + sourceDir);
            // TODO: this does not handle repeating entries (which will pause
            //  execution to prompt the user in the console and thus get stuck).
            String[] args = new String[] {"jar", "-uf", sourcesJarPath, "-C", sourceDir, "."};
            try {
                JHelper.runWithOutput(args, "JAR");
            } catch (IOException ex) {
                logError("Error adding sources directory contents (" + sourceDir + ") to JAR file '" + sourcesJarPath + "'");
                ex.printStackTrace();
            }
        }
        Collection<String> ret = new HashSet<>();
        ret.add(Conventions.CLUE_BUNDLE_DIR + File.separator + Conventions.SOURCES_FILE);
        return ret;
    }

    private static BundleMetadataConf gatherMetadataAndConfigurations(String traceFile, String jsonDir, String proguard) throws IOException {
        println("Gathering metadata and configurations using trace file '" + traceFile +"'...");

        String configurationsFile = new File(Conventions.CLUE_BUNDLE_DIR, Conventions.CONFIGURATIONS_FILE).getCanonicalPath();
        Map[] json = (new Gson()).fromJson(new InputStreamReader(new FileInputStream(traceFile)), Map[].class);
        for (Map map : json) {
            Object argsEntry = map.get("args");
            if (argsEntry != null && argsEntry instanceof Map) {
                Object descEntry = ((Map<String, Object>)argsEntry).get("description");
                if (descEntry != null) {
                    String desc = descEntry.toString();
                    if (desc.contains("javac "))
                        processJavacInvocation(jsonDir, desc);
                    else if (desc.contains("java ") && desc.contains("-jar") &&
                             desc.contains(proguard))
                        processProguardInvocation(desc, configurationsFile);
                }
            }
        }

        String metadataFile = new File(Conventions.CLUE_BUNDLE_DIR, Conventions.METADATA_FILE).getCanonicalPath();
        println("Adding JSON metadata to file: " + metadataFile);

        try (FileOutputStream fos = new FileOutputStream(metadataFile);
             ZipOutputStream metadataZip = new ZipOutputStream(fos)) {

            List<File> jsonFiles = Files.walk(Paths.get(jsonDir)).filter(Files::isRegularFile).map(Path::toFile).collect(Collectors.toList());
            for (File jsonFile : jsonFiles) {
                FileInputStream fis = new FileInputStream(jsonFile);
                metadataZip.putNextEntry(new ZipEntry(jsonFile.getName()));
                byte[] bytes = new byte[1024];
                int length;
                while((length = fis.read(bytes)) >= 0)
                    metadataZip.write(bytes, 0, length);
                fis.close();
            }
        }

        return new BundleMetadataConf(metadataFile, configurationsFile);
    }

    private static void processJavacInvocation(String jsonDir, String desc) {
        // Read classpath from contents of bundled jcplugin dir.
        if (cachedJcpluginClasspath == null)
            cachedJcpluginClasspath = JcPlugin.getJcPluginClasspath(BundlerMain.class.getClassLoader(), "jcplugin/");

        final String CP_OPT = "-classpath";
        int cpIndex = desc.indexOf(CP_OPT);
        if (cpIndex == -1)
            logError("ERROR: could not find classpath option in: " + desc);
        else {
            int cpStart = cpIndex + CP_OPT.length() + 1;
            StringBuilder newEntries = new StringBuilder();
            for (String jar : cachedJcpluginClasspath)
                newEntries.append(jar).append(":");
            // The extra flag stops the command line from interpreting
            // the next argument in a wrong way.
            String plugin = "-Xplugin:TypeInfoPlugin -AjcpluginJSONDir="+jsonDir;
            desc = desc.substring(0, cpIndex) + plugin + " " + desc.substring(cpIndex, cpStart) + newEntries.toString() + desc.substring(cpStart);
            println("== Changed command: " + desc + " ==");
            try {
                int exitCode = JHelper.runCommand(desc, "JC", System.out::println);
                println("== Command finished, exit code: " + exitCode + " ==");
            } catch (Exception ex) {
                println("== Command failed: " + desc + " ==");
            }
        }
    }

    private static void processProguardInvocation(String desc, String configurationsFile) {
        int atIndex = desc.indexOf('@');
        if (atIndex == -1) {
            logError("ERROR: could not find arguments file of proguard command: " + desc);
            return;
        }
        int endIndex = desc.indexOf(')');
        if (endIndex == -1)
            endIndex = desc.indexOf(' ');
        String argsFile = endIndex == -1 ? desc.substring(atIndex+1) : desc.substring(atIndex+1, endIndex);
        println("Reading proguard args from file: " + argsFile);

        try (BufferedReader reader = new BufferedReader(new FileReader(argsFile))) {
            String line;
            boolean nextLineIsRulesFile = false;
            while ((line = reader.readLine()) != null) {
                line = line.replaceAll("\n", "").replaceAll("\"", "");
                // println("["+line+"]");
                if (line.equals("-include")) {
                    nextLineIsRulesFile = true;
                    continue;
                } else if (nextLineIsRulesFile) {
                    println("Adding configuration file: " + line);
                    String[] cmd = new String[] { "zip", "-r", configurationsFile, line };
                    JHelper.runWithOutput(cmd, "PG_CONF");
                }
                nextLineIsRulesFile = false;
            }
        } catch (IOException e) {
            logError("Could not parse proguard args file: " + argsFile);
            e.printStackTrace();
        }
    }

    private static void postBundle(String bundleApk, Collection<String> sourceJars,
                                   BundleMetadataConf bmc, Config conf) {
        println("Posting bundle to the server...");

        PostState ps = new PostState();
        ps.setId(Conventions.BUNDLE_ID);
        ps.addFileInput("INPUTS", bundleApk);
        sourceJars.forEach (sj -> ps.addFileInput("SOURCES_JAR", sj));
        if (bmc != null) {
            ps.addFileInput("JCPLUGIN_METADATA", bmc.metadata);
            ps.addFileInput("PG_ZIP", bmc.configuration);
        }
        ps.addStringInput("PLATFORM", Conventions.getR8AndroidPlatform("25"));
        Helper.doPost(conf.host, conf.port, conf.username, conf.password, conf.project, conf.profile, ps);
    }

    static void println(String s) {
        System.out.println(msg(s));
    }

    static void logError(String s) {
        System.err.println(msg(s));
    }
}

class BundleMetadataConf {
    final String metadata;
    final String configuration;
    BundleMetadataConf(String metadata, String configuration) {
        this.metadata = metadata;
        this.configuration = configuration;
    }
}
