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
  annotationsVersion = "24.1.1"
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
java -agentlib:hprof=heap=dump,format=b,depth=8 Program.jar
```

In Android, for some App/Activity, generation of HPROF data differs:

```
adb shell am start --track-allocation App/Activity
```

and the convert the HPROF file using hprof-conv (found in the Android
SDK):

```
hprof-conv original.hprof java.hprof
```

Step 2. Zip java.hprof to produce java.hprof.zip. (This step is
optional, you can upload the java.hprof file but it might be big.)

Step 3. In the 'doop' section in build.gradle, add this line:

```
hprof = 'java.hprof.zip'
```
