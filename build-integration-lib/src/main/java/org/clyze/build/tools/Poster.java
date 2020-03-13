package org.clyze.build.tools;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.clyze.client.web.Helper;
import org.clyze.client.web.PostState;
import org.clyze.client.web.api.Remote;

/**
 * This is a thin interface over the clue-client functionality that
 * posts a project to the server.
 */
public class Poster {
    private final boolean cachePost;
    private final Options options;
    private final File metadataDir;
    private final boolean android;

    public Poster(Options options, boolean cachePost,
                  File metadataDir, boolean android) {
        this.options = options;
        this.cachePost = cachePost;
        this.metadataDir = metadataDir;
        this.android = android;
    }

    public void post(PostState ps, List<Message> messages) {
        if (cachePost) {
            try {
                File tmpDir = Files.createTempDirectory("").toFile();
                ps.saveTo(tmpDir);
                Message.print(messages, "Saved post state in " + tmpDir);
            } catch (IOException ex) {
                Message.warn(messages, "WARNING: cannot save post state: " + ex.getMessage());
            }
        }

        // Check if the server can receive Android bundles.
        if (android) {
            Map<String, String> diag = Remote.at(options.host, options.port).diagnose();
            String androidSDK_OK = diag.get("ANDROID_SDK_OK");
            if ((androidSDK_OK != null) && (androidSDK_OK.equals("false"))) {
                Message.print(messages, "ERROR: Cannot post bundle: Android SDK setup missing.");
                return;
            }
        }

        if (metadataDir != null) {
            File metadataFile = new File(metadataDir, Conventions.POST_METADATA);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(metadataFile))) {
                Message.print(messages, "Saving options in: " + metadataFile.getCanonicalPath());
                writer.write(ps.toJSON());
            } catch (IOException ex) {
                Message.warn(messages, "WARNING: cannot save metadata: " + ex.getMessage());
            }
        }

        if (!options.dry) {
            Helper.doPost(options.host, options.port, options.username,
                          options.password, options.project, options.profile, ps);
        }
    }

    /**
     * The options that drive the interaction with the server.
     */
    public static class Options {
        public String host;
        public int port;
        public String username;
        public String password;
        public String project;
        public String profile;
        public boolean dry;
    }
}
