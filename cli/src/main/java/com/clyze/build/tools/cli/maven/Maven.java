package com.clyze.build.tools.cli.maven;

import com.clyze.build.tools.Settings;
import com.clyze.build.tools.cli.BuildTool;
import com.clyze.build.tools.cli.Config;
import com.clyze.client.web.PostState;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.function.Consumer;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Integration with the Maven build system.
 */
public class Maven extends BuildTool {
    private final MavenDependencyResolver mavenDependencyResolver;

    public Maven(File currentDir, Config config) {
        super(currentDir, config);
        this.mavenDependencyResolver = new MavenDependencyResolver(config);
    }

    @Override
    public String getName() {
        return "maven";
    }

    @Override
    public void populatePostState(PostState ps, Config config) throws IOException {
        if (debug)
            System.out.println("Looking for code...");
        String currentDirPath = currentDir.getCanonicalPath();
        gatherCodeFromTargetDir(ps, currentDirPath, false);

        if (debug)
            System.out.println("Gathering dependencies...");
        resolveDependencies(ps);

        if (debug)
            System.out.println("Looking for sources...");
        gatherSourcesFromSrcDir(ps);
        gatherGeneratedSourcesFromTarget(ps, currentDirPath);
    }

    private void processPom(String tag, File pom, Consumer<Model> proc) throws IOException {
        if (!pom.exists())
            return;
        String pomPath = pom.getCanonicalPath();
        if (debug)
            System.out.println(tag + " Reading " + pomPath);
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try (FileReader fr = new FileReader(pom)) {
            Model model = reader.read(fr);
            proc.accept(model);
            // Go up if there is a parent pom.xml.
            if (model.getParent() != null) {
                int lastSep = pomPath.lastIndexOf(File.separator);
                if (lastSep >= 0) {
                    String thisDir = pomPath.substring(0, lastSep);
                    int secondToLastSep = thisDir.lastIndexOf(File.separator);
                    if (secondToLastSep >= 0) {
                        String parent = thisDir.substring(0, secondToLastSep);
                        if (debug)
                            System.out.println("Checking parent=" + parent);
                        processPom(tag, new File(parent, "pom.xml"), proc);
                    }
                }
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        }
    }

    private void gatherMavenDependencies(PostState ps, File pom) throws IOException {
        // Step 1: gather all (parent-)project properties, in order
        // to resolve dependencies with symbolic versions.
        Properties allProperties = new Properties();
        processPom("DEPENDENCIES/PROPERTIES: ", pom, (m -> allProperties.putAll(m.getProperties())));
        if (debug)
            allProperties.forEach((k, v) -> System.out.println(k + " -> " + v));

        // Step 2: gather artifacts and use properties (from above)
        // to make symbolic dependencies concrete.
        processPom("DEPENDENCIES/ARTIFACTS: ", pom, (m -> {
            for (Dependency dep : m.getDependencies()) {
                String groupId = dep.getGroupId();
                String artifactId = dep.getArtifactId();
                String version = dep.getVersion();
                if (version != null && version.startsWith("${")) {
                    String versionLookup = (String) allProperties.get(version.substring(2, version.length()-1));
                    if (versionLookup != null)
                        version = versionLookup;
                }
                if (groupId != null && artifactId != null && version != null) {
                    String id = groupId + ":" + artifactId + ":" + version;
                    if (debug)
                        System.out.println("Detected Maven dependency: " + id);
                    mavenDependencyResolver.resolveDependency(ps, groupId, artifactId, version);
                }
            }
        }));
    }

    private void gatherGeneratedSourcesFromTarget(PostState ps, String currentDirPath) throws IOException {
        File generatedSourcesDir = Paths.get(currentDirPath, "target", "generated-sources").toFile();
        gatherSources(ps, generatedSourcesDir, getTmpJarFile("generated-sources"));
    }

    private void resolveDependencies(PostState ps) throws IOException {
        String userHomeDir = Settings.getUserHomeDir();
        if (userHomeDir == null) {
            System.out.println("WARNING: no user home directory found, cannot resolve dependencies.");
            return;
        }

        mavenDependencyResolver.indexMavenLocal(userHomeDir);
        gatherMavenDependencies(ps, new File("pom.xml"));
    }
}
