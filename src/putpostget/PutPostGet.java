package putpostget;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class PutPostGet {
	
	
	/** A small tool to poke my server with
	 *  Can be used to verify that POST/PUT work without CGI scripts
	 *  Can also test DELETE
	 */
	public static final Scanner input = new Scanner(System.in);
	public static String address = "127.0.0.1";
	public static String method = "GET";
	public static String resource = "/";
	public static int port = 80;

	public static void main(String[] args) throws UnknownHostException, IOException {

		
		System.out.println("Press enter 4 times with server running for quick demo");

		address = getAddress();
		method = getMethod();
		port = getPort();
		resource = getResource();
		
		String requestLine = method + " " + resource + " HTTP/1.1\r\n";
		String toSend = "";

		if (method.equals("POST") || method.equals("PUT")) {
			System.out.println("Enter post data to send (type \".\" to stop)");
			String line = "";
			String payload = "";
			while (true) {
				line = input.nextLine();
				if (line.equals("."))
					break;
				payload += line + "\n";
			}

			toSend = requestLine + "Connection: close\n"
					+ "Host: " + InetAddress.getLocalHost().getHostAddress() + "\n"
					+ "User-Agent: PutPostGet/0.1\n" 
					+ "Content-Length: " + payload.length() + "\n"
					+ "Content-Type: application/x-www-form-urlencoded"
					+ "\r\n" + payload;
		}
		else if (method.equals("GET") || method.equals("DELETE")) {
			toSend = requestLine + "Connection: close\n"
					+ "Host:" + InetAddress.getLocalHost().getHostAddress() + "\n"
					+ "User-Agent: PutPostGet/0.1\n";
		}

		System.out.println("\n\nRequest to send:\n" + toSend);

		Socket socket = new Socket(address, port);

		InputStream sockin = socket.getInputStream();
		OutputStream sockout = socket.getOutputStream();

		sockout.write(toSend.getBytes());
		sockout.flush();

		String response = "";
		boolean recieved = false;
		while (true) {
			int data = sockin.read();
			if (data == -1 && recieved)
				break;
			if (data != -1)
				recieved = true;
			response += (char) data;
		}

		System.out.println("\n\nThe server responded:\n" + response);
		socket.close();
	}

	public static int getPort() {
		System.out.println("Enter a port (default " + port + ")");
		try {
			return (Integer.parseInt(input.nextLine()));
		} catch (NoSuchElementException | IllegalStateException | NumberFormatException e) {
			return (port);
		}
	}

	public static String getResource() {
		System.out.println("Enter a resource (method argument) (default "
				+ resource + ")");
		try {
			String res = input.nextLine();
			if (res.isEmpty()) throw new NoSuchElementException();
			return (res);
		} catch (NoSuchElementException | IllegalStateException e) {
			return (resource);
		}
	}

	public static String getAddress() {
		System.out.println("Enter an address (default " + address + ")");
		try {
			return (input.nextLine());
		} catch (NoSuchElementException | IllegalStateException e) {
			System.out.println("Try again");
			return (address);
		}
	}

	public static String getMethod() {
		System.out.println("What method would you like to use? (default is GET)");
		String methods[] = { "GET", "POST", "PUT","DELETE" };
		System.out.println("[1] GET");
		System.out.println("[2] POST");
		System.out.println("[3] PUT");
		System.out.println("[4] DELETE");
		try {
			return (methods[Integer.parseInt(input.nextLine())-1]);
		} catch (NoSuchElementException | IllegalStateException | NumberFormatException e) {
			return ("GET");
		}
	}

}
