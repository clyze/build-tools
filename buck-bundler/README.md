# Buck bundler #

The Buck bundler tool handles projects that use the [https://github.com/facebook/buck/](Buck) build system.

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

First, build your project as usual:

```
buck clean; buck build
```

The bundler works by inspecting the trace of commands issued in a full
build of the project. To see the command line arguments of the
bundler, issue:

```
buck-bundler --help
```

### Example: bucksamples ###

This example uses
[bucksamples](https://github.com/fbsamples/bucksamples). Assume the
server runs on localhost, port 8010.

```
git clone https://github.com/fbsamples/bucksamples.git
cd bucksamples/cross-platform-scale-2015-demo
buck clean ; buck build demo_app_android
buck-bundler --apk buck-out/gen/android/demo-app.apk --source-dir "android/java" --source-dir "buck-out/bin/android/__demo-app#generate_rdot_java_rdotjava_src__" --port 8010
```
