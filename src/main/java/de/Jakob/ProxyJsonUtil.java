package de.Jakob;

import com.google.gson.JsonObject;

public class ProxyJsonUtil {

    public static JsonObject proxyEntry(String ip, int port) {
        JsonObject obj = new JsonObject();
        obj.addProperty("ip", ip);
        obj.addProperty("port", port);
        return obj;
    }

}
