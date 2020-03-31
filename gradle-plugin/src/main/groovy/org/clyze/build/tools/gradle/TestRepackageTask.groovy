package org.clyze.build.tools.gradle

import groovy.transform.TypeChecked
import org.apache.commons.io.FileUtils
import org.clyze.build.tools.Archiver
import org.clyze.build.tools.Conventions
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.zeroturnaround.zip.ZipUtil

import static org.clyze.build.tools.Conventions.msg

/**
 * A task that runs project tests on the optimized output.
 */
@TypeChecked
class TestRepackageTask extends PostTask {

    /**
     * The main task action.
     */
    @TaskAction
    void repackageTest() {

        Extension ext = Extension.of(project)
        if (!(ext.platform instanceof AndroidPlatform)) {
            project.logger.error msg("Task '" + PTask.REPACKAGE_TEST.name + "' is only available for Android projects.")
            return
        }
        AndroidPlatform platform = (AndroidPlatform)ext.platform

        String classTopDir = platform.getAppBuildDir() + File.separator + 'intermediates' + File.separator + 'javac' + File.separator + platform.getFlavorAndBuildType()
        String classTopDirUnitTest = classTopDir + "UnitTest"
        // Separate code and test-code directories: only code found in
        // the former will be optimized.
        List<File> codeDirs = [
            new File(classTopDir, 'classes'),
            new File(classTopDir + File.separator + platform.getUnitTestCompileInnerTask(), 'classes'),
        ]
        List<File> testCodeDirs = [ new File(classTopDirUnitTest, 'classes') ]

        List<File> existingTestCodeDirs = (codeDirs + testCodeDirs).findAll {it.exists()}
        if (existingTestCodeDirs.size() == 0) {
            project.logger.error msg("ERROR: could not find test code, directories searched: " + codeDirs)
        } else {
            project.logger.info msg("Test code directories: " + existingTestCodeDirs)
            println msg("Test code directories searched: " + codeDirs)
            File testCodeBundleDir = new File(ext.getBundleDir(project), Conventions.TEST_CODE_DIR)
            if (!testCodeBundleDir.exists()) {
                testCodeBundleDir.mkdirs()
            }
            Map<File, File> testCodeArchives = Archiver.zipTrees(existingTestCodeDirs, testCodeBundleDir)
            Map<File, File> codeJars = testCodeArchives.findAll {dir, jar -> codeDirs.contains(dir)}
            if (codeJars.size() == 0) {
                project.logger.error msg("ERROR: no code JARs found.")
                return
            }

            File originalCodeDir = null
            File preTestCodeJar = null
            codeJars.each {dir, jar ->
                originalCodeDir = dir
                preTestCodeJar = jar
            }
            if (codeJars.size() > 1) {
                project.logger.warn msg("WARNING: too many code JARs found, using: " + preTestCodeJar)
            }

            // Call standard 'repackage' task functionality on test code.
            File repackagedCode = repackageCodeArchive(ext, preTestCodeJar.canonicalPath, 'repackaged-test-code', '.jar', 'false')
            println msg("Repackaged code: ${repackagedCode.canonicalPath}")

            println msg("Replacing " + originalCodeDir + " with contents of " + repackagedCode)
            FileUtils.deleteDirectory(originalCodeDir)
            if (!originalCodeDir.mkdirs()) {
                project.logger.warn msg("WARNING: directory may not have been deleted properly: ${originalCodeDir}")
            }
            ZipUtil.unpack(repackagedCode, originalCodeDir)
        }
    }
}
