package javaservergradle.com.hareesh;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class IPv6AddressFetcher {
	public static String getIPv6Address() {
		try {
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

			while (networkInterfaces.hasMoreElements()) {
				NetworkInterface networkInterface = networkInterfaces.nextElement();

				// Check if the network interface is up and not a loopback interface
				if (networkInterface.isUp() && !networkInterface.isLoopback()) {
					Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();

					while (inetAddresses.hasMoreElements()) {
						InetAddress inetAddress = inetAddresses.nextElement();

						// Only return the first IPv6 address found
						if (inetAddress instanceof Inet6Address) {
							return inetAddress.getHostAddress();
						}
					}
				}
			}
		} catch (SocketException e) {
			System.out.println("Error while fetching IPv6 addresses: " + e.getMessage());
		}
		return null; // Return null if no IPv6 address is found
	}
}