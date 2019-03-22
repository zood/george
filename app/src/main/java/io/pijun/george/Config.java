package io.pijun.george;

public class Config {

    public static String apiAddress() {
        return "api.zood.xyz";
    }

    private static boolean apiSecure() {
        return true;
    }

    public static String httpScheme() {
        if (apiSecure()) {
            return "https";
        } else {
            return "http";
        }
    }

    public static String wsScheme() {
        if (apiSecure()) {
            return "wss";
        } else {
            return "ws";
        }
    }

}
