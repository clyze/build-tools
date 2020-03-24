package org.clyze.build.tools;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.clyze.client.web.Helper;
import org.clyze.client.web.PostState;
import org.clyze.client.web.api.AttachmentHandler;
import org.clyze.client.web.api.Remote;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.conn.HttpHostConnectException;

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
        // Optional: save state that will be uploaded.
        if (cachePost) {
            try {
                File tmpDir = Files.createTempDirectory("").toFile();
                ps.saveTo(tmpDir);
                Message.print(messages, "Saved post state in " + tmpDir);
            } catch (IOException ex) {
                Message.warn(messages, "WARNING: cannot save post state: " + ex.getMessage());
            }
        }

        // Optional: save post request options.
        if (metadataDir != null) {
            File metadataFile = new File(metadataDir, Conventions.POST_METADATA);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(metadataFile))) {
                Message.debug(messages, "Saving options in: " + metadataFile.getCanonicalPath());
                writer.write(ps.toJSON());
            } catch (IOException ex) {
                Message.warn(messages, "WARNING: cannot save metadata: " + ex.getMessage());
            }
        }

        try {
            // Check if the server can receive Android bundles.
            if (android && !isAndroidSupported(diagnose())) {
                Message.print(messages, "ERROR: Cannot post bundle: Android SDK setup missing.");
                return;
            }

            if (!options.dry)
                Helper.doPost(options.host, options.port, options.username,
                              options.password, options.project, options.profile, ps);
        } catch (HttpHostConnectException ex) {
            Message.print(messages, "ERROR: cannot not post bundle, is the server running?");
        }
    }

    /**
     * Helper method to check if the "diagnose" output of the server supports
     * posting of Android apps.
     *
     * @param diag   the JSON output of the server endpoint (as a Map)
     * @return       true if the server supports Android apps, false otherwise
     */
    public static boolean isAndroidSupported(Map<String, Object> diag) {
        Boolean androidSDK_OK = (Boolean)diag.get("ANDROID_SDK_OK");
        return (androidSDK_OK == null) || androidSDK_OK;
    }

    /**
     * Invokes the "diagnose" endpoint of the server.
     *
     * @return the JSON response as a Map
     * @throws HttpHostConnectException if the server did not respond
     */
    public Map<String, Object> diagnose() throws HttpHostConnectException {
        return Remote.at(options.host, options.port).diagnose();
    }

    /**
     * Invokes the automated repackaging endpoint.
     *
     * @param ps       the bundle representation
     * @param handler  a handler of the resulting file returned by the server
     * @throws ClientProtocolException  if the server encountered an error
     */
    public void repackageBundleForCI(PostState ps, AttachmentHandler<String> handler)
    throws ClientProtocolException{
        Remote.at(options.host, options.port)
            .repackageBundleForCI(options.username, options.project, ps, handler);
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
