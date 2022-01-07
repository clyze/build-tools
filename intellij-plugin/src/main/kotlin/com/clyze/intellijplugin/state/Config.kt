package com.clyze.intellijplugin.state

import com.clyze.client.web.AuthToken
import com.clyze.client.web.api.Remote

/**
 * A server configuration.
 */
class Config(
    var server: String, var user: String, var token: String,
    var projectName: String?, var snapshotName: String?
) {
    companion object {
        /** The default server to use. */
        const val defaultRemote = "localhost:8080"
    }

    fun getRemote() : Remote {
        return Remote.at(server, AuthToken(user, token))
    }

    /**
     * Returns a URL that can be opened in a Web browser to land the user
     * to an appropriate location in the Web UI.
     */
    fun getWebPath() : String {
        var serverPath = server
        if (!serverPath.startsWith("http://") && !serverPath.startsWith("https://"))
            serverPath = "http://$serverPath"
        if (user != "") {
            serverPath += "#/u/$user"
            if (projectName != null) {
                serverPath += "/projects/$projectName"
                if (snapshotName != null)
                    serverPath += "/snapshots/$snapshotName"
            }
        }
        return serverPath
    }
}