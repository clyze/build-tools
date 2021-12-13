package com.clyze.intellijplugin.state

/**
 * A unique analysis run: project -> snapshot -> analysis name.
 */
class AnalysisRun(val project : String, val snapshot : String, val analysis : String)
