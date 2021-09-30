package com.clyze.build.tools.cli;

import com.clyze.client.ConsolePrinter;
import com.clyze.client.web.Helper;
import com.clyze.client.web.PostOptions;
import com.clyze.client.web.PostState;
import org.apache.commons.cli.*;

import java.io.File;

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
            PostState ps = buildTool.generatePostState(config);
            PostOptions postOptions = config.postOptions;
            if (postOptions.dry)
                System.out.println("Dry mode enabled.");
            Helper.post(ps, postOptions, new File("cache"), null, new ConsolePrinter(debug), debug);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
}
