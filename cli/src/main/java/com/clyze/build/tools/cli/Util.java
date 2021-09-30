package com.clyze.build.tools.cli;

import com.clyze.build.tools.Conventions;

public class Util {
    // Set to true for extra debug messages.
    private static final boolean debug = false;

    public static void logDebug(String s) {
        if (debug)
            System.err.println(Conventions.msg(s));
    }

    public static void println(String s) {
        System.out.println(Conventions.msg(s));
    }

    public static void logError(String s) {
        System.err.println(Conventions.msg(s));
    }
}
