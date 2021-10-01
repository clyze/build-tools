package com.clyze.build.tools.cli.buck;

import com.clyze.build.tools.Archiver;
import com.clyze.build.tools.Conventions;
import com.clyze.build.tools.cli.BuildTool;
import com.clyze.build.tools.cli.Config;
import com.clyze.build.tools.cli.Util;
import com.clyze.client.Printer;
import com.clyze.client.web.PostState;
import com.google.gson.Gson;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.clyze.build.tools.cli.Util.*;

/**
 * Integration with the Buck build system.
 */
public class Buck extends BuildTool {

    public Buck(File currentDir, Config config) {
        super(currentDir, config);
    }

    @Override
    public String getName() {
        return "buck";
    }

    @Override
    public void populatePostState(PostState ps, Config config) {
        List<String> codeFiles = config.getCodeFiles();
        if (codeFiles != null && codeFiles.size() > 0) {
            println("Code files: " + codeFiles);
            if (codeFiles.size() > 1) {
                logError("More than one code files given, this is not supported yet.");
                return;
            }
        } else {
            logError("Error: No code was given.");
            config.printUsage();
            return;
        }

        Collection<SourceFile> sourceFiles = Sources.getSources(config.getSourceDirs(), config.isAutodetectSources());

        println("Using snapshot directory: " + Conventions.CLYZE_SNAPSHOT_DIR);
        boolean mk = new File(Conventions.CLYZE_SNAPSHOT_DIR).mkdirs();
        logDebug("Directory " + Conventions.CLYZE_SNAPSHOT_DIR + " created: " + mk);

        String buildApk = gatherApk(codeFiles.get(0));

        File sourcesJar = new File(Conventions.CLYZE_SNAPSHOT_DIR, Conventions.SOURCES_FILE);
        Collection<File> sourceJars = new HashSet<>();
        Sources.packSources(sourceFiles, sourcesJar);
        sourceJars.add(sourcesJar);

        BuildMetadataConf bmc = null;
        try {
            List<String> configurations = config.getConfigurations();
            boolean explicitConf = configurations != null && configurations.size() > 0;
            // If explicit configuration is provided, disable rule autodetection.
            String proguard = explicitConf ? null : config.getProguard();
            bmc = gatherMetadataAndConfigurations(config.getTraceFile(), config.getJsonDir(), proguard);
            if (explicitConf) {
                logError("Using provided configuration: " + configurations);
                List<File> entries = new LinkedList<>();
                configurations.forEach(c -> entries.add(new File(c)));
                zipConfigurations(entries, getConfigurationsFile());
            }
        } catch (IOException ex) {
            logError("Error gathering metadata/configurations, will try to continue...");
            ex.printStackTrace();
        }

        ps.addFileInput(Conventions.BINARY_INPUT_TAG, buildApk);
        sourceJars.forEach (sj -> addSourceJar(ps, sj));
        if (bmc != null) {
            ps.addFileInput("JCPLUGIN_METADATA", bmc.metadata);
            try {
                ps.addFileInput("PG_ZIP", bmc.configuration.getCanonicalPath());
            } catch (IOException ex) {
                logError("Error: could not add configurations file: " + bmc.configuration);
            }
        }

        // Set default platform, in case the server cannot determine
        // the platform from the submitted code.
        ps.addStringInput(Conventions.JVM_PLATFORM, Conventions.getR8AndroidPlatform("25"));
    }

    /**
     * Copies the code archive to the snapshot directory.
     *
     * @param code the path to the code archive
     * @return     the path of the code inside the snapshot directory (null on failure)
     */
    private static String gatherApk(String code) {
        File target = new File(Conventions.CLYZE_SNAPSHOT_DIR, new File(code).getName());
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
        return new File(Conventions.CLYZE_SNAPSHOT_DIR, Conventions.CONFIGURATIONS_FILE);
    }

    /**
     * Read the Buck trace file to gather code metadata and configurations.
     *
     * @param traceFile   the Buck trace file to read
     * @param jsonDir     the JSON metadata output directory (for the metadata generator)
     * @param proguard    the optimizer binary used (null to skip configuration autodetection)
     */
    private static BuildMetadataConf gatherMetadataAndConfigurations(String traceFile, String jsonDir, String proguard) throws IOException {
        println("Gathering metadata and configurations using trace file '" + traceFile +"'...");

        File configurationsFile = getConfigurationsFile();
        @SuppressWarnings("unchecked") Map<String, Object>[] json = (new Gson()).fromJson(new InputStreamReader(new FileInputStream(traceFile)), Map[].class);
        for (Map<String, Object> map : json) {
            Object argsEntry = map.get("args");
            if (argsEntry instanceof Map) {
                @SuppressWarnings("unchecked") Object descEntry = ((Map<String, Object>)argsEntry).get("description");
                if (descEntry != null) {
                    String desc = descEntry.toString();
                    if (proguard != null && desc.contains("java ") &&
                            desc.contains("-jar") && desc.contains(proguard))
                        processProguardInvocation(desc, configurationsFile);
                }
            }
        }

        String metadataFile = new File(Conventions.CLYZE_SNAPSHOT_DIR, Conventions.METADATA_FILE).getCanonicalPath();
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

        return new BuildMetadataConf(metadataFile, configurationsFile);
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
        String projectDir = (new File(".")).getCanonicalPath();
        Archiver.zipConfigurations(entries, configurationsFile, consolePrinter, projectDir, null, null);
    }

    static final Printer consolePrinter = new Printer() {

        @Override
        public void error(String message) {
            Util.logError(message);
        }

        @Override
        public void warn(String message) {
            Util.logError(message);
        }

        @Override
        public void debug(String message) {
            Util.logDebug(message);
        }

        @Override
        public void info(String message) {
            System.out.println(message);
        }

        @Override
        public void always(String message) {
            System.out.println(message);
        }
    };

    private static void addSourceJar(PostState ps, File sourceJar) {
        try {
            ps.addFileInput("SOURCES_JAR", sourceJar.getCanonicalPath());
        } catch (IOException ex) {
            logError("Could not add source archive: " + sourceJar);
        }
    }

}
