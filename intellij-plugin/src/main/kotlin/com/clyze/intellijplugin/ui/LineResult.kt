package com.clyze.intellijplugin.ui

/**
 * An analysis result returned by the server for a specific source line.
 */
class LineResult(val symbolId : String, val type : String, val description : String)
