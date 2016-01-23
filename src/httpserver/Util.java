package httpserver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

public class Util {
	
	
	/* This class contains misc helper functions and log stuff 
	 * Some of these methods are not actually used, but they were helpful for debugging
	 */

	public static final Logger classLogger = Logger.getLogger(CGI.class.getName());


	private static FileHandler logFileHandler = null;
	private static StreamHandler logStdOutHandler = null;

	public static void setFileHandler(FileHandler fh) {
		logFileHandler = fh;
		logFileHandler.setFormatter(new SimpleFormatter());
	}
	
	public static Map<String,String> mapIfValid(String name, String value, Map<String,String> map) {
		
		if (value.isEmpty() || value.trim().equals("") || value.equals(null)) {
			return(map);
		}

		map.put(name, value);
		return(map);
	}
	
	public static short getByteDivisor(long available) {
		
		/* http://www.evanjones.ca/software/java-bytebuffers.html
		   According to this guy's blog:
		   "OutputStream: When writing byte[] arrays larger than 8192 bytes, 
		   performance takes a hit. Read/write in chunks ≤ 8192 bytes"*/
		
		if (available <= 8192)
			return(1);
		
		for (short i=8192;i>1;i--) {
			if (available%i == 0) return(i);
		}
		
		return(1);
	}

	public static void setConsoleHandler(StreamHandler sh) {
		logStdOutHandler = sh;
		logStdOutHandler.setFormatter(new SimpleFormatter());
	}

	public static void writeBytesToSocket(InputStream requestData,
			DataOutputStream writeClient) throws IOException {
		System.out.println("writeBytesToSocket");
		byte b;
		while ((b = (byte) requestData.read()) != -1) {
			writeClient.write(b);
		}
	}

	public static String getStringFromDataStream(DataInputStream s) 
	//The advantage of this is that I can make it show up in log files
	
		throws IOException {
		String r = "";
		//while ((b = s.readByte()) != -1) {
		while (s.available() != 0) {
			r += (char)s.read();
		}
		return (r);
	}

	public static String getStringFromStackTrace(Exception e) {
		StringWriter out = new StringWriter();
		e.printStackTrace(new PrintWriter(out));
		String errString = out.toString();
		return (errString);
	}

	public static void flushLogHandlers() {

		try {
			logStdOutHandler.flush();
		} catch (NullPointerException e) {
			classLogger.finest("Won't flush console handler since console logging is disabled!");
		}

		try {
			logFileHandler.flush();
		} catch (NullPointerException e) {
			classLogger.finest("Won't flush file handler since file logging is disabled!");
		}
	}

	public static boolean checkPathExist(String path) {
		File file = new File(path);
		if (file.exists())
			return (true);
		else
			return (false);
	}

	public static Logger initClassLogger(String classname) {
		Logger logger = Logger.getLogger(classname);
		logger.setLevel(Config.logLevel);

		if (Config.enableConsoleLogging) {
			logStdOutHandler.setLevel(Config.logLevel);
			logger.addHandler(logStdOutHandler);
		}
		if (Config.enableFileLogging) {
			logFileHandler.setLevel(Config.logLevel);
			logger.addHandler(logFileHandler);
		}
		logger.setUseParentHandlers(false); // otherwise duplicate messages will
											// go to stderr

		return (logger);
	}

	public static String getTypeFromFile(Path filePath) throws IOException { 

		String fileType = Files.probeContentType(filePath);

		fileType = fileType.trim().toLowerCase();

		if (fileType.equals("null") || fileType.isEmpty()
				|| fileType.charAt('/') == -1) {
			return ("null"); // Just in case it's something weird I haven't
								// tested...
		} else {
			fileType = fileType.substring(0, fileType.indexOf("/"));
		}

		return (fileType);
	}

	public static String getStringFromReader(BufferedReader br)
			throws IOException {

		String AccumulateString = "";

		do {
			AccumulateString += (char) (br.read());
		} while (br.ready());
		return (AccumulateString);
	}

	public static int[] getByteFromReader(BufferedReader br, long size)
			throws IOException {

		System.out.println("SIZE: " + size);

		int[] CollectBytes = new int[(int) size];
		int count = 0;

		while (br.ready()) {
			CollectBytes[count] = br.read();
			count++;
		}
		return (CollectBytes);
	}

	public static boolean isValidTypeString(String fileType) {
		if (fileType.equals("null") || fileType.isEmpty() || fileType == null) { // File is good!
			return (false);
		}
		return (true);
	}
}
