package javaservergradle.com1.pubsub;
import  java.util.Scanner;

import redis.clients.jedis.Jedis;

public class RedisPublisher {
    String  URL ;
    int PORT;
    public RedisPublisher(String url,int port){
        this.URL=url;
        this.PORT = port;
    }


    public void redisServerPub(){
        try(Jedis jedis = new Jedis(URL,PORT)) {
            System.out.println("_".repeat(20)+"enter the message to publish"+"-".repeat(120));
            Scanner msg = new Scanner(System.in);
            String  message=  msg.nextLine();
            jedis.publish("my-channel", message);
        } catch (Exception e) {
            System.out.println("error occured in pub"+e.getMessage());
        }
    }

    
}