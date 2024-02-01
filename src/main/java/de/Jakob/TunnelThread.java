package de.Jakob;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TunnelThread extends Thread {

    private static final Logger log = LoggerFactory.getLogger(TunnelThread.class);

    private final Socket socket;
    private final Socket target;

    public TunnelThread(Socket socket, InetSocketAddress addr) {
        this.socket = socket;
        try {
            this.target = new Socket();
            this.target.connect(addr);

            socketInput = socket.getInputStream();
            socketOutput = socket.getOutputStream();
            targetInput = target.getInputStream();
            targetOutput = target.getOutputStream();
        } catch (IOException e) {
            try {
                socket.close();
                log.warn("IP " + addr + " failed to connect! Removing it.");
                Tunnel.brokenIP(addr);
            } catch (Exception ignored) {}
            throw new RuntimeException(e);
        }
    }

    private final InputStream socketInput;
    private final OutputStream socketOutput;
    private final InputStream targetInput;
    private final OutputStream targetOutput;

    private Thread thread1, thread2;

    @Override
    public void run() {
        thread1 = redirectThread(socketInput, targetOutput, true);
        thread2 = redirectThread(targetInput, socketOutput, false);

        thread1.start();
        thread2.start();

        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {}

        try {
            thread1.interrupt();
            thread2.interrupt();

            socketInput.close();
            socketOutput.close();
            targetInput.close();
            targetOutput.close();
            socket.close();
            target.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info("Tunnel for " + socket.getInetAddress() + " stopped.");
    }

    private Thread redirectThread(InputStream is, OutputStream os, boolean log) {
         return new Thread(() -> {
            byte[] buffer = new byte[4096];
            try {
                while (!isInterrupted()) {
                    int bytesRead = is.read(buffer);
                    if(bytesRead == -1) break;
                    os.write(buffer, 0, bytesRead);
                    os.flush();

                    if(log) {
                        for (int i = 0; i < bytesRead; i++) {
                            byte b = buffer[i];
                            //System.out.printf("%02x ", b);
                        }
                        parseSocksPacket(Arrays.copyOfRange(buffer, 0, bytesRead));
                    }
                }
            } catch (IOException e) {}
        });
    }

    private int stage = 0;
    private void parseSocksPacket(byte[] data) {
        if(data.length >= 2) {
            if(data[0] == 0x05) {
                if(stage == 0) {
                    stage++;
                    List<String> authSchemes = getAuthSchemes(data);
                    log.info("Client supports: " + String.join(", ", authSchemes));
                } else if(stage == 1) {
                    stage++;
                    ByteBuffer buf = ByteBuffer.wrap(data, 1, data.length - 1);
                    byte cmd = buf.get();
                    byte mustNull = buf.get();
                    byte type = buf.get();
                    //System.out.printf("type: %02x\n", type);
                    //System.out.printf("cmd: %02x\n", cmd);
                    if(mustNull == 0x00 && type == 0x03) {
                        byte len = buf.get();
                        byte[] strBytes = new byte[len];
                        buf.get(strBytes);
                        int port = ((buf.get() & 0xff00) << 8) | (buf.get() & 0xff);
                        log.info("Dest Domain: " + new InetSocketAddress(new String(strBytes, StandardCharsets.ISO_8859_1), port));
                    } else if(mustNull == 0x00 && type == 0x04) {
                        byte[] v6Bytes = new byte[16];
                        buf.get(v6Bytes);
                        int port = ((buf.get() & 0xff00) << 8) | (buf.get() & 0xff);
                        try {
                            log.info("Dest IPv6: " + new InetSocketAddress(InetAddress.getByAddress(v6Bytes), port));
                        } catch (Exception e) {
                            System.out.println("Dest IPv6: " + Arrays.toString(v6Bytes) + ":" + port);
                        }
                    } else if(mustNull == 0x00 && type == 0x01) {
                        byte[] v4Bytes = new byte[4];
                        buf.get(v4Bytes);
                        int port = ((buf.get() & 0xff00) << 8) | (buf.get() & 0xff);
                        try {
                            System.out.println("Dest IPv4: " + new InetSocketAddress(InetAddress.getByAddress(v4Bytes), port));
                        } catch (Exception e) {
                            System.out.println("Dest IPv4: " + Arrays.toString(v4Bytes) + ":" + port);
                        }
                    }

                    /*try {
                        socketOutput.write(0x05);
                        socketOutput.write(0x02);
                        socketOutput.flush();
                        Thread.sleep(100);
                        TunnelThread.this.interrupt();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                }
            }
        }
    }

    @NotNull
    private static List<String> getAuthSchemes(byte[] data) {
        List<String> authSchemes = new ArrayList<>();
        for (int i = 0; i < data[1]; i++) {
            int scheme = data[2 + i] & 0xff;
            if(scheme == 0x00)
                authSchemes.add("No Auth");
            else if(scheme == 0x01)
                authSchemes.add("GSSAPI");
            else if(scheme == 0x02)
                authSchemes.add("Username/Password");
            else if(scheme <= 0x7F)
                authSchemes.add("IANA assigned");
            else if(scheme <= 0xFE)
                authSchemes.add("Private Method (" + String.format("%02x", scheme) + ")");
            else
                authSchemes.add("No acceptable Methods");
        }
        return authSchemes;
    }
}
