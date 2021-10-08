package com.clyze.build.tools.cli.maven;

import com.clyze.build.tools.Conventions;
import com.clyze.build.tools.cli.Config;
import com.clyze.client.web.PostState;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.clyze.utils.JHelper;

/**
 * A local-filesystem resolver for Maven-style dependencies ("group:artifact:version").
 */
public class MavenDependencyResolver {
    private final Map<String, String> dependencyPaths = new HashMap<>();
    private final boolean debug;
    private final Config config;

    public MavenDependencyResolver(Config config) {
        this.config = config;
        this.debug = config.isDebug();
    }

    /**
     * Index the local Maven repository.
     * @param userHomeDir    the home directory of the user
     */
    public void indexMavenLocal(String userHomeDir) {
        indexLocalRepository(Paths.get(userHomeDir, ".m2", "repository"));
    }

    /**
     * Index a repository from the local filesystem.
     * @param dir    the directory containing the Maven-style repository
     */
    public void indexLocalRepository(Path dir) {
        if (!dir.toFile().exists()) {
            if (debug)
                System.out.println("Ignoring non-existent path: " + dir);
            return;
        }

        String[] cmd = new String[]{"find", dir.toString()};
        if (debug)
            System.out.println(Arrays.toString(cmd));
        try {
            JHelper.runWithOutput(cmd, null, ((String line) -> {
                int lastSep = line.lastIndexOf(File.separator);
                if ((line.endsWith(".jar") || line.endsWith(".pom")) && lastSep >= 0) {
                    String depFile = line.substring(lastSep + 1);
                    if (debug)
                        System.out.println("Registering: " + depFile + " -> " + line);
                    dependencyPaths.put(depFile, line);
                }
                else if (debug)
                    System.out.println("Ignoring: " + line);

            }));
        } catch (IOException ex) {
            if (debug)
                ex.printStackTrace();
        }
    }

    /**
     * Returns the computed dependency path mapping.
     *
     * @return a map from JAR artifacts to file paths
     */
    public Map<String, String> getDependencyPaths() {
        return dependencyPaths;
    }

    /**
     * Resolves a dependency and adds it as a library to the snapshot.
     * @param ps            the snapshot state to update
     * @param groupId       the dependency group id
     * @param artifactId    the dependency artifact id
     * @param version       the dependency version
     */
    public void resolveDependency(PostState ps, String groupId, String artifactId, String version) {
        String prefix = artifactId + "-" + version;
        String jarKey = prefix + ".jar";
        if (debug)
            System.out.println("Searching for: " + jarKey);
        String depPath = dependencyPaths.get(jarKey);
        if (depPath != null) {
            if (debug)
                System.out.println("Adding dependency: " + depPath);
            ps.addFileInput(Conventions.LIBRARY_INPUT_TAG, depPath);
            if (config.includesDepSources()) {
                String depSources = dependencyPaths.get(prefix + "-sources.jar");
                if (debug)
                    System.out.println("Adding dependency source: " + depSources);
                ps.addFileInput(Conventions.SOURCE_INPUT_TAG, depSources);
            }
        } else if (dependencyPaths.get(prefix + ".pom") != null) {
            if (debug)
                // Ignore .pom-only dependencies (e.g. Bill-Of-Materials dependencies).
                System.out.println("Ignoring dependency without code but with .pom: " + groupId + ":" + artifactId + ":" + version);
        } else
            System.out.println("WARNING: cannot resolve dependency: " + groupId + ":" + artifactId + ":" + version);
    }
}
