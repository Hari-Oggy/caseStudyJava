package javaservergradle.com.hareesh;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Udp6Server {
    private static Udp6Server instance;
    final int port = 9001;
    private String saveFilePath;
    private final String ipv6Address;
    private static final int BUFFER_SIZE = 4096;
    private static final int HEADER_SIZE = 8;
    private static final byte[] END_MARKER = "END_OF_FILE".getBytes();
    
    // Performance metrics
    private static class PerformanceMetrics {
        long totalBytes;
        long packetCount;
        long lostPackets;
        long duplicatePackets;
        double averageLatency;
        double maxLatency;
        double minLatency = Double.MAX_VALUE;
        double jitter;
        List<Double> latencies = new ArrayList<>();
        Instant startTime;
        Map<Integer, Long> packetSendTimes = new HashMap<>();
        
        PerformanceMetrics() {
            this.startTime = Instant.now();
        }
        
        void updateLatency(double latency) {
            latencies.add(latency);
            averageLatency = latencies.stream().mapToDouble(d -> d).average().orElse(0.0);
            maxLatency = Math.max(maxLatency, latency);
            minLatency = Math.min(minLatency, latency);
            
            // Calculate jitter (variation in latency)
            if (latencies.size() > 1) {
                double[] differences = new double[latencies.size() - 1];
                for (int i = 0; i < latencies.size() - 1; i++) {
                    differences[i] = Math.abs(latencies.get(i + 1) - latencies.get(i));
                }
                jitter = Arrays.stream(differences).average().orElse(0.0);
            }
        }
        
        void displayMetrics() {
            Duration totalTime = Duration.between(startTime, Instant.now());
            double throughput = (totalBytes / 1024.0 / 1024.0) / Math.max(1, totalTime.toSeconds());
            
            System.out.println("\n=== Performance Metrics ===");
            System.out.printf("Transfer Rate: %.2f MB/s\n", throughput);
            System.out.printf("Average Latency: %.2f ms\n", averageLatency);
            System.out.printf("Min Latency: %.2f ms\n", minLatency);
            System.out.printf("Max Latency: %.2f ms\n", maxLatency);
            System.out.printf("Jitter: %.2f ms\n", jitter);
            System.out.printf("Packet Loss Rate: %.2f%%\n", 
                (lostPackets * 100.0) / Math.max(1, packetCount));
            System.out.printf("Duplicate Packets: %d\n", duplicatePackets);
            System.out.printf("Total Time: %d seconds\n", totalTime.toSeconds());
            System.out.println("=========================");
        }
    }

    private Udp6Server() {
        this.saveFilePath = "received_files/";
        this.ipv6Address = IPv6AddressFetcher.getIPv6Address();
        createSaveDirectory();
    }

    private void createSaveDirectory() {
        File directory = new File(saveFilePath);
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                System.out.println("Created directory: " + directory.getAbsolutePath());
            } else {
                System.out.println("Failed to create directory. Using current directory.");
                this.saveFilePath = "./collect2/";
                new File(saveFilePath).mkdirs();
            }
        }
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

        try (DatagramSocket socket = new DatagramSocket(port, 
                Inet6Address.getByName(ipv6Address))) {
                
            System.out.println("UDP6 Server listening on " + ipv6Address + ":" + port);
            System.out.println("Files will be saved to: " + new File(saveFilePath).getAbsolutePath());
            
            while (true) {
                PerformanceMetrics metrics = new PerformanceMetrics();
                try {
                    receiveFile(socket, metrics);
                } catch (SocketTimeoutException e) {
                    System.out.println("Socket timeout - waiting for new connection");
                } catch (IOException e) {
                    System.out.println("Error receiving data: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
        }
    }

    private void receiveFile(DatagramSocket socket, PerformanceMetrics metrics) 
            throws IOException {
        // First packet contains file information
        byte[] infoBuffer = new byte[BUFFER_SIZE];
        DatagramPacket infoPacket = new DatagramPacket(infoBuffer, infoBuffer.length);
        
        // Measure initial response time
        Instant requestStart = Instant.now();
        socket.receive(infoPacket);
        double initialResponseTime = Duration.between(requestStart, Instant.now()).toNanos() / 1_000_000.0;
        System.out.printf("Initial Response Time: %.2f ms\n", initialResponseTime);

        ByteBuffer infoData = ByteBuffer.wrap(infoPacket.getData(), 0, infoPacket.getLength());
        long fileSize = infoData.getLong();

        if (fileSize <= 0 || fileSize > 1024 * 1024 * 1024) {
            System.out.println("Invalid file size received: " + fileSize + " bytes");
            return;
        }

        System.out.printf("Receiving file of size: %.2f MB\n", fileSize / (1024.0 * 1024.0));

        String fileName = String.format("received_file_%d.dat", System.currentTimeMillis());
        File outputFile = new File(saveFilePath, fileName);

        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long totalBytesReceived = 0;
            int lastProgressPercent = 0;
            Set<Integer> receivedSequences = new HashSet<>();

            while (totalBytesReceived < fileSize) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                Instant packetStart = Instant.now();
                socket.setSoTimeout(5000);
                socket.receive(packet);

                // Measure packet latency
                double latency = Duration.between(packetStart, Instant.now()).toNanos() / 1_000_000.0;
                metrics.updateLatency(latency);

                if (isEndMarker(packet)) {
                    break;
                }

                ByteBuffer packetData = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                int sequenceNumber = packetData.getInt();
                int dataLength = packetData.getInt();

                // Check for duplicate packets
                if (receivedSequences.contains(sequenceNumber)) {
                    metrics.duplicatePackets++;
                    continue;
                }

                if (dataLength <= 0 || dataLength > BUFFER_SIZE - HEADER_SIZE) {
                    metrics.lostPackets++;
                    continue;
                }

                receivedSequences.add(sequenceNumber);
                byte[] data = new byte[dataLength];
                packetData.get(data);
                fileOutputStream.write(data);
                
                totalBytesReceived += dataLength;
                metrics.totalBytes = totalBytesReceived;
                metrics.packetCount++;

                // Update progress
                int progressPercent = (int)((totalBytesReceived * 100) / fileSize);
                if (progressPercent > lastProgressPercent) {
                    System.out.printf("\rProgress: %d%% (%.2f MB / %.2f MB) | Latency: %.2f ms", 
                        progressPercent,
                        totalBytesReceived / (1024.0 * 1024.0),
                        fileSize / (1024.0 * 1024.0),
                        latency);
                    lastProgressPercent = progressPercent;
                }

                // Send acknowledgment with timing information
                sendAcknowledgment(socket, packet.getAddress(), packet.getPort(), 
                    sequenceNumber, latency);
            }

            System.out.println("\nFile received successfully:");
            System.out.println("Saved as: " + outputFile.getAbsolutePath());
            metrics.displayMetrics();

        } catch (IOException e) {
            System.out.println("\nError while receiving file: " + e.getMessage());
            if (outputFile.exists()) {
                outputFile.delete();
                System.out.println("Deleted incomplete file: " + outputFile.getName());
            }
        }
    }

    private void sendAcknowledgment(DatagramSocket socket, InetAddress address, 
            int port, int sequenceNumber, double latency) throws IOException {
        ByteBuffer ackBuffer = ByteBuffer.allocate(16);
        ackBuffer.putInt(sequenceNumber);
        ackBuffer.putDouble(latency);
        
        DatagramPacket ackPacket = new DatagramPacket(
            ackBuffer.array(),
            ackBuffer.array().length,
            address,
            port
        );
        socket.send(ackPacket);
    }

    private boolean isEndMarker(DatagramPacket packet) {
        if (packet.getLength() != END_MARKER.length) {
            return false;
        }
        byte[] data = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());
        return new String(data).equals(new String(END_MARKER));
    }
}