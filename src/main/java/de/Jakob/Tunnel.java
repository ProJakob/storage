package de.Jakob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("CallToPrintStackTrace")
public class Tunnel {

    private static final AtomicInteger threadNumber = new AtomicInteger(1);
    private static final ExecutorService executor = Executors.newCachedThreadPool((task) -> {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.setName("Tunnel-Executor-" + threadNumber.getAndIncrement());
        return thread;
    });

    private static final Logger log = LoggerFactory.getLogger(Tunnel.class);

    private static boolean reloadingIPs;

    private static Deque<InetSocketAddress> ipQueue = new ArrayDeque<>();

    public static void main(String[] args) throws Exception {
        log.info("Loading proxy list...");

        reloadIPs();

        new AcceptorThread(new ServerSocket(1080)).start();
        log.info("Proxy listening on port 1080.");
    }

    public static InetSocketAddress ip() {
        InetSocketAddress addr = ipQueue.poll();
        if(addr != null)
            ipQueue.offer(addr);
        return addr;
    }

    public static void brokenIP(InetSocketAddress addr) {
        ipQueue.remove(addr);
    }

    public static void reloadIPs() {
        if(reloadingIPs) {
            log.warn("Already reloading list of IPs, please wait..");
            return;
        }

        reloadingIPs = true;
        ipQueue.clear();

        OkHttpClient client = new OkHttpClient.Builder().build();
        try(Response resp = client.newCall(new Request.Builder()
                .get()
                .url("https://proxy.navine.xyz/proxies")
                .build()).execute()) {
            if(resp.body() == null)
                return;

            JsonArray arr = JsonParser.parseString(resp.body().string()).getAsJsonObject().get("proxies").getAsJsonArray();

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            arr.add(ProxyJsonUtil.proxyEntry("localhost", 8001));

            int amount = 0;

            for (JsonElement ele : arr) {
                if(amount++ >= 100)
                    break;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    InetSocketAddress addr = new InetSocketAddress(
                            ele.getAsJsonObject().get("ip").getAsString(),
                            ele.getAsJsonObject().get("port").getAsInt()
                    );

                    try(Socket s = new Socket(new Proxy(Proxy.Type.SOCKS, addr))) {
                        s.connect(new InetSocketAddress("google.com", 80), 1000);
                        log.info("Checked IP " + addr);
                        return addr;
                    } catch (Exception e) {
                        log.info("IP " + addr + " is invalid!");
                        return null;
                    }
                }, executor).thenAccept((addr) -> {
                    if(addr != null) ipQueue.offer(addr);
                }));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            log.info("Loaded " + ipQueue.size() + " valid proxies.");
        } catch (Exception e) { e.printStackTrace(); }
        reloadingIPs = false;
    }

}
