package com.clyze.build.tools.cli;

import com.clyze.build.tools.Conventions;
import com.clyze.client.ConsolePrinter;
import com.clyze.client.web.Helper;
import com.clyze.client.web.PostOptions;
import com.clyze.client.web.PostState;
import java.util.List;
import org.apache.commons.cli.*;

import static com.clyze.build.tools.cli.Util.println;

public class Main {

    public static void main(String[] args) {
        try {
            Config config = new Config(args);
            if (config.help) {
                config.printUsage();
                return;
            }
            String buildToolArg = config.buildTool;
            boolean debug = config.debug;
            if (debug)
                System.out.println("Debug mode enabled.");
            BuildTool buildTool = buildToolArg == null ? BuildTool.detect(config) : BuildTool.get(buildToolArg, config);
            if (buildTool == null) {
                System.out.println("ERROR: could not determine build tool, use --" + Config.OPT_BUILD_TOOL);
                return;
            }
            System.out.println("Assuming build tool: " + buildTool.getName());
            PostOptions postOptions = config.postOptions;
            if (postOptions.dry)
                println("Assembling snapshot (dry mode)...");
            else
                println("Posting snapshot to the server...");
            PostState ps = createPostState(buildTool, config);
            Helper.post(ps, postOptions, config.getCacheDir(), null, new ConsolePrinter(debug), debug);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private static PostState createPostState(BuildTool buildTool, Config config) {
        PostState ps = new PostState();
        ps.setId(Conventions.SNAPSHOT_ID);
        ps.addStringInput("API_VERSION", Conventions.API_VERSION);

        List<String> stacks = config.getPostOptions().stacks;
        ps.setStacks(stacks);
        System.out.println("Stacks: " + stacks);
        String platform = config.getPlatform();
        if (stacks.contains(Conventions.JVM_STACK)) {
            System.out.println("Assuming JVM stack.");
            ps.addStringInput(Conventions.JVM_PLATFORM, platform != null ? platform : Config.DEFAULT_JAVA_PLATFORM);
        } else if (stacks.contains(Conventions.ANDROID_STACK)) {
            System.out.println("Assuming Android stack.");
            ps.addStringInput(Conventions.ANDROID_PLATFORM, platform != null ? platform : Config.DEFAULT_ANDROID_PLATFORM);
        } else
            System.err.println("WARNING: unsupported stacks: " + stacks);
        buildTool.populatePostState(ps, config);
        return ps;
    }
}
