package javaservergradle.com1.pubsub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class RedisReceiver {
    String url;
    int port;

    public RedisReceiver(String url, int port) {
        this.url = url;
        this.port = port;
    }

    public void subscriber() {
        // Create a new thread to run the subscription

            try (Jedis jedis = new Jedis(url, port)) {
                JedisPubSub sub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        System.out.println("Received message from channel " + channel + ": " + message);
                    }
                };

                // Subscribe to a channel (for example, "my-channel")
                jedis.subscribe(sub, "my-channel");
            } catch (Exception e) {
                System.out.println("Error occurred: " + e.getMessage());
            }

    }


}
