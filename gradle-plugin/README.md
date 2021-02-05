# Gradle plugin #

This plugin integrates with Gradle builds for posting projects to the
server for analysis. Some support for Ant-based builds is also included
(see below for details).

## Setup ##

Use the "clyze" plugin in your Gradle build:

```
buildscript {
    repositories {
        maven { url "https://dl.bintray.com/clyze/plugins" }
    }
    dependencies {
       classpath 'com.clyze:gradle-plugin:4.0.+'
    }
}

apply plugin: 'clyze'
```

Android integration has been tested with the following versions of the
Android Gradle plugin (dependency
"com.android.tools.build:gradle:X.Y.Z"): 3.4.2, 3.5.x,
3.6.x, and 4.0.x.

## Running the build task on a Java application ##

Assume a Java application that can be built by OpenJDK and has a
build.gradle.

Step 1. Put these lines in build.gradle (lines with default values can
be omitted):

```
clyze {
    flavor = ...         // default: none
}
```

Step 2. Run the following tasks to create the build data and post the build:

```
gradlew createBuild postBuild
```

## Running the build task on an Android app ##

Assume an Android Studio project with the following structure:

```
Project
  +-- build.gradle
  +-- local.properties
  +-- Application
    +-- build.gradle
```

Step 1. Put these lines in Application/build.gradle:

```
apply plugin: 'clyze'
...
clyze {
    flavor = "prod"       // omit if product flavors are not used
    aab = true            // true to use generated AAB, false (default) to use APK
}
```

Step 2. In directory "Project", run the build task:

```
gradlew createBuild postBuild
```

If the project contains no app submodule, then run the build tasks
in the top level directory.

## Using HPROF information ##

To add HPROF information, these steps are required:

Step 1. Run the progam using an HPROF agent to produce the heap dump
(e.g., java.hprof). For more details on how to obtain an HPROF file to
use with Doop, consult the [HeapDL
documentation](https://github.com/plast-lab/HeapDL).

Step 2. Zip java.hprof to produce java.hprof.zip. (This step is
optional, you can post the java.hprof file but it might be big.)

Step 3. In the plugin configuration section in build.gradle, add this line:

```
hprofs = [ 'java.hprof.zip' ]
```

## Interoperability with Ant ##

Basic Ant projects are supported via Gradle's Ant support.

A sample build.gradle file to generate a build for an Ant project is the following:

```
apply plugin: 'java'
apply plugin: 'application'
apply plugin: 'clyze'

buildscript {
    repositories {
        // Put here the repositories needed to find the plugin.
    }
    dependencies {
        classpath ('com.clyze:gradle-plugin:4.0.+')
    }
}

repositories {
    jcenter()
}

dependencies {
    // Put the project's dependencies here.
}

// Configure the project in Gradle (e.g. source paths).

// Import Ant tasks. Rename them so that Ant's "clean" task does not
// clash with Gradle's "clean" task.
ant.importBuild('build.xml') { antTargetName ->
    'ant-' + antTargetName
}

clyze {
    port = ...

    // Put here the rest of the options...

    // Build the sources archive separately and provide it here.
    useSourcesJar = "dist/source.zip"
}
```

## Options ##

* String _host_: the host name of the server (e.g. `"localhost"`).

* int _port_: the server port.

* String _username_: the user name to use when posting the analysis to the server.

* String _password_: the password to use to authenticate to the server.

* String _project_: the project id to post builds and analyses to, in the form "userName/projectName". E.g., for testing, it is convenient to use the ```"$clue_user/scrap"``` as the value of this option.

* String _orgName_: organization name of the artifact to post.

* String _projectName_: project name of the artifact to post.

* String _projectVersion_: project version of the artifact to post.

* List _configurationFiles_: a list of paths to configuration files
  (such as .pro files). On Android, these are optional and will be
  autodetected if left empty.

* String _ruleFile_: the rule file to use for automated repackaging.

* String _useSourcesJar_: a sources JAR archive to be posted instead
  of using the output of the `sourcesJar` Gradle task. Used in Ant
  interoperability.

* String _hprofs_: one or more HPROF files to be used in the analysis
  by HeapDL.

* List<String> _extraInputs_: extra inputs, as a list of paths
   relative to the project root directory. This parameter can be used
   to add dependency JARs whose resolutions has failed or extra code.

* Map<String, Object> _options_: the Doop options to use (empty for R8).

* String _cachePostDir_: path to write the data before posting the
  analysis to the server, so that it can be later replayed.

* String _convertUTF8Dir_: a directory of sources that must be
  converted to UTF-8. This parameter is supported for plain Java code.

* boolean _dry_: if `true`, then the artifact is not posted.

* boolean _ignoreConfigurations_: if 'true', no configurations will be
  posted.

* boolean _sources_: if 'false', no sources will be posted.

* String _repackageOutput_: the filename to use when saving the output
  of the automated repackaging task (optional).

* String _codeqlDatabase_: the path to the CodeQL database directory for
  this project (optional)

### Android-specific options ###

* String _subprojectName_: sub-project name (used when an `app`
  sub-directory contains the actual app code).

* String _buildType_: `"debug"` or `"release"` (default).

* boolean aab: 'true' to post generated AAB archives, 'false' (default)
  to post generated APK archives.

* String _flavor_: the name of the flavor to use. If not given,
  default tasks are used (such as 'assembleDebug'/'assembleRelease'
  in APK mode or 'bundleDebug'/'bundleRelease' in AAB mode).

* String _apkFilter_: if more than one .apk outputs are found, pick
  the one that contains this value as a substring.

* List _replacedByExtraInputs_: a list of string pairs of artifacts
  that are provided by _extraInputs_ and thus should not be resolved
  by the dependency resolver (e.g. `[ [ "com.android.support",
  "appcompat-v7" ], [ "com.android.support", "recyclerview-v7" ] ]`).

* boolean _printConfig_: if `true`, then a "print configuration" directive
  is used to gather the configuration rules and directives.

* String _signingConfig_: the name of the signing configuration
  (defined in build.gradle) to use when signing code via automated
  repackaging. To use this feature, you must set environment variable
  ANDROID_SDK to point to the Android SDK location.

## Automated repackaging ##

To post a build and automatically get back an optimized binary, first
add your optimization rules file in build.gradle:

```
clyze {
    ruleFile = 'rules.json'
}
```

Then, crate a build and post it for repackaging.

```
gradlew createBuild repackage
```

## Replay the post ##

When invoking the plugin with the ```cachePostDir``` option set to a
path, the build task will write the post state to that path.  You
can then trigger a replay of posting this state with the following
command:

```
./gradlew replay --fromDir [path-to-dir]
```
