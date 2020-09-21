package com.clyze.build.tools;

import java.io.*;
import java.util.List;
import com.clyze.client.Message;
import com.clyze.client.web.Helper;
import com.clyze.client.web.PostOptions;
import com.clyze.client.web.PostState;
import com.clyze.client.web.api.AttachmentHandler;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.conn.HttpHostConnectException;

/**
 * This is a thin interface over the clue-client functionality that
 * posts a project to the server.
 */
public class Poster {
    private final File cachePostDir;
    private final PostOptions options;
    private final File metadataDir;

    public Poster(PostOptions options, File cachePostDir,
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
     * @param ps       the build representation
     * @param handler  a handler of the resulting file returned by the server
     * @throws ClientProtocolException  if the server encountered an error
     */
    public void repackageBuildForCI(PostState ps, AttachmentHandler<String> handler)
    throws ClientProtocolException{
        if (options.dry)
            System.err.println("WARNING: automated repackaging ignores dry option.");
        Helper.repackageBuildForCI(options.host, options.port, options.username,
                                   options.password, options.project, options.platform, ps, handler);
    }

}
