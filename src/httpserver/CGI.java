package httpserver;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ProcessBuilder;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;

public class CGI {
	
	
	/*
	 * A good overview of how CGI works: http://www.cs.toronto.edu/~penny/teaching/csc309-01f/lectures/40/CGI.pdf
	 * 
	 * This object is created whenever the request path is inside the CGI
	 * directory. It will set up the CGI metavariables (via setCGIEnv) and important class
	 * variables (stdin and stdout handlers from/to the program). Then, either the getCGI,
	 * postCGI, etc method will be called from inside the Connection.handle*()
	 * methods. These methods will be responsible for setting up data streams so
	 * connection can deal with them.
	 *
	 * My only regret with this class is that I did not have enough time to set up
	 * QUERY_STRING, so forms with input boxes are basically useless :(
	 * 
	 * What does work: 
	 * -Script output on get requests
	 * -File uploading with html form enctype set to "multipart/form-data"
	 * 
	 * What's weird: 
	 * -Normally scripts are supposed to output their content-type before anything else, and if they don't the server returns an error.
	 * Jhttpd already does this, so the script does not (cannot) create it's own http headers (this is why running my test.py has "content-type: text/html" at the top!)
	 * -Probably much more
	 */

	private PrintStream stdIn;
	private InputStream stdOut;
	private ProcessBuilder cgiProgram;
	File requestFile;
	public int encounteredError;
	private static final Logger LOG = Util.initClassLogger(CGI.class.getName());
	private DataInputStream readClient;
	private Process runningProgram;

	CGI(File CGIRequestFile, DataInputStream connectionReadClient) {
		LOG.fine("Entered CGI Constructor!");
		encounteredError = 0;

		requestFile = CGIRequestFile;
		readClient = connectionReadClient;

		if (!requestFile.canExecute()) {
			encounteredError = 403;
			LOG.info("CGI target " + requestFile + " is not executable!");
			return;
		}

		if (!requestFile.isFile()) { // Will they try to execute a directory?
			LOG.info("CGI target " + requestFile + " is not a file!");
			encounteredError = 403; // What's a better error for this?
			return;
		}

		ArrayList<String> cgiArguments = new ArrayList<String>();

		String OS = System.getProperty("os.name").toUpperCase();

		if (OS.contains("WIN")) {
                      
			cgiArguments.add("C:\\Windows\\System32\\cmd"); 
                        cgiArguments.add("/c");
		}

		cgiArguments.add(requestFile.getAbsolutePath()); //shebang (#!) has me covered on *nix

		cgiProgram = new ProcessBuilder(cgiArguments);
		
		Util.flushLogHandlers();

		setCGIEnv(requestFile, readClient);

		cgiProgram.redirectErrorStream(true); // stderr to webpage please!

		LOG.info("Starting CGI program");

		try {
			runningProgram = cgiProgram.start();
			 Map<String,String> m = cgiProgram.environment();
			 String[] values = Arrays.toString(m.values().toArray()).replaceAll("[\\]\\[]", "").split(", ");
			 String[] keys = Arrays.toString(m.keySet().toArray()).replaceAll("[\\]\\[]", "").split(", ");
			 String pairs = "";
			 for (int i=0;i<m.size();i++) {
				 pairs += keys[i].trim() + " <=> " + values[i].trim() + "\n";
			 }
			LOG.finest("CGI Metavariables: \n" + pairs);
			 
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
		}
		stdIn = new PrintStream(runningProgram.getOutputStream());
		stdOut = runningProgram.getInputStream();
	}

	public InputStream returnPostCGIStream() { 
		
		try {
			String boundary = "NoBoundFound";

			
			int readTrys = 0;
			
			while (readClient.available() <= 0) { 
				readTrys++;
				LOG.finest("Request Trys: " + readTrys);
				if (readTrys >= 50000) { 
					return(stdOut);
				}
			}
			
			
			LOG.info("POST CGI entering parse loop! " + readClient.available());
			
			boolean foundBoundary = false;
			readClient.mark(20000);
			
			while (readClient.available() > 0) { // Feed portion of browser request to script
				
				String line = readClient.readLine();
				if (line.contains("boundary=")) { // Find what the browser set the boundary to
					boundary = line.substring(line.indexOf("boundary=") + 9);
					foundBoundary = true;
				}

				if (line.contains(boundary)) {
					LOG.fine("Found upload content boundary! Sending the rest of the data to CGI stdin!");

					/*
					 * There are some huge nono's here: 
					 * 
					 * #1: relying on .available(): I don't know what voodoo magic it uses, but
					 * it's probably not reliable 
					 * 
					 * #2: Readline in DataInputStream is deprecated, but how else am I supposed
					 * to use SomeStringFriendlyReader and DataInputStream on the same source?
					 * 
					 * #3:The entire thing is loaded into RAM in a byte array, which means...
					 * 
					 * #4:Buffer overflow, or java OutOfMemery (or whatever exception) is very possible
					 */

					// Shove entire request, including post data into buf[],
					// then send the whole thing to the script
					byte buf[] = new byte[readClient.available()];
					readClient.read(buf, 0, readClient.available());
					String uploadData = "";
					for (byte i : buf) {
						uploadData += (char)i;
					}
					stdIn.write(buf);
					LOG.finest("Uploaded File Contents: \n" + uploadData);
				}
				
				if (!foundBoundary && readClient.available() <= 0 ) {//not a proper browser upload! 
					//I have no idea if this breaks spec, but it's just so I echo.py works!
					
					readClient.reset();
					
					String postData = Util.getStringFromDataStream(readClient);
					String[] postTokens = postData.split("\r\n");
					String postContent = postTokens[postTokens.length-1];
					LOG.fine("HACKED CGI/POST data: \n" +postContent);
					
					stdIn.print(postContent);
					//LOG.finest("No boundary, so used entire request: \n" + uploadData);
				
				}
				
			}

			stdIn.flush();
			stdIn.close();
			
			
			LOG.finer("stdIn flushed and closed");
			
			Util.flushLogHandlers();

			return (stdOut);
			
		} catch (IOException e) {
			LOG.warning("A problem occured exchanging data with CGI program!");
			LOG.fine("Sending ERR to Browser!");
			StringWriter out = new StringWriter();
			e.printStackTrace(new PrintWriter(out));
			String errString = out.toString();
			return (new ByteArrayInputStream(errString.getBytes()));
		}
	}


	private void setCGIEnv(File requestFile, DataInputStream readClient) {

		String CONTENT_TYPE = "";
		String HTTP_ACCEPT = "";
		String HTTP_CONNECTION = "";
		String HTTP_REFERER = "";
		String HTTP_USER_AGENT = "";
		String CONTENT_LENGTH = "";

		LOG.fine("Using Readclient from setCGIEnv!");
		
		String logLines = "";

		try {
			readClient.mark(20000); // 20000 characters to get upload headers...
			while (readClient.available() > 0) {

				String b = readClient.readLine().trim();
				logLines += b + "\n";

				//This is making some horrible assumptions about the request.. 
				String[] headerField = b.split(": "); 
				
				switch (headerField[0].toLowerCase()) { //set some necessary CGI variables
					case "content-type": CONTENT_TYPE = headerField[1]; break;
					case "content-length": CONTENT_LENGTH = headerField[1]; break;
					case "accept": HTTP_ACCEPT = headerField[1]; break;
					case "connection": HTTP_CONNECTION = headerField[1]; break;
					case "user-agent": HTTP_USER_AGENT = headerField[1]; break;
					case "referer": HTTP_REFERER = headerField[1]; break;
					default: break;
				}

				if (b.startsWith("-----")) {
					break;
				}
			}
			LOG.finest("Parsed header for CGI: \n" + logLines);
			readClient.reset();
		} catch (IOException e) {
			LOG.warning("Problem reading request to set CGI metavariables!");
			LOG.warning(Util.getStringFromStackTrace(e));
			return;
		}


		// Calling .start() will magically read currentEnv that we set here
		// and use it as the process environment
		// WTF java, that's really strange behavior!
		Map<String, String> currentEnv = cgiProgram.environment();

		// For security purposes... clear current environment
		currentEnv.clear();
		
		//If we set variables with empty strings, python cgi.test() complains
		//So, use Util.mapIfValid to map only if it's !empty
		
		Util.mapIfValid("CONTENT_TYPE", CONTENT_TYPE, currentEnv);
		Util.mapIfValid("CONTENT_LENGTH", CONTENT_LENGTH, currentEnv);
		Util.mapIfValid("HTTP_ACCEPT", HTTP_ACCEPT, currentEnv);
		Util.mapIfValid("HTTP_CONNECTION", HTTP_CONNECTION, currentEnv);
		Util.mapIfValid("HTTP_USER_AGENT", HTTP_USER_AGENT, currentEnv);
		Util.mapIfValid("HTTP_REFERER", HTTP_REFERER, currentEnv);
		
		
		//There are many more of these I could set, but I think these are the most important
		//See https://tools.ietf.org/html/rfc3050#section-5.5.1
		currentEnv.put("DOCUMENT_ROOT", Config.fileRootPath);
		currentEnv.put("HTTP_HOST", Config.localHost);
		currentEnv.put("REQUEST_METHOD", "POST");
		currentEnv.put("SCRIPT_NAME", requestFile.getName());
		currentEnv.put("SERVER_NAME", "jhttpd");
		currentEnv.put("GATEWAY_INTERFACE", "CGI/1.1");
		currentEnv.put("SERVER_PORT", Short.toString(Config.port));
		currentEnv.put("SERVER_PROTOCOL", "HTTP/1.1");
		currentEnv.put("X_SERVER_COMMENT", "Hello from jhttpd!");
		
		LOG.info("Set up all metavariables!");

	}

	public InputStream returnGetCGIStream() {

		stdIn.close(); 

		try {
			String guessType = URLConnection.guessContentTypeFromStream(stdOut); //Really unreliable method, returns null on plaintext, is not consistant
			if (guessType.equals(null)) {
				Web.setContentType("text/html");
			} else {
				Web.setContentType(guessType);
			}

		} catch (IOException e) { 
			LOG.warning("Exception occured trying to Guess Content Type!");
		}
		catch (NullPointerException e) { // sometimes this happens...
			Web.setContentType("text/html");
		}

		try {

			Web.setContentLength(Integer.toString(stdOut.available()));
			return (stdOut);

		} catch (IOException e) {
			LOG.warning("Problem occured returning CGI stream on GET, sending error to browser!");
			StringWriter out = new StringWriter();
			e.printStackTrace(new PrintWriter(out));
			String errString = out.toString();
			return (new ByteArrayInputStream(errString.getBytes()));
		}
	}

}
