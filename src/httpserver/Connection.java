package httpserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.util.logging.Logger;

public class Connection extends Thread {

	public static Logger LOG = Util.initClassLogger(Connection.class.getName());

	private Socket client;
	
	// DataStreams required to up/download binary files...
	private DataInputStream readClient; 
	private DataOutputStream writeClient;

	private File requestFile;
	private String requestPath;

	private String resource;
	private String requestLine; // Method Resource Protocol \n

	//Constructor is called before run()
	public Connection(Socket acceptRequest) { 

		client = acceptRequest;
		LOG.info("Socket accepted from " + client.getRemoteSocketAddress());
		LOG.fine("Client connected to local socket at " + client.getLocalSocketAddress());

		try {
			writeClient = new DataOutputStream(new BufferedOutputStream(client.getOutputStream()));
			readClient = new DataInputStream(new BufferedInputStream(client.getInputStream()));
			LOG.fine("Created client readers and writers!");
		} catch (IOException e) {
			LOG.warning("Cant initialize client reader or writer!");
			LOG.warning(Util.getStringFromStackTrace(e));
			e.printStackTrace();
			return;
		}
	}

	public void run() {

		LOG.fine("Retrieving request in run()");

		try {

			int readTrys = 0;
			LOG.finest("Waiting for client to connect via readClient");
			
			//This used to be readClient.ready() when readClient was a BufferedReader
			//It sometimes would not work until it retried a few hundred times
			//I have not seen similar behavior with datastream though... but just in case 
			while (readClient.available() <= 0) { 
				readTrys++;
				LOG.finest("Request Trys: " + readTrys);
				if (readTrys >= 50000) { 
					sendStatusPage(408);
					return;
				}
			}

		} catch (IOException e) {
			// Send a message to log, cant initialize client writer!!
			LOG.warning("Can't retrieve request in run()");
			LOG.warning(Util.getStringFromStackTrace(e));
			return;
		}

		requestLine = "";
		try {
			requestLine = readClient.readLine();
			if (requestLine.isEmpty()) {
				LOG.fine("Improper request!");
				sendStatusPage(400);
				return;
			}
			LOG.fine("RequestLine: " + requestLine);
		} 
		catch (IOException e) {
			LOG.warning("Can't read requestline, dropping connection!");
			LOG.warning(Util.getStringFromStackTrace(e));
			return;
		}
		
		String[] requestLineTokens = requestLine.split(" ");
		
		if (requestLineTokens.length != 3) {
			LOG.fine("Improper Request");
			sendStatusPage(400);
			return;
		}

		String method = requestLineTokens[0].toUpperCase().trim(); 
		resource = requestLineTokens[1].trim();
		String protocol = requestLineTokens[2].toUpperCase().trim();

		resource = Web.convertURLToPath(resource); //Allows weird characters in URL, eg spaces

		if (resource.equals("/")) //root
			requestPath = Config.defaultFilePath; 
		else
			requestPath = Config.fileRootPath + resource;

		requestFile = new File(requestPath);

		if ((protocol.equals("HTTP/1.0"))) {
			sendStatusPage(505);
			return;
		} else if (!protocol.equals("HTTP/1.1")
				|| (requestPath.isEmpty() || !(requestPath.contains("/")))) {
			sendStatusPage(400);
			return;
		}

		LOG.fine("Resource requested: " + resource);
		
		Util.flushLogHandlers();

		try {
			switch (method) { // Not in web because the type of request affects the use of sockets
			case "GET":
				handleGet();
				break;
			case "POST":
				handlePost();
				break;
			case "OPTIONS":
				handleOptions();
				break;
			case "HEAD":
				handleHead();
				break;
			case "PUT":
				handlePut();
				break;
			case "DELETE":
				handleDelete();
				break;
			case "TRACE":
				break;
			case "CONNECT":
				break;
			default:
				sendStatusPage(400);
				break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void handleOptions() {
		sendResponseHeader(501);

	}

	private void handleDelete() {
		
		if (!Config.enableDelete) {
			sendStatusPage(403);
			return;
		}
		/*
		 * A successful response SHOULD be 200 (OK) if the response includes an
		 * entity describing the status, 202 (Accepted) if the action has not
		 * yet been enacted, or 204 (No Content) if the action has been enacted
		 * but the response does not include an entity.
		 */
		
		if (!requestFile.exists()) {
			sendResponseHeader(404);
		}
		else {
			if (requestFile.delete()) { //true, deleted
				sendResponseHeader(204);
			}
			else { 
				sendResponseHeader(500); //I guess permission was denied?
			}
		}
	}

	private void handleHead() {
		sendResponseHeader(200);
	}

	private void handlePut() throws IOException {
		//Put cannot accept binary data...

		String postData = Util.getStringFromDataStream(readClient);
		String[] postTokens = postData.split("\r\n");
		String postContent = postTokens[postTokens.length-1];
		LOG.fine("PUT/POST data: \n" +postData);
		
		String putPath = Config.fileRootPath + resource;
		LOG.finer("Client wants to put file at " + putPath);
		File putFile = new File(putPath);

		if (requestPath.contains(Config.cgiPath)) { // Sorry, I'm not letting you put stuff there
			sendStatusPage(403);
			return;
		}

		if (Config.enablePutAndRawPost) {
			if (!Config.enablePutOverwrite && putFile.exists()) {
				sendStatusPage(403);
				return;
			}
				BufferedWriter putFileWriter = new BufferedWriter(new FileWriter(putFile));
				putFileWriter.write(postContent);
				putFileWriter.flush();
				putFileWriter.close();
				sendResponseHeader(201);
				LOG.info("Wrote PUT/POST data to file");
		} else {
			sendStatusPage(405);
		}
	}

	public void closeSockets() { // Public so we can call from other classes
		
		try {
			LOG.fine("Closing sockets");
        	Util.flushLogHandlers();
			writeClient.close();
			readClient.close();
		} catch (IOException e) {
			LOG.warning("Cannot close sockets!");
			Util.getStringFromStackTrace(e);
        	Util.flushLogHandlers();
			return;
		}
	}

	private void handleGet() { 

		if (!requestFile.exists()) {
			LOG.info("GET RequestFile " + requestFile.getAbsolutePath() + " Does not exist!");
			sendStatusPage(404);
			return;
		}
		
		if (requestFile.isDirectory() && Config.enableDirView) {
			LOG.info("GET RequestFile is a directory, showing dirview");
			if (Config.cgiPath.contains(requestPath)) { sendStatusPage(403); return;} //No looking in script Dir!
			byte[] pageBytes = Web.makePageFromDir(requestFile).getBytes();
			ByteArrayInputStream dirPageBytes = (new ByteArrayInputStream(Web.makePageFromDir(requestFile).getBytes()));
			Web.setContentLength(String.valueOf(pageBytes.length));
			Web.setContentType("text/html");
			sendResponseHeader(200);
			sendResponseData(dirPageBytes);
			return;
		}
		

		if (requestPath.contains(Config.cgiPath)) { // File is in cgi directory
			LOG.info("GET request is a CGI File");
			CGI CGIRequest = new CGI(requestFile, readClient);
			if (CGIRequest.encounteredError != 0) {
				System.out.println("CGI request object encountered Error!");
				sendStatusPage(CGIRequest.encounteredError);
				return;
			}

			sendResponseHeader(200);
			sendResponseData(CGIRequest.returnGetCGIStream());

		}

		else {
			try {
				Web.setContentType(Files.probeContentType(requestFile.toPath()));
				Web.setContentLength(Long.toString(requestFile.length()));
			}
			catch (IOException e) {
				LOG.warning("GET Problem determing content type!");
				Util.getStringFromStackTrace(e);
				return;
			}
			
			try {
				sendResponseHeader(200);
				sendResponseData(new FileInputStream(requestPath));
			}
			catch (IOException e) {
				LOG.warning("GET Problem sending response data!");
				Util.getStringFromStackTrace(e);
				return;
			}
		}
	}

	private void sendResponseHeader(int status) {
		try {
			String header = Web.generateResponseHeader(status);
			LOG.fine("Generated Response Header: \n" + header);

			writeClient.write(header.getBytes()); 
			writeClient.flush();
			
		} catch (IOException e) {
			LOG.warning("Problem Sending Response Header!");
			Util.getStringFromStackTrace(e);
			return;
		}
	}

	private void handlePost() throws IOException {

		if (requestPath.contains(Config.cgiPath)) {

			LOG.fine("POST with cgi script!");

			CGI CGIRequest = new CGI(requestFile, readClient);
			if (CGIRequest.encounteredError != 0) {
				LOG.warning("CGI request object encountered Error!");
				sendStatusPage(CGIRequest.encounteredError);
				return;
			}

			sendResponseHeader(200);
			sendResponseData(CGIRequest.returnPostCGIStream());

		} else {
			if (Config.enablePutAndRawPost) {
				// If it's a post request that's not being fed to a cgi script,
				// It's the same as put
				LOG.fine("POST passing to handlePut()!");
				handlePut();
			} else {
				String DELETE = "";
				if (Config.enableDelete)
					DELETE = ", DELETE";
				Web.setAllow("GET, HEAD, PUT, POST" + DELETE);
				sendStatusPage(405);
			}
		}

		closeSockets();
		return;

	}

	public void sendStatusPage(int code) {
		sendResponseHeader(code);
		try {
			ByteArrayInputStream statusPageBytes = (new ByteArrayInputStream(
					Web.makeStatusPage(code).getBytes()));
			LOG.fine("Sending status code " + code + " : " + Web.httpStatusMessage.get(code));
			Util.writeBytesToSocket(statusPageBytes, new DataOutputStream(writeClient));
		} catch (IOException e) {
			LOG.warning("IOException occured trying to send status!");
			LOG.warning(Util.getStringFromStackTrace(e));
			return;
		}
		closeSockets();
	}

	private void sendResponseData(InputStream requestedData) { 
		
		try {
			LOG.fine("Writing Data in sendResponseData()!");

			LOG.info("Sending file " + resource + " [" + requestedData.available() + " bytes est]");
			
			short chunkSize = Util.getByteDivisor(requestedData.available());
			
			LOG.fine("Sending data in increments of " + chunkSize + "!");
			
			
			Util.flushLogHandlers();
			
			byte rawBytes[] = new byte[chunkSize]; //this should make it pretty efficient most of the time

			while ((requestedData.read(rawBytes)) != -1) {
				writeClient.write(rawBytes);
			}

			writeClient.flush();

		} catch (IOException e) {
			// Error: Cannot write response payload! (I can't tell the client,
			LOG.warning("IOException in sendResponseData, cannot send data to client!");
			LOG.warning(Util.getStringFromStackTrace(e));
			return;
		}
		closeSockets();
	}
}
