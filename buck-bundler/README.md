# Buck bundler #

The Buck bundler tool handles projects that use the [Buck](https://github.com/facebook/buck/) build system.

## Setup ##

* To install the tool in directory "build/install/buck-bundler", run:

```
./gradlew :buck-bundler:installDist
```

* Add the install directory to your path:

```
export PATH=$PATH:/path/to/build/install/buck-bundler/bin
```

## Use ##

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
buck-bundler --apk path/to/generated/app.apk --configuration path/to/configuration.txt --source-dir android/java
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
buck-bundler --apk buck-out/gen/android/demo-app.apk --source-dir "android/java" --source-dir "buck-out/bin/android/__demo-app#generate_rdot_java_rdotjava_src__" --port 8010
```

### Advanced ###

The bundler works by inspecting the trace of commands issued in a full
build of the project. To see the command line arguments of the
bundler, issue:

```
buck-bundler --help
```
