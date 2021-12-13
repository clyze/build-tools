package com.clyze.intellijplugin

import com.clyze.intellijplugin.state.Config
import com.intellij.openapi.project.Project
import net.lingala.zip4j.ZipFile
import org.apache.commons.lang.SystemUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Helper class with functionality about posting code snapshots to the server.
 */
class Poster {
    /**
     * Post a code snapshot via the CLI tool.
     * @param project    the current project containing the code
     * @param config     the plugin configuration for this project
     */
    fun postWithCLI(project : Project, config : Config) {
        val cliId = "cli-4.0.69"
        val cliInputStream = Poster::class.java.getResourceAsStream("/$cliId.zip")
        if (cliInputStream == null) {
            println("ERROR: could not find bundled CLI tool!")
            return
        }
        val cliZip = Files.createTempFile("cli", ".zip")
        Files.copy(cliInputStream, cliZip, StandardCopyOption.REPLACE_EXISTING)
        val cliDir = Files.createTempDirectory("cli").toString()
        ZipFile(cliZip.toFile()).extractAll(cliDir)
        println("Extracted bundled CLI $cliZip -> $cliDir")
        val cli = "$cliDir/$cliId/bin/cli" + (if (SystemUtils.IS_OS_WINDOWS) ".bat" else "")
        val cmd = listOf(
            cli,
            "--dir", project.basePath,
            "--host", config.host,
            "--port", config.port,
            "--server-base-path", config.basePath,
            "--user", config.user,
            "--api-key", config.token,
            "--project", project.name
        )
        println("Running command: $cmd")
        val process = ProcessBuilder(cmd).start()
        val inputStream = process.inputStream
        val isr = InputStreamReader(inputStream)
        val br = BufferedReader(isr)
        br.lines().forEach {
            println(it)
        }
    }
}