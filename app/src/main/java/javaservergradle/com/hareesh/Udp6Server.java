package javaservergradle.com.hareesh;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;

public class Udp6Server {

    private static Udp6Server instance;
    final int port = 9001; // Port for the UDP server
    String saveFilePath = "/home/hareesh/Desktop/code/JavaServerGradle/app/collect/received_file.txt";
    String ipv6Address = IPv6AddressFetcher.getIPv6Address(); 

    // Private constructor
    private Udp6Server() {
    }

    @SuppressWarnings("DoubleCheckedLocking") 
    public static Udp6Server getInstance() {
        if (instance == null) {
            synchronized (Udp6Server.class) {
                if (instance == null) {
                    instance = new Udp6Server(); 
                }
            }
        }
        return instance; 
    }

    public void startUdpServer() {
        if (ipv6Address == null) {
            System.out.println("No IPv6 address found. Exiting...");
            return; 
        }

        try (DatagramSocket socket = new DatagramSocket(port, Inet6Address.getByName(ipv6Address))) {
            System.out.println("UDP6 Server is listening on port " + port);

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
