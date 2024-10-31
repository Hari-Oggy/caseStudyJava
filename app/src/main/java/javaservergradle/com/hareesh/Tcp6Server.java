

package javaservergradle.com.hareesh;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import redis.clients.jedis.Jedis;

public class Tcp6Server {
    private static Tcp6Server instance; // Static instance for Singleton
    final int port = 9000;
    String saveFilePath = "../../../../../../../savedfile/received_file.txt";
    String addr6 = IPv6AddressFetcher.getIPv6Address();

    // Private constructor to prevent instantiation
    private Tcp6Server() {
          Jedis jedis = new Jedis("localhost", 6379); 
    }

    // Public method to provide access to the instance
    @SuppressWarnings("DoubleCheckedLocking")
    public static Tcp6Server getInstance() {
        if (instance == null) { // Check if instance is null
            synchronized (Tcp6Server.class) {
                if (instance == null) { // Double-checked locking
                    instance = new Tcp6Server();
                }
            }
        }
        return instance; // Return the single instance
    }

    public void getServerStart() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(addr6, port)); // Bind directly to IPv6 address
            System.out.println("Server is listening on IPv6 address " + addr6 + " port: " + port);

            while (true) {
                try (Socket socket = serverSocket.accept();
                     InputStream inputStream = socket.getInputStream();
                     FileOutputStream fileOutputStream = new FileOutputStream(saveFilePath)) {

                    System.out.println("Client connected: " + socket.getInetAddress());

                    byte[] buffer = new byte[4096];
                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead);
                    }
                    System.out.println("File received and saved to " + saveFilePath);
                } catch (IOException e) {
                    System.out.println("File transfer failed: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
        }
    }
}
