package org.clyze.build.tools;

import java.io.*;
import java.util.List;
import org.clyze.client.Message;
import org.clyze.client.web.Helper;
import org.clyze.client.web.PostOptions;
import org.clyze.client.web.PostState;
import org.clyze.client.web.api.AttachmentHandler;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.conn.HttpHostConnectException;

/**
 * This is a thin interface over the clue-client functionality that
 * posts a project to the server.
 */
public class Poster {
    private final String cachePostDir;
    private final PostOptions options;
    private final File metadataDir;

    public Poster(PostOptions options, String cachePostDir,
                  File metadataDir) {
        this.options = options;
        this.cachePostDir = cachePostDir;
        this.metadataDir = metadataDir;
    }

    public void post(PostState ps, List<Message> messages, boolean debug) {
        Helper.post(ps, options, messages, cachePostDir, metadataDir, debug);
    }

    /**
     * Test server capabilities.
     *
     * @param messages  a list of messages to contain resulting errors/warnings
     * @return          true if the server is compatible, false otherwise (see
     *                  messages for reason)
     * @throws HttpHostConnectException if the server did not respond
     */
    public boolean isServerCapable(List<Message> messages)
        throws HttpHostConnectException {
        return Helper.isServerCapable(options, messages);
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
        Helper.repackageBundleForCI(options.host, options.port, options.username,
                                    options.password, options.project, options.platform, ps, handler);
    }

}
