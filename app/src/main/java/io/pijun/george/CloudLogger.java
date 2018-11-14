package io.pijun.george;

public class CloudLogger {

    public static void log(Throwable t) {
        L.w(t.getLocalizedMessage(), t);
    }

    public static void log(String msg) {
        L.w(msg);
    }

}
