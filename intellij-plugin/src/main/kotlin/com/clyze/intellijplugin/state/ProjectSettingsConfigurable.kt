package com.clyze.intellijplugin.state

import com.clyze.intellijplugin.services.ClyzeProjectService
import com.clyze.intellijplugin.ui.UIHelper
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.BorderLayout.PAGE_START
import java.awt.GridLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * The project settings UI (under the Settings IDE option).
 */
class ProjectSettingsConfigurable(val project: Project) : Configurable {
    private var modified : Boolean = false
    private var serverInput : JTextField? = null
    private var authUserInput : JTextField? = null
    private var authTokenInput : JTextField? = null
    private var createPublicProjects : JCheckBox? = null

    companion object {
        fun addInputModelUpdater(input : JTextField, updater : (t : String) -> Unit) {
            input.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(p0: DocumentEvent?) {
                    updater(input.text)
                }

                override fun removeUpdate(p0: DocumentEvent?) {
                    updater(input.text)
                }

                override fun changedUpdate(p0: DocumentEvent?) {
                    updater(input.text)
                }
            })
        }
    }

    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout())
        val connectionForm = JPanel(GridLayout(5, 2))
        // Host
        connectionForm.add(JLabel("Host:"))
        val hostInput = JTextField()
        connectionForm.add(hostInput)
        // User
        connectionForm.add(JLabel("User:"))
        val authUserInput = JTextField()
        connectionForm.add(authUserInput)
        // API key
        connectionForm.add(JLabel("API key:"))
        val authTokenInput = JTextField()
        connectionForm.add(authTokenInput)
        // Create public projects in the server
        connectionForm.add(JLabel("Create public projects"))
        val createPublicProjects = JCheckBox()
        connectionForm.add(createPublicProjects)
        UIHelper.setMaximumHeight(connectionForm, 220)
        panel.add(connectionForm, PAGE_START)

        // Set listeners to mark settings as "modified" (and thus enable the "Apply" button).
        val modificationSetter = { _ : String -> modified = true }
        addInputModelUpdater(hostInput, modificationSetter)
        addInputModelUpdater(authUserInput, modificationSetter)
        addInputModelUpdater(authTokenInput, modificationSetter)
        createPublicProjects.addActionListener { modified = true }

        // Record text fields in object state.
        this.serverInput = hostInput
        this.authUserInput = authUserInput
        this.authTokenInput = authTokenInput
        this.createPublicProjects = createPublicProjects

        return panel
    }

    override fun isModified(): Boolean {
        return this.modified
    }

    override fun apply() {
        // Update configuration state.
        val config = getProjectConfig()
        setFromTextField(serverInput) { s -> config.setServer(s) }
        setFromTextField(authUserInput) { s -> config.setUser(s) }
        setFromTextField(authTokenInput) { s -> config.setToken(s) }
        config.setPublic(createPublicProjects?.isSelected ?: false)
        // Reset "modified" flag.
        this.modified = false
    }

    private fun getProjectConfig() : Config {
        return project.getService(ClyzeProjectService::class.java).config
    }

    private fun setFromTextField(v : JTextField?, setter : (s : String) -> Unit) {
        if (v != null)
            setter(v.text)
    }

    override fun getDisplayName(): String {
        return "Clyze"
    }

    override fun reset() {
        super.reset()
        val config = getProjectConfig()
        serverInput?.text = config.getServer()
        authUserInput?.text = config.getUser()
        authTokenInput?.text = config.getToken()
        createPublicProjects?.isSelected = config.getPublic()
    }
}