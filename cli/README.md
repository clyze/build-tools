# Clyze CLI #

The Clyze CLI tool handles build integration with Maven/Gradle/Buck/Ant projects.

## Setup ##

* To install the tool in directory "build/install/cli", run:

```
../gradlew installDist
```

* Add the install directory to your path:

```
export PATH=$PATH:/path/to/build/install/cli/bin
```

## Use with Maven/Gradle/Ant ##

Step 1. Build your project (preferably without shrinking/obfuscation).

Step 2. Post a code snapshot with the following command:

```
cli
```

If the CLI fails to detect that this is a Maven/Gradle project, issue:

```
cli -b maven    # or cli -b gradle / cli -b ant
```

To set a custom server:

```
cli --host my.server.com --port 8090
```

## Use with Buck (Android) ##

Step 1. Build your project without shrinking/obfuscation. The easiest
way to do it is to add these lines to one of your .pro files used in
the build:

```
-dontshrink
-dontoptimize
-dontobfuscate
-printconfiguration configuration.txt
```

Then, build your project as usual:

```
buck clean; buck build
```

So far, you will have an output .apk and configuration.txt. Run the
following command to post the code with its configuration and its
sources (assumed to be in "android/java"):

```
cli --apk path/to/generated/app.apk --configuration path/to/configuration.txt --source-dir android/java
```

### Example: bucksamples ###

This example uses
[bucksamples](https://github.com/fbsamples/bucksamples). Assume the
server runs on localhost, port 8010. The following commands will build
the Android app and post it (with processed sources) to the server.

```
git clone https://github.com/fbsamples/bucksamples.git
cd bucksamples/cross-platform-scale-2015-demo
buck clean ; buck build demo_app_android
cli --apk buck-out/gen/android/demo-app.apk --source-dir "android/java" --source-dir "buck-out/bin/android/__demo-app#generate_rdot_java_rdotjava_src__" --port 8010
```
