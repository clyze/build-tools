package org.clyze.build.tools;

import java.io.*;
import java.nio.file.Files;
import org.clyze.build.tools.Conventions;
import org.clyze.client.web.Helper;
import org.clyze.client.web.PostState;

/**
 * This is a thin interface over the clue-client functionality that
 * posts a project to the server.
 */
public class Poster {
    private final boolean cachePost;
    private final Options options;

    public Poster(Options options, boolean cachePost) {
        this.options = options;
        this.cachePost = cachePost;
    }

    public void post(PostState ps) {
        if (cachePost) {
            try {
                File tmpDir = Files.createTempDirectory("").toFile();
                ps.saveTo(tmpDir);
                System.out.println(Conventions.msg("Saved post state in " + tmpDir));
            } catch (IOException ex) {
                System.err.println(Conventions.msg("WARNING: cannot save post state: " + ex.getMessage()));
            }
        }

        File metadataFile = new File(Conventions.CLUE_BUNDLE_DIR, Conventions.POST_METADATA);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(metadataFile))) {
            System.out.println(Conventions.msg("Saving options in: " + metadataFile.getCanonicalPath()));
            writer.write(ps.toJSON());
        } catch (IOException ex) {
            System.err.println(Conventions.msg("WARNING: cannot save metadata: " + ex.getMessage()));
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
