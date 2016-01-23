
package httpserver;

/*
 * Goals:
 * -Create a basic httpd-like program using java netsockets
 * 
 * Features:
 * -Support for several connections at once using java threading
 * -Support for cgi scripts using java ProcessBuilder
 * -A text-based configuration file
 * -PUT, POST, HEAD, GET, DELETE, and proper response headers
 * -URL decoding
 * -Optional file and console logging
 * 
 * Things I could have added, but ran out of time:
 * -Gzip compression (java.util.zip.GZIPInputStream)
 * -Caching in RAM
 * -QUERY_STRING for CGI on forms
 * -Compatibility with http/1.0
 * -Something fun with OPTIONs, TRACE, or other obscure http methods
 * 
 */

import java.io.*;
import java.net.*;
import java.util.logging.Logger;
import java.math.BigInteger;

public class HttpServer {
	
	public static boolean done = false;
	private static Logger classLogger;// = Util.initClassLogger(Connection.class.getName());

	public static void main(String[] args) throws IOException {
		
		Config mainconfig;
		String cfgPath="javaHttpd.conf";
		
		if (args.length > 0) {
			if (Util.checkPathExist(args[1])) {
				System.out.println("Using specified config file");
				cfgPath = args[1];
			}
			else System.out.println("No config file found: " + args[1] + " found\nUsing default");
		}

		mainconfig = new Config(cfgPath);
		classLogger = Util.initClassLogger(Connection.class.getName());
        classLogger.info("Waiting for connections...");
        
        BigInteger connectionCount = new BigInteger("0");
        
      	ServerSocket listen = new ServerSocket(Config.port); 
      	Web.initMessages();
        
        while(!done){

        	Util.flushLogHandlers();
        	//Creates a new thread when listen catches something on its port
        	(new Thread(new Connection(listen.accept()))).start();
        	connectionCount = connectionCount.add(BigInteger.ONE);
        	classLogger.fine("Connection #" + connectionCount);
        }
        
      listen.close();

	}

}
