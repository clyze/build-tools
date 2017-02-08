## Running the 'analyse' task on a Java application ##

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

Step 2. Run the analyse task:

```
gradle analyse
```

## Running the 'analyse' task on an Android app ##

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

Step 3. In directory "Project", run the analyse task:

```
./gradlew :Application:analyse
```
