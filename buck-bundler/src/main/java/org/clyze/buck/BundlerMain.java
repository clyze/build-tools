package org.clyze.buck;

import org.clyze.build.tools.Conventions;
import org.clyze.build.tools.JcPlugin;

import java.io.File;

class BundlerMain {

    public static void main(String[] args) {
        System.out.println("Buck bundler main.");

        String javacPlugin = JcPlugin.getJcPluginArtifact();
        System.out.println("Using javacPlugin: " + javacPlugin);

        System.out.println("Using bundle directory: " + Conventions.CLUE_BUNDLE_DIR);
        new File(Conventions.CLUE_BUNDLE_DIR).mkdirs();

    }
}
