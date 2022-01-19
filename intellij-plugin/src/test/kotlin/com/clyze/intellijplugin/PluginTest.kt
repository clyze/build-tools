package com.clyze.intellijplugin

import com.clyze.intellijplugin.state.Config
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PluginTest : BasePlatformTestCase() {

    fun test_getWebPath() {
        val config = Config(myFixture.project, null, null)
        val host = "localhost"
        val userName = "user-1"
        val token = "token-2"
        config.setServer(host)
        config.setUser(userName)
        config.setToken(token)

        assertEquals(config.getServer(), host)
        assertEquals(config.getUser(), userName)
        assertEquals(config.getToken(), token)

        assertEquals(config.getWebPath(), "http://$host/#/u/$userName")

        val projectName = "proj2"
        config.projectName = projectName
        assertEquals(config.getWebPath(), "http://$host/#/u/$userName/projects/$projectName")
        val snapshotName = "snap4"
        config.snapshotName = snapshotName
        assertEquals(config.getWebPath(), "http://$host/#/u/$userName/projects/$projectName/snapshots/$snapshotName")
    }

}
