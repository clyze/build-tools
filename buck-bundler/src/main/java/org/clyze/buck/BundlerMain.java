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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.types.Commandline;
import org.clyze.build.tools.Archiver;
import org.clyze.build.tools.Conventions;
import org.clyze.build.tools.JcPlugin;
import org.clyze.build.tools.Poster;
import org.clyze.client.web.Helper;
import org.clyze.client.web.PostState;
import org.clyze.utils.JHelper;
import org.zeroturnaround.zip.*;

import static org.clyze.buck.BundlerUtil.*;

public class BundlerMain {

    private static List<String> cachedJcpluginClasspath = null;

    public static void main(String[] args) {

        Config conf;
        try {
            conf = new Config(args);
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            Config.showUsage();
            return;
        }

        if (conf.help) {
            Config.showUsage();
            return;
        }

        List<String> codeFiles = conf.codeFiles;
        if (codeFiles != null && codeFiles.size() > 0) {
            println("Code files: " + codeFiles);
            if (codeFiles.size() > 1) {
                logError("More than one code files given, this is not supported yet.");
                return;
            }
        } else {
            logError("Error: No code was given.");
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

        Collection<SourceFile> sourceFiles = Sources.getSources(conf.sourceDirs, conf.autodetectSources);

        println("Using bundle directory: " + Conventions.CLUE_BUNDLE_DIR);
        boolean mk = new File(Conventions.CLUE_BUNDLE_DIR).mkdirs();
        logDebug("Directory " + Conventions.CLUE_BUNDLE_DIR + " created: " + mk);

        String bundleApk = gatherApk(codeFiles.get(0));

        File sourcesJar = new File(Conventions.CLUE_BUNDLE_DIR, Conventions.SOURCES_FILE);
        Collection<File> sourceJars = new HashSet<>();
        Sources.packSources(sourceFiles, sourcesJar);
        sourceJars.add(sourcesJar);

        BundleMetadataConf bmc = null;
        try {
            boolean explicitConf = conf.configurations != null && conf.configurations.size() > 0;
            // If explicit configuration is provided, disable rule autodetection.
            String proguard = explicitConf ? null : conf.proguard;
            bmc = gatherMetadataAndConfigurations(conf.traceFile, conf.jsonDir, proguard);
            if (explicitConf) {
                logError("Using provided configuration: " + conf.configurations);
                List<File> entries = new LinkedList<>();
                conf.configurations.forEach(c -> entries.add(new File(c)));
                zipConfigurations(entries, getConfigurationsFile());
            }
        } catch (IOException ex) {
            logError("Error gathering metadata/configurations, will try to continue...");
            ex.printStackTrace();
        }

        postBundle(bundleApk, sourceJars, bmc, conf);
    }

    /**
     * Copies the code archive to the bundle directory.
     *
     * @param code the path to the code archive
     * @return     the path of the code inside the bundle directory (null on failure)
     */
    private static String gatherApk(String code) {
        File target = new File(Conventions.CLUE_BUNDLE_DIR, new File(code).getName());
        try {
            Files.copy(Paths.get(code), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return target.getCanonicalPath();
        } catch (IOException ex) {
            logError("Failed to copy '" + code + "' to '" + target + "'");
            ex.printStackTrace();
        }
        return null;
    }

    private static File getConfigurationsFile() {
        return new File(Conventions.CLUE_BUNDLE_DIR, Conventions.CONFIGURATIONS_FILE);
    }

    /**
     * Read the Buck trace file to gather code metadata and configurations.
     *
     * @param traceFile   the Buck trace file to read
     * @param jsonDir     the JSON metadata output directory (for the metadata generator)
     * @param proguard    the optimizer binary used (null to skip configuration autodetection)
     */
    private static BundleMetadataConf gatherMetadataAndConfigurations(String traceFile, String jsonDir, String proguard) throws IOException {
        println("Gathering metadata and configurations using trace file '" + traceFile +"'...");

        File configurationsFile = getConfigurationsFile();
        @SuppressWarnings("unchecked") Map<String, Object>[] json = (new Gson()).fromJson(new InputStreamReader(new FileInputStream(traceFile)), Map[].class);
        for (Map<String, Object> map : json) {
            Object argsEntry = map.get("args");
            if (argsEntry instanceof Map) {
                @SuppressWarnings("unchecked") Object descEntry = ((Map<String, Object>)argsEntry).get("description");
                if (descEntry != null) {
                    String desc = descEntry.toString();
                    if (desc.contains("javac "))
                        processJavacInvocation(jsonDir, desc);
                    else if (proguard != null && desc.contains("java ") &&
                             desc.contains("-jar") && desc.contains(proguard))
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

    private static String[] insertSpace(String[] src, int pos, int extraElems) {
        String[] ret = new String[src.length + extraElems];
        System.arraycopy(src, 0, ret, 0, pos);
        System.arraycopy(src, pos, ret, pos + extraElems, src.length - pos);
        return ret;
    }

    private static void processJavacInvocation(String jsonDir, String desc) {
        // Read classpath from contents of bundled jcplugin dir.
        if (cachedJcpluginClasspath == null)
            cachedJcpluginClasspath = JcPlugin.getJcPluginClasspath();
        // If no metadata plugin was found, skip javac rerun but allow for the
        // rest of the operations to continue.
        if (cachedJcpluginClasspath == null) {
            logError("Internal error: no javac plugin found -- continuing without source metadata");
            return;
        }

        StringBuilder newEntries = new StringBuilder();
        for (int i = 0; i < cachedJcpluginClasspath.size(); i++) {
            String jar = cachedJcpluginClasspath.get(i);
            if (i == 0)
                newEntries.append(jar);
            else
                newEntries.append(":").append(jar);
        }

        // Convert string command (plus javac plugin) to array.
        String plugin = "-Xplugin:TypeInfoPlugin " + jsonDir;
        String[] args = Commandline.translateCommandline(desc + " '" + plugin + "'");
        if (!args[0].endsWith("javac")) {
            logError("Warning: command line does not look like a javac invocation: " + desc);
        }

        int processorpathIdx = -1;
        int idx = 0;
        while (idx < args.length) {
            if (args[idx].equals("-processorpath")) {
                processorpathIdx = idx + 1;
                idx += 2;
            } else
                idx += 1;
        }
        String jcCP = newEntries.toString();
        if (processorpathIdx != -1)
            args[processorpathIdx] += ":" + jcCP;
        else {
            int pos = 1;
            int extraElems = 2;
            final String PP_FLAG = "-processorpath";
            String[] argsWithJcCP = insertSpace(args, pos, extraElems);
            argsWithJcCP[pos] = PP_FLAG;
            argsWithJcCP[pos + 1] = jcCP;
            args = argsWithJcCP;
        }

        String newCmd = Arrays.asList(args).toString();
        println("Changed command: " + newCmd);
        try {
            JHelper.runWithOutput(args, "JC", null);
        } catch (Exception ex) {
            println("Command failed: " + newCmd);
        }
    }

    /**
     * Process a ProGuard invocation from a trace file, to detect rule files.
     *
     * @param desc                the contents of the JSON 'desc' value
     * @param configurationsFile  the output file to hold configurations
     */
    private static void processProguardInvocation(String desc, File configurationsFile) {
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

        List<File> entries = new LinkedList<>();
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
                    File rulesFile = new File(line);
                    try {
                        entries.add(rulesFile);
                    } catch (Exception ex) {
                        logError("Could not process file: " + rulesFile);
                    }
                }
                nextLineIsRulesFile = false;
            }
            zipConfigurations(entries, configurationsFile);
        } catch (IOException e) {
            logError("Could not parse proguard args file: " + argsFile);
            e.printStackTrace();
        }
    }

    /**
     * Packages a list of files.
     *
     * @param entries             the file entries to package
     * @param configurationsFile  the output file
     * @throws IOException        on packaging error
     */
    private static void zipConfigurations(List<File> entries, File configurationsFile) throws IOException {
        List<String> warnings = new LinkedList<>();
        Archiver.zipConfigurations(entries, configurationsFile, warnings);
        warnings.forEach(BundlerUtil::logError);
    }

    private static void postBundle(String bundleApk, Collection<File> sourceJars,
                                   BundleMetadataConf bmc, Config conf) {
        PostState ps = new PostState();
        ps.setId(Conventions.BUNDLE_ID);
        ps.addFileInput("INPUTS", bundleApk);
        if (sourceJars != null)
            sourceJars.forEach (sj -> addSourceJar(ps, sj));
        if (bmc != null) {
            ps.addFileInput("JCPLUGIN_METADATA", bmc.metadata);
            try {
                ps.addFileInput("PG_ZIP", bmc.configuration.getCanonicalPath());
            } catch (IOException ex) {
                logError("Error: could not bundle configurations file: " + bmc.configuration);
            }
        }

        // Set default platform, in case the server cannot determine
        // the platform from the submitted code.
        ps.addStringInput("PLATFORM", Conventions.getR8AndroidPlatform("25"));

        if (conf.opts.dry)
            println("Assembling bundle (dry mode)...");
        else
            println("Posting bundle to the server...");

        (new Poster(conf.opts, false)).post(ps);
    }

    private static void addSourceJar(PostState ps, File sourceJar) {
        try {
            ps.addFileInput("SOURCES_JAR", sourceJar.getCanonicalPath());
        } catch (IOException ex) {
            logError("Could not add source archive: " + sourceJar);
        }
    }

}

class BundleMetadataConf {
    final String metadata;
    final File configuration;
    BundleMetadataConf(String metadata, File configuration) {
        this.metadata = metadata;
        this.configuration = configuration;
    }
}
