package org.clyze.doop.gradle
/**
 * Created by saiko on 28/7/2015.
 */
class AnalysisConfig {
    String id
    String name = 'context-insensitive'
    Set<File> inputFiles
    Map<String, Object> options
}
