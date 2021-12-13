package com.clyze.intellijplugin.state

import com.clyze.client.web.AuthToken
import com.clyze.client.web.api.Remote

/**
 * A server configuration.
 */
class Config(var host : String?, var port : String?, var basePath : String?,
             var user : String?, var token : String?, var projectName : String?,
             var snapshotName : String?) {
    private fun getRemotePath() : String {
        return "$host:$port$basePath"
    }

    fun getRemote() : Remote {
        return Remote.at(getRemotePath(), user, AuthToken(user, token))
    }

    /**
     * Returns a URL that can be opened in a Web browser to land the user
     * to an appropriate location in the Web UI.
     */
    fun getWebPath() : String {
        var serverPath = getRemotePath()
        if (!serverPath.startsWith("http://") && !serverPath.startsWith("https://"))
            serverPath = "http://" + serverPath
        if (user != null) {
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