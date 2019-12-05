package org.clyze.buck;

import org.clyze.build.tools.JcPlugin;

class BundlerMain {

    public static void main(String[] args) {
        System.out.println("Buck bundler main.");

        String javacPlugin = JcPlugin.getJcPluginArtifact();
        System.out.println("Using javacPlugin: " + javacPlugin);
    }
}
