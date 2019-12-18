package org.clyze.doop.gradle

import groovy.transform.TypeChecked
import org.clyze.build.tools.Archiver
import org.clyze.build.tools.Conventions
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

import static org.clyze.build.tools.Conventions.msg

@TypeChecked
class TestRepackageTask extends DefaultTask {

    @TaskAction
    void repackageTest() {

        Extension ext = Extension.of(project)
        if (!(ext.platform instanceof AndroidPlatform)) {
            project.logger.error msg("Task '" + RepackagePlugin.TASK_REPACKAGE_TEST + "' is only available for Android projects.")
            return
        }
        AndroidPlatform platform = (AndroidPlatform)ext.platform

        String testClassTopDir = platform.getAppBuildDir() + File.separator + 'intermediates' + File.separator + 'javac' + File.separator + platform.getFlavorAndBuildType()
        String testClassTopDirUnitTest = testClassTopDir + "UnitTest";
        List<File> testCodeDirs = [
            new File(testClassTopDir, 'classes'),
            new File(testClassTopDir + File.separator + platform.getUnitTestCompileInnerTask(), 'classes'),
            new File(testClassTopDirUnitTest, 'classes')
        ]

        List<File> existingTestCodeDirs = testCodeDirs.findAll {it.exists()}
        if (existingTestCodeDirs.size() == 0) {
            project.logger.error msg("ERROR: could not find test code, directories searched: " + testCodeDirs)
        } else {
            project.logger.info msg("Test code directories: " + existingTestCodeDirs)
            println msg("Test code directories searched: " + testCodeDirs)
            File testCodeBundleDir = new File(ext.scavengeOutputDir, Conventions.TEST_CODE_DIR)
            if (!testCodeBundleDir.exists()) {
                testCodeBundleDir.mkdirs()
            }
            File preTestCodeJar = new File(testCodeBundleDir, Conventions.TEST_CODE_PRE_JAR)
            Archiver.zipTree(existingTestCodeDirs, preTestCodeJar)

            // Call standard 'repackage' task functionality on test code.
            File out = RepackageTask.repackageCodeArchive(project, ext, preTestCodeJar.getCanonicalPath(), "repackaged-test-code", ".jar")
            println msg("Repackaged test code: ${out.canonicalPath}")
        }
    }
}
