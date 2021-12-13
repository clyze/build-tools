package com.clyze.intellijplugin.services

import com.clyze.intellijplugin.MyBundle

/**
 * The global application service for the Clyze plugin. Currently a no-op.
 */
class ClyzeApplicationService {

    init {
        println(MyBundle.message("applicationService"))
    }
}
