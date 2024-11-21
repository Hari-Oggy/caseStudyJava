package javaservergradle.com.hareesh;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Tcp6Server {
    private static Tcp6Server instance;
    final int port = 9000;
    private final Path saveFilePath;
    private final String addr6;
    private final ExecutorService executorService;
    private final ScheduledExecutorService metricsExecutor;
    private final AtomicLong totalBytesTransferred;
    private final int CHUNK_SIZE = 8192 * 1024;
    private final ConcurrentHashMap<String, TransferMetrics> activeTransfers;

    static class TransferMetrics {
        Instant startTime;
        Instant endTime;
        long bytesTransferred;
        long latency;

        TransferMetrics() {
            this.startTime = Instant.now();
            this.bytesTransferred = 0;
        }

        void complete() {
            this.endTime = Instant.now();
        }

        long getTransferTime() {
            return Duration.between(startTime, endTime).toMillis();
        }

        double getThroughput() {
            return (bytesTransferred / 1024.0 / 1024.0) / (getTransferTime() / 1000.0);
        }
    }

    private Tcp6Server() {
        // Create directory in the project root
        String currentDir = System.getProperty("user.dir");
        this.saveFilePath = Paths.get(currentDir, "collect2");
        this.addr6 = IPv6AddressFetcher.getIPv6Address();
        
        // Create storage directory
        createStorageDirectory();

        this.executorService = new ThreadPoolExecutor(
            4,
            8,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        this.metricsExecutor = Executors.newScheduledThreadPool(1);
        this.totalBytesTransferred = new AtomicLong(0);
        this.activeTransfers = new ConcurrentHashMap<>();

        metricsExecutor.scheduleAtFixedRate(
            this::logMetrics,
            1,
            1,
            TimeUnit.MINUTES
        );
    }

    private void createStorageDirectory() {
        try {
            // Create directory if it doesn't exist
            Files.createDirectories(saveFilePath);
            System.out.println("Storage directory created/verified at: " + 
                saveFilePath.toAbsolutePath());
            
            // Test write permissions
            Path testFile = saveFilePath.resolve("test.txt");
            Files.write(testFile, "test".getBytes());
            Files.delete(testFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create/access storage directory: " + 
                e.getMessage(), e);
        }
    }

    @SuppressWarnings("DoubleCheckedLocking")
    public static Tcp6Server getInstance() {
        if (instance == null) {
            synchronized (Tcp6Server.class) {
                if (instance == null) {
                    instance = new Tcp6Server();
                }
            }
        }
        return instance;
    }

    private void logMetrics() {
        System.out.println("\n=== Server Metrics ===");
        System.out.printf("Total data transferred: %.2f MB\n", 
            totalBytesTransferred.get() / 1024.0 / 1024.0);
        System.out.println("Active transfers: " + activeTransfers.size());
        System.out.println("===================\n");
    }

    public void getServerStart() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(addr6, port));
            System.out.println("TCP/IPv6 server listening on " + addr6 + " port: " + port);
            System.out.println("Files will be saved to: " + saveFilePath.toAbsolutePath());

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientId = clientSocket.getInetAddress().getHostAddress();
                System.out.println("Client connected: " + clientId);

                TransferMetrics metrics = new TransferMetrics();
                activeTransfers.put(clientId, metrics);

                long responseTime = System.currentTimeMillis();
                executorService.submit(() -> handleFileTransfer(clientSocket, clientId, metrics));
                metrics.latency = System.currentTimeMillis() - responseTime;
                
                System.out.println("Initial response time for " + clientId + ": " + 
                    metrics.latency + "ms");
            }
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void handleFileTransfer(Socket socket, String clientId, TransferMetrics metrics) {
        String fileName = "received_file_" + System.currentTimeMillis() + ".dat";
        Path outputFile = saveFilePath.resolve(fileName);

        try {
            // Ensure file doesn't exist
            if (Files.exists(outputFile)) {
                Files.delete(outputFile);
            }

            // Create new file
            Files.createFile(outputFile);

            try (InputStream inputStream = new BufferedInputStream(socket.getInputStream(), 
                    CHUNK_SIZE);
                 OutputStream outputStream = Files.newOutputStream(outputFile, 
                    StandardOpenOption.WRITE)) {

                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;
                long totalRead = 0;
                Instant transferStart = Instant.now();

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    metrics.bytesTransferred = totalRead;
                    totalBytesTransferred.addAndGet(bytesRead);

                    if (totalRead % (CHUNK_SIZE * 10) == 0) {
                        Duration elapsed = Duration.between(transferStart, Instant.now());
                        double speed = (totalRead / 1024.0 / 1024.0) / 
                            (elapsed.toMillis() / 1000.0);
                        System.out.printf("Transfer progress for %s: %.2f MB, Speed: %.2f MB/s\n",
                            clientId, totalRead / 1024.0 / 1024.0, speed);
                    }
                }

                outputStream.flush();
                metrics.complete();

                System.out.println("\n=== Transfer Completed ===");
                System.out.println("Client: " + clientId);
                System.out.println("Saved to: " + outputFile.toAbsolutePath());
                System.out.printf("File size: %.2f MB\n", totalRead / 1024.0 / 1024.0);
                System.out.printf("Transfer time: %d ms\n", metrics.getTransferTime());
                System.out.printf("Average throughput: %.2f MB/s\n", metrics.getThroughput());
                System.out.printf("Initial latency: %d ms\n", metrics.latency);
                System.out.println("=======================\n");

            } catch (IOException e) {
                System.out.println("File transfer failed for " + clientId + ": " + 
                    e.getMessage());
                Files.deleteIfExists(outputFile);
            }

        } catch (IOException e) {
            System.out.println("File creation failed: " + e.getMessage());
            try {
                Files.deleteIfExists(outputFile);
            } catch (IOException ex) {
                System.out.println("Failed to delete incomplete file: " + ex.getMessage());
            }
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Error closing socket: " + e.getMessage());
            }
            activeTransfers.remove(clientId);
        }
    }

    public void shutdown() {
        try {
            executorService.shutdown();
            metricsExecutor.shutdown();
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!metricsExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                metricsExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            metricsExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}