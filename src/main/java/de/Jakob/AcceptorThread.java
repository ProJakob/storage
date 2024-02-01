package de.Jakob;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

@SuppressWarnings("CallToPrintStackTrace")
public class AcceptorThread extends Thread {

    private static final Logger log = LoggerFactory.getLogger(AcceptorThread.class);

    private final ServerSocket serverSocket;

    public AcceptorThread(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    @Override
    public void run() {
        while (serverSocket.isBound()) {
            try {
                Socket s = serverSocket.accept();

                log.info("Client from " + s.getInetAddress() + " established connection.");
                InetSocketAddress ip = Tunnel.ip();

                int attempts = 0;
                while (ip == null && ++attempts <= 5) {
                    log.warn("No available IPs, reloading list. (Attempt " + attempts + ")");
                    Tunnel.reloadIPs();
                    ip = Tunnel.ip();
                }

                if(ip == null) {
                    log.warn("Failed to assign a valid IP to client " + s.getInetAddress());
                    s.close();
                    continue;
                }

                log.info("Assigned IP " + ip + " to client " + s.getInetAddress());

                new TunnelThread(s, ip).start();
            } catch (RuntimeException e) {
                if(e.getCause() != null)
                    e.getCause().printStackTrace();
                else e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
