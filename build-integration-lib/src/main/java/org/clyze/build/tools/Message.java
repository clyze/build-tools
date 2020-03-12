package org.clyze.build.tools;

import java.util.List;

/**
 * A message returned from the core library, so that it can be fed to
 * a logger or output in the console.
 */
public final class Message {
    public final Type type;
    public final String text;

    private enum Type { PRINT, WARNING, DEBUG }

    private Message(Type type, String text) {
        this.type = type;
        this.text = text;
    }

    public boolean isWarning() { return type == Type.WARNING; }
    public boolean isDebug()   { return type == Type.DEBUG;   }
    public boolean isPrint()   { return type == Type.PRINT;   }

    public static void warn(List<Message> messages, String text) {
        messages.add(new Message(Type.WARNING, text));
    }

    public static void debug(List<Message> messages, String text) {
        messages.add(new Message(Type.DEBUG, text));
    }

    public static void print(List<Message> messages, String text) {
        messages.add(new Message(Type.PRINT, text));
    }
}
