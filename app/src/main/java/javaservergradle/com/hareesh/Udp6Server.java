package javaservergradle.com.hareesh;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;

public class Udp6Server {
    // Singleton instance
    private static Udp6Server instance;
    final int port = 9000; // Port for the UDP server
    String saveFilePath = "../../../../../../../savedfile/received_fileUdp6.txt"; // Path for saving received files
    String ipv6Address = IPv6AddressFetcher.getIPv6Address(); // Fetch the IPv6 address

    // Private constructor
    private Udp6Server() {
    }

    @SuppressWarnings("DoubleCheckedLocking") // Suppress warning for double-checked locking
    public static Udp6Server getInstance() {
        if (instance == null) {
            synchronized (Udp6Server.class) {
                if (instance == null) {
                    instance = new Udp6Server(); // Create instance
                }
            }
        }
        return instance; // Return the single instance
    }

    public void startUdpServer() {
        if (ipv6Address == null) {
            System.out.println("No IPv6 address found. Exiting...");
            return; // Exit if no IPv6 address
        }

        try (DatagramSocket socket = new DatagramSocket(port, Inet6Address.getByName(ipv6Address))) {
            System.out.println("UDP Server is listening on port " + port);

            // Create a buffer for receiving data
            byte[] buffer = new byte[4096];
            try (FileOutputStream fileOutputStream = new FileOutputStream(saveFilePath)) {
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet); // Receive the packet

                    // Write data to file
                    fileOutputStream.write(packet.getData(), 0, packet.getLength());
                    System.out.println("Received packet from: " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
                }
            }
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
        }
    }
}
