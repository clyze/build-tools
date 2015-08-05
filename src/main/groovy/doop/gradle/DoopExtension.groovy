package doop.gradle

import org.gradle.util.ConfigureUtil

/**
 * Created by saiko on 28/7/2015.
 */
class DoopExtension {
    String url
    String username
    String password
    AnalysisConfig analysis = new AnalysisConfig()

    def analysis(Closure cl) {
        ConfigureUtil.configure(cl, analysis)
    }
}
