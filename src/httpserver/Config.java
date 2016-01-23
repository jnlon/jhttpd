package httpserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;

public class Config {
	
	/*
	 * Holds global variables, some of which are read from javaHttpd.conf
	 * 
	 */
	
	
	//Internal, not parsed in Config
	private static Logger classLogger;// = Logger.getLogger(Config.class.getName());
	
	public static Level logLevel; 
	public static String localHost = "";
	private static String userDir = System.getProperty("user.dir");
	public static String logFilePath;
	
	public static boolean enableCGI = true;
	//Raw means without going through a cgi script
	public static boolean enablePutAndRawPost = true; 
	public static boolean enableConsoleLogging = true;
	public static boolean enableFileLogging = true;
	public static boolean enablePutOverwrite = true;
	public static boolean enableDirView = true;
	public static boolean enableDelete = true;
	public static short port = 80;
	public static String fileRootPath = userDir + "/www/httpd"; //should be java.class.path + ../ ?
	public static String defaultFilePath = fileRootPath + "/index.html";
	public static String logPath = userDir + "/log/";
	public static String cgiPath = fileRootPath + "/cgi/";
	public static String logIntensity = "vvvvvvv";
	public static String serverName = "jhttpd";
	
	public Config(String path) {

		File configFile = new File(path);
		try {
			System.out.println("Found config File: " + configFile.getPath());
			parseConfig(configFile);
		}
		catch (IOException s){
			System.err.println("Warning: Using default config");
		}
		
		initConfig();
		printConfig();
	}
	
	
	private void parseConfig(File configFile) throws FileNotFoundException {
		
		Scanner configScanner = new Scanner(configFile);
		long linecount = 0;
		
		while (configScanner.hasNextLine()) {
			String line = configScanner.nextLine().trim();
			linecount++;
			
			//if line is empty, a comment, or has no equal sign, it's invalid
			
			boolean validLine = true;

			System.out.println(line);
			
			if  (line.isEmpty() || line.charAt(0) == '#' || !line.contains("=")) {
				validLine = false;
			}
			
			if (validLine) {
				
				String answer = line.substring(line.indexOf("=")+1,line.length()).trim();
				
				String key = line.substring(0,line.indexOf("=")).trim().toLowerCase(); //case insensitive keys

				switch (key) { //everything on right of '=' gets assigned to its variable
					
					/*Boolean*/
					case "enablecgi": enableCGI = Boolean.valueOf(answer); break;
					case "enableputandrawpost": enablePutAndRawPost = Boolean.valueOf(answer); break;
					case "enableputoverwrite": enablePutOverwrite = Boolean.valueOf(answer);
					case "enableconsolelogging": enableConsoleLogging = Boolean.valueOf(answer); break;
					case "enablefilelogging": enableFileLogging = Boolean.valueOf(answer); break;
					case "enabledirview": enableDirView = Boolean.valueOf(answer); break;
					case "enabledelete": enableDelete = Boolean.valueOf(answer); break;
					
					/*Strings*/
					case "logintensity": logIntensity = answer; break;
					case "filerootpath": fileRootPath = answer; break;
					case "logpath": logPath = answer; break;
					case "cgipath": cgiPath = answer; break;
					case "defaultfilepath": defaultFilePath = answer; break;
					case "servername": serverName = answer; break;
					
					case "port": port = Short.parseShort(answer); break;
					default: System.err.println("Error parsing config, unknown variable at line " + linecount); break;
				}
			}
			else {
				System.out.println("Skipped '" + line + "'");
			}
		}
		configScanner.close();
	}
	
	public void initConfig() {
		//In this method, checks for slashes are performed, and file paths are created if they don't already exist
		
		switch (logIntensity.toLowerCase()) {
			case "v": logLevel = Level.SEVERE; break; 
			case "vv": logLevel = Level.WARNING; break;
			case "vvv": logLevel = Level.INFO; break;
			case "vvvv": logLevel = Level.CONFIG; break;
			case "vvvvv": logLevel = Level.FINE; break;
			case "vvvvvv": logLevel = Level.FINER; break;
			case "vvvvvvv": logLevel = Level.ALL; break;
			default: logLevel = Level.ALL;
		}
		
		if (enableConsoleLogging) {
			System.out.println("Console logging has been enabled");
			Util.setConsoleHandler(new StreamHandler(System.out, new SimpleFormatter()));
		}
		
		if (enableFileLogging) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd-hh.mm.ss");
			if (!logPath.isEmpty() && !String.valueOf(logPath.trim().charAt(logPath.length()-1)).equals(System.getProperty("file.separator")))  //ugliest thing I've ever written
				logPath += System.getProperty("file.separator");
			logFilePath = logPath + "httpd-" + dateFormat.format(new GregorianCalendar().getTime()) + ".log";
			try {
				Util.setFileHandler(new FileHandler(logFilePath));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		classLogger = Util.initClassLogger(Logger.class.getName());
		
		File cgiDir = new File(cgiPath);
		File logDir = new File(logPath);
		File fileRootDir = new File(fileRootPath);
		File defaultFile = new File(defaultFilePath);

		//TODO: Log if this fails? mkdirs() won't throw an exception, but will return false...
		if (cgiDir.mkdirs())
			System.out.println(cgiPath + " created");
		if (logDir.mkdirs())
			System.out.println(logPath + " created");
		if (fileRootDir.mkdirs())
			classLogger.log(Level.INFO , fileRootPath + " created");
		
		if (!defaultFile.exists()) 
			System.out.println("Warning: Default file " + defaultFilePath + " does not exist");
		
				
		try {
              localHost = InetAddress.getLocalHost().getCanonicalHostName();
		}
		catch (UnknownHostException e) {
			  localHost = "";
			  System.out.println("Cannot determine hostname!");
		}
	}
	
	
	private void printConfig() {
		String LF = "\n";
		classLogger.config("\n" +
		                 "enableCGI: " + enableCGI + LF + 
		                 "enableConsoleLogging: " + enableConsoleLogging + LF + 
		                 "enableFileLogging: " + enableFileLogging + LF + 
		                 "enableDelete: " + enableDelete + LF + 
		                 "fileRootPath: " + fileRootPath + LF + 
		                 "port: " + port + LF + 
		                 "logPath: " + logPath + LF + 
		                 "defaultFilePath: " + defaultFilePath + LF + 
		                 "cgiPath: " + cgiPath + LF + 
		                 "logFilePath: " + logFilePath + LF +
		                 "logLevel: " + logLevel);
		Util.flushLogHandlers();
	}
	
}
