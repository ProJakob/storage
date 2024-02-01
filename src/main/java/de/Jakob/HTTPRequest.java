package de.Jakob;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.net.InetSocketAddress;
import java.net.Proxy;

public class HTTPRequest {

    public static void main(String[] args) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost",1080)))
                .build();

        Request request = new Request.Builder()
                .url("http://152.89.239.25")
                .get()
                .build();

        Response response = client.newCall(request).execute();

        System.out.println(response.body().string());
    }

}
