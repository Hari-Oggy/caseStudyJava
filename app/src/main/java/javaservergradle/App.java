package javaservergradle;

import java.util.Scanner;

import javaservergradle.com.hareesh.Tcp6Server;
import javaservergradle.com.hareesh.Udp6Server;




public class App {


    static public void selectServerTcpOrUdp(){
        System.out.println("-".repeat(20)+"Select TCP(1) or UDP(2)"+"-".repeat(20));
        Scanner sc = new Scanner(System.in);
        String choice ;
        String continues;
        
      try {
        do { 
            System.out.println("-".repeat(10)+"Enter your choice:"+"-".repeat(10));
            choice=sc.nextLine();
            int val =Integer.parseInt(choice);
            switch (val) {
                case 1 -> {
                    Tcp6Server server= Tcp6Server.getInstance();
                    server.getServerStart();
                    System.out.println("server started");
                }
                case 2 -> {
                    Udp6Server udp =  Udp6Server.getInstance();
                    udp.startUdpServer();
                }
                default -> {
                    System.out.println("Invalid choice! Please enter a number between 1 and 5.");
                    sc.close();
                    System.exit(0);
                }
                
            }
            System.out.println("Enter your (yes) if wnat continue:");
            continues = sc.nextLine();
        } while (continues.equals("yes"));
      } catch (Exception e) {
        System.out.println("error occured"+e.getMessage());
      }
    }
    public static void main(String[] args){
        selectServerTcpOrUdp();
    }

}
