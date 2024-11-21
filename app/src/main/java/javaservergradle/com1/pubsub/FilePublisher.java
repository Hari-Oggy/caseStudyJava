package javaservergradle.com1.pubsub;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import redis.clients.jedis.Jedis;

public class FilePublisher {
    private static final String CHANNEL_NAME = "file_channel";

    public static void main(String[] args) throws IOException {
        String filePath = "/app/pubcollect/file.txt";
        Jedis jedis = new Jedis("localhost");

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jedis.publish(CHANNEL_NAME, line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            jedis.close();
        }

        System.out.println("File published successfully!");
    }
}
