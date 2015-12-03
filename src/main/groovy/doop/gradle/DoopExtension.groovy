package doop.gradle

import org.gradle.util.ConfigureUtil

/**
 * Created by saiko on 28/7/2015.
 */
class DoopExtension {
    String host
    int port
    String username
    String password
    String projectName
    String projectVersion
    File scavengeOutputDir
    AnalysisConfig analysis = new AnalysisConfig()

    def analysis(Closure cl) {
        ConfigureUtil.configure(cl, analysis)
    }
}
