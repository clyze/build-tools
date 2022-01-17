package com.clyze.intellijplugin.state

import com.clyze.client.web.AuthToken
import com.clyze.client.web.api.Remote
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project

/**
 * A server configuration.
 */
class Config(val project: Project, var projectName: String?, var snapshotName: String?) {
    fun getRemote() : Remote {
        return Remote.at(getServer(), AuthToken(getUser(), getToken()))
    }

    private fun getProps(): PropertiesComponent {
        return PropertiesComponent.getInstance(project)
    }

    fun setServer(s: String) {
        getProps().setValue("server", s)
    }

    fun getServer() : String {
        return getProps().getValue("server") ?: ""
    }

    fun setUser(u: String) {
        getProps().setValue("user", u)
    }

    fun getUser() : String {
        return getProps().getValue("user") ?: ""
    }

    fun setToken(t: String) {
        getProps().setValue("token", t)
    }

    fun getToken() : String {
        return getProps().getValue("token") ?: ""
    }

    /**
     * Returns a URL that can be opened in a Web browser to land the user
     * to an appropriate location in the Web UI.
     */
    fun getWebPath() : String {
        var serverPath = getServer()
        if (!serverPath.startsWith("http://") && !serverPath.startsWith("https://"))
            serverPath = "http://$serverPath"
        val user = getUser()
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