## Running the 'scavenge' task on an Android app ##

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
  annotationsVersion = "24.1.1"
  subprojectName = "Application"
  buildType = "debug"
}
```

Step 3. In directory "Project", generate R.java and class files:

```
./gradlew assemble
```

Step 4. Compile R.java to a custom directory under the "Application"
directory:

```
cd Application
mkdir -p R-class
javac -d R-class build/generated/source/r/debug/com/example/android/camera2basic/R.java
```

Step 5. In directory "Project", run the scavenge task:

```
./gradlew :Application:scavenge
```