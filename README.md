## Running the 'analyze' task on a Java application ##

Assume a Java application that can be built by OpenJDK and has a
build.gradle.

Step 1. Put these lines in build.gradle:

```
apply plugin: 'doop'
...
doop {
  host = ...
  port = ...
  username = ...
  password = ...
}
```

Step 2. Run the analyze task:

```
gradle analyze
```

## Running the 'analyze' task on an Android app ##

Assume an Android Studio project with the following structure:

```
Project
  +-- build.gradle
  +-- local.properties
  +-- Application
    +-- build.gradle
```

Step 1. Update local.properties to point to the Android SDK:

```
sdk.dir=/home/user/Android/Sdk
```

Step 2. Put these lines in Application/build.gradle:

```
apply plugin: 'doop'
...
doop {
  host = ...
  port = ...
  username = ...
  password = ...
  subprojectName = "Application"
  buildType = "debug"
}
```

Step 3. In directory "Project", run the analyze task:

```
./gradlew :Application:analyze
```

## Using HPROF information ##

To add HPROF information, these steps are required:

Step 1. Run the progam using an HPROF agent to produce the heap dump
(e.g., java.hprof). In JVM, this can be done as follows:

```
java -agentlib:hprof=heap=dump,format=b,depth=8 -jar Program.jar
```

In Android, for some App/Activity, generation of HPROF data differs:

```
adb shell am start --track-allocation App/Activity
```

and then convert the HPROF file using hprof-conv (found in the Android
SDK):

```
hprof-conv original.hprof java.hprof
```

For more details on how to obtain an HPROF file to use with Doop,
consult the [HeapDL documentation](https://github.com/plast-lab/HeapDL).

Step 2. Zip java.hprof to produce java.hprof.zip. (This step is
optional, you can upload the java.hprof file but it might be big.)

Step 3. In the 'doop' section in build.gradle, add this line:

```
hprof = 'java.hprof.zip'
```

## Interoperability with Ant ##

Basic Ant projects are supported via Gradle's Ant support.

A sample build.gradle file to run `gradle analyze` in an Ant project
is the following:

```
apply plugin: 'java'
apply plugin: 'application'
apply plugin: 'doop'

buildscript {
    repositories {
        // Put here the repositories needed to find the Doop plugin.
    }
    dependencies {
        classpath ('org.clyze:doop-gradle-plugin:2.0.+')
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

doop {
    host = clue_host
    port = clue_port as Integer
    username = clue_user
    password = clue_password

    // Put here the rest of the Doop options...

    // Build the sources archive separately and provide it here.
    useSourcesJar = "dist/source.zip"
}
```

## Flags ##

* String _host_: the host name of the server (e.g. `"localhost"`).

* int _port_: the server port.

* String _username_: the user name to use when posting the analysis to the server.

* String _password_: the password to use to authenticate to the server.

* String _orgName_: organization name of the artifact to post.

* String _projectName_: project name of the artifact to post.

* String _projectVersion_: project version of the artifact to post.

* String _useSourcesJar_: a sources JAR archive to be posted instead
  of using the output of the `sourcesJar` Gradle task. Used in Ant
  interoperability.

* String _hprof_: an HPROF file to be used in the analysis by HeapDL.

* List<String> _extraInputs_: extra inputs, as a list of paths
   relative to the project root directory. This parameter can be used
   to add dependency JARs whose resolutions has failed or extra code.

* Map<String, Object> _options_: the Doop options to use.

* boolean _cachePost_: cache the data before posting the analysis to
  the server, so that it can be later replayed.

* String _convertUTF8Dir_: a directory of sources that must be
  converted to UTF-8. This parameter is supported for plain Java code.

* boolean _dry_: if `true`, then the artifact is not uploaded to the
  server.

### Android-specific flags ###

* String _subprojectName_: sub-project name (used when an `app`
  sub-directory contains the actual app code).

* String _buildType_: `"debug"` or `"release"`.

* String _flavor_: the name of the flavor to use. If not given,
  default tasks are used (such as 'assembleDebug'/'assembleRelease').

* List _replacedByExtraInputs_: a list of string pairs of artifacts
  that are provided by _extraInputs_ and thus should not be resolved
  by the dependency resolver (e.g. `[ [ "com.android.support",
  "appcompat-v7" ], [ "com.android.support", "recyclerview-v7" ] ]`).