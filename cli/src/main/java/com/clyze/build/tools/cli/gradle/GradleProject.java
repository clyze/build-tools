package com.clyze.build.tools.cli.gradle;

import com.clyze.build.tools.Archiver;
import com.clyze.build.tools.Conventions;
import com.clyze.build.tools.cli.Config;
import com.clyze.build.tools.cli.maven.MavenDependencyResolver;
import com.clyze.client.web.PostState;
import org.clyze.utils.JHelper;
import org.clyze.utils.OS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * A representation of aspects of the current Gradle project, needed for
 * constructing a snapshot to post to the server.
 */
public class GradleProject {
    public static final String DEP_PROJECT_PREFIX = "project ";
    public static final String ROOT_PROJECT = "<ROOT>";
    public static final String PROJECT_PREFIX = "Project ";
    enum State { WAITING, DEP_START, DEP_END }

    private State state;

    private final Set<String> dependencies = new HashSet<>();
    private String project = null;
    private final String userHomeDir;
    private final Config config;
    private final File currentDir;
    private final boolean debug;
    private final PostState ps;
    private final MavenDependencyResolver mavenDependencyResolver;

    public GradleProject(File currentDir, Config config, String userHomeDir, PostState ps) {
        this.userHomeDir = userHomeDir;
        this.config = config;
        this.currentDir = currentDir;
        this.debug = config.isDebug();
        this.ps = ps;
        this.mavenDependencyResolver = new MavenDependencyResolver(config);
    }

    /**
     * Resolves the current project dependencies.
     */
    public void resolveDependencies() {
        if (config.isDebug())
            System.out.println("Reading Gradle caches...");
        initDependencyPaths();

        try {
            String[] cmd = new String[] { findGradle(currentDir), "dependencies" };
            List<String> dependencyLines = new ArrayList<>();
            JHelper.runWithOutput(cmd, null, dependencyLines::add);
            this.state = State.WAITING;
            dependencyLines.forEach(this::processDependencyLine);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (debug)
            System.out.println("Dependencies: " + dependencies.size());
    }

    private String findGradle(File dir) throws IOException {
        if (dir == null)
            return "gradle";
        File gradlew = new File(dir, "gradlew");
        if (gradlew.exists())
            return gradlew.getCanonicalPath();
        File gradlewBat = new File(dir, "gradlew.bat");
        if (gradlewBat.exists())
            return gradlewBat.getCanonicalPath();
        if (new File(dir, "settings.gradle").exists())
            return "gradle";
        return findGradle(dir.getParentFile());
    }

    public void setProject(String project) {
        this.project = project;
    }

    public void processDependencyLine(String line) {
        if (debug)
            System.out.println("Processing line: " + line);
        switch (state) {
            case WAITING:
                if (line.startsWith("runtimeClasspath "))
                    state = State.DEP_START;
                else if (line.startsWith("Root project"))
                    setProject(GradleProject.ROOT_PROJECT);
                else if (line.startsWith(GradleProject.PROJECT_PREFIX))
                    setProject(line.substring(GradleProject.PROJECT_PREFIX.length()));
                break;
            case DEP_START:
                line = line.trim();
                if (line.length() == 0)
                    state = State.DEP_END;
                else {
                    int idx = 0;
                    for (int i = 0; i < line.length(); i++) {
                        char c = line.charAt(i);
                        if (c != '|' && c != '\t' && c != ' ' && c != '+' &&
                                c != '-' && c != '\\') {
                            idx = i;
                            break;
                        }
                    }
                    String dependency = line.substring(idx);
                    // Skip "(*)"/"(c)" suffix
                    if (dependency.endsWith(" (*)") || dependency.endsWith(" (c)"))
                        dependency = dependency.substring(0, dependency.length() - 3);
                    else {
                        // Transform "a:b:X -> Y" to "a:b:Y".
                        String ARROW = " -> ";
                        int arrowIdx = dependency.indexOf(ARROW);
                        if (arrowIdx > 0) {
                            int lastColonIdx = dependency.lastIndexOf(':');
                            dependency = dependency.substring(0, lastColonIdx) + ':' + dependency.substring(arrowIdx + ARROW.length());
                        }
                    }
                    if (debug)
                        System.out.println("Found dependency: " + dependency);
                    // Handle (sub-)project dependencies.
                    if (dependency.startsWith(DEP_PROJECT_PREFIX))
                        resolveProjectDependency(dependency.substring(DEP_PROJECT_PREFIX.length()));
                    else
                        resolveExternalDependency(dependency.trim());
                    dependencies.add(dependency);
                }
                break;
            case DEP_END:
                // Do nothing, dependencies already gathered.
        }
    }

    private void resolveExternalDependency(String dependency) {
        if (debug)
            System.out.println("Processing dependency: " + dependency);
        if (dependencies.contains(dependency)) {
            if (debug)
                System.out.println("Dependency already processed: " + dependency);
            return;
        }

        if (dependency.startsWith(DEP_PROJECT_PREFIX)) {
            System.out.println("Skipping project dependency: " + dependency);
            return;
        }
        String[] parts = dependency.split(":");
        if (parts.length == 3)
            mavenDependencyResolver.resolveDependency(ps, parts[0], parts[1], parts[2]);
        else
            System.out.println("WARNING: cannot handle dependency: " + dependency);
    }

    private void resolveProjectDependency(String dependency) {
        System.out.println("TODO: project dependency: " + dependency);
        StringBuilder sb = new StringBuilder();
        // Go up according to project name.
        if (project != null && !DEP_PROJECT_PREFIX.equals(project)) {
            String[] parts = project.split(":");
            for (String part : parts) {
                if (part.length() > 0) {
                    sb.append("..");
                    sb.append(File.separator);
                }
            }
        }

        // Go down according to subproject structure.
        String[] parts = dependency.split(":");
        for (String part : parts)
            if (part.length() > 0) {
                sb.append(part);
                sb.append(File.separator);
            }

        String subprojDir = sb.toString();
        File subproj = new File(subprojDir);
        if (subproj.exists()) {
            System.out.println("Detected subproject directory: " + subprojDir);
            String depId = dependency.replaceAll(":", "_");
            try {
                File code = zipClasses(subprojDir, Paths.get(subprojDir, "build", "classes"), "clyze-" + depId + "-classes.jar");
                if (code != null)
                    ps.addFileInput(Conventions.BINARY_INPUT_TAG, code.getCanonicalPath());
                File sources = zipClasses(subprojDir, Paths.get(subprojDir, "src"), "clyze-" + depId + "-sources.jar");
                if (sources != null)
                    ps.addFileInput(Conventions.SOURCE_INPUT_TAG, sources.getCanonicalPath());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private File zipClasses(String subprojDir, Path classesDir, String archiveName) {
        File javaClassesDir = classesDir.toFile();
        if (javaClassesDir.exists()) {
            try {
                File outFile = Paths.get(subprojDir, "build", archiveName).toFile();
                Archiver.zipTree(javaClassesDir, outFile);
                return outFile;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void initDependencyPaths() {
        if (OS.linux || OS.macOS) {
            mavenDependencyResolver.indexMavenLocal(userHomeDir);
            mavenDependencyResolver.indexLocalRepository(Paths.get(userHomeDir, ".gradle", "caches"));
        } else if (OS.win) {
            System.out.println("ERROR: dependency resolution on Windows is not yet supported.");
            return;
        }
        if (debug)
            System.out.println("Dependency paths: " + mavenDependencyResolver.getDependencyPaths().size());
    }

}
