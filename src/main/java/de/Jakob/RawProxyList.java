package de.Jakob;

import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RawProxyList {

    public static List<JsonObject> getTxtList(String url) {
        try {
            List<JsonObject> result = new ArrayList<>();

            OkHttpClient client = new OkHttpClient.Builder().build();
            for (String s : client.newCall(new Request.Builder()
                    .url(url)
                    .get()
                    .build()).execute().body().string().split("\n")) {
                String[] split = s.split(":");
                try {
                    if(split.length == 2)
                        result.add(ProxyJsonUtil.proxyEntry(split[0], Integer.parseInt(split[1])));
                } catch (NumberFormatException e) {}
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

}
