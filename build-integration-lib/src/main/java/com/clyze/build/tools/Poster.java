package com.clyze.build.tools;

import java.io.*;
import com.clyze.client.Printer;
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

    public void post(PostState ps, Printer printer, boolean debug) {
        Helper.post(ps, options, cachePostDir, metadataDir, printer, debug);
    }

    /**
     * Test server capabilities.
     *
     * @param printer   receiver of messages to display
     * @return          true if the server is compatible, false otherwise (see
     *                  messages for reason)
     * @throws HttpHostConnectException if the server did not respond
     */
    public boolean isServerCapable(Printer printer)
        throws HttpHostConnectException {
        return Helper.isServerCapable(options, printer);
    }

    /**
     * Invokes the automated repackaging endpoint.
     *
     * @param ps       the snapshot representation
     * @param handler  a handler of the resulting file returned by the server
     * @param printer  receiver of messages to display
     * @throws ClientProtocolException  if the server encountered an error
     */
    public void repackageSnapshotForCI(PostState ps, AttachmentHandler<String> handler,
                                       Printer printer)
    throws ClientProtocolException{
        if (options.dry)
            printer.warn("WARNING: automated repackaging ignores dry option.");
        Helper.repackageSnapshotForCI(options.getHostPrefix(), options.username,
                options.authToken, options.project, ps, handler, printer);
    }

}
