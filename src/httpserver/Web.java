package httpserver;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.HashMap;


public class Web {
	
	/*
	 * 
	 * Web handles a bunch of high level stuff HTTP stuff including:
	 * 
	 * -Status Codes/Definitions
	 * -URL Decoding
	 * -Setting/Generating response header fields
	 * 
	 */
	
	final private static SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
	
    public static HashMap<Integer, String> httpStatusMessage = new HashMap<Integer, String>();
	
	final private static char LF = '\n';
	final private static char CR = '\r';
    final private static String protocol = "HTTP/1.1";
	final private static String connection = "Connection: Keep-Alive" + LF ; // Default for http/1.1
	final private static String server = "Server: " + Config.serverName + LF;
	
	//If we implement gzip, set this
    private static String acceptEncoding = "";
    private static String host = "";
    private static String date = "";
    //Use with 405
    public static String allow = "";
    
    //Unfortunately, these cannot all be found in one function, so we will access them when we have the change
	public static String contentType = "Content-Type: text/html" + LF; 
    public static String contentLength = ""; 
    
    public static void setAllow(String newAllow) {
    	allow = "Allow: " + newAllow + LF;
    }
    
    public static void setContentType(String newContentType) {
    	contentType = "Content-Type: " + newContentType + LF;
    }
	
    public static void setContentLength(String newContentLength) {
    	contentLength = "Content-Length: " + newContentLength + LF;
    }
    
    
    
    public static void initMessages() {
    	
		httpStatusMessage.put(100,"Continue");
		httpStatusMessage.put(101, "Switching Protocols");
		/*Successful*/
		httpStatusMessage.put(200, "OK");
		httpStatusMessage.put(201, "Created");
		httpStatusMessage.put(202, "Accepted");
		httpStatusMessage.put(203, "Non-Authoritative Information");
		httpStatusMessage.put(204, "No Content");
		httpStatusMessage.put(205, "Reset Content");
		httpStatusMessage.put(206, "Partial Content");
		/*Redirection*/
		httpStatusMessage.put(300, "Multiple Choices");
		httpStatusMessage.put(301, "Moved Permanently");
		httpStatusMessage.put(302, "Found");
		httpStatusMessage.put(303, "See Other");
		httpStatusMessage.put(304, "Not Modified");
		httpStatusMessage.put(305, "Use Proxy");
		/*Client Error*/
        httpStatusMessage.put(400, "Bad Request");
        httpStatusMessage.put(401, "Unauthorized");
        httpStatusMessage.put(402, "Payment Required"); //Apparently not used
        httpStatusMessage.put(403, "Forbidden");
        httpStatusMessage.put(404, "Not Found");
        httpStatusMessage.put(405, "Method Not Allowed");
        httpStatusMessage.put(406, "Not Acceptable");
        httpStatusMessage.put(407, "Proxy Authentication Required");
        httpStatusMessage.put(408, "Request Timeout");
        httpStatusMessage.put(409, "Conflict");
        httpStatusMessage.put(410, "Gone");
        httpStatusMessage.put(411, "Length Required");
        httpStatusMessage.put(412, "Precondition Failed");
        httpStatusMessage.put(413, "Request Entity Too Large");
        httpStatusMessage.put(414, "Request-URI Too Long");
        httpStatusMessage.put(415, "Unsupported Media Type");
        httpStatusMessage.put(416, "Requested Range Not Satisfiable");
        httpStatusMessage.put(417, "Expectation Failed");
        /*Server Error*/ 
        httpStatusMessage.put(500, "Internal Server Error");
        httpStatusMessage.put(501, "Not Implemented");
        httpStatusMessage.put(502, "Bad Gateway");
        httpStatusMessage.put(503, "Service Unavailable");
        httpStatusMessage.put(504, "Gateway Timeout");
        httpStatusMessage.put(505, "HTTP Version Not Supported");
    }
	
    
    public static String makePageFromDir(File directory) {
    	StringBuilder page = new StringBuilder();
    	
    	page.append("<html>");
    	page.append("<body>");
    	page.append("<h3> Files found in this directory: </h3>");
    	for (File file : directory.listFiles()) {
    		String fileName = file.getAbsolutePath();
    		page.append("<a href=\"" + fileName.substring(Config.fileRootPath.length()) + "\">" + file.getName() + "</a><br>");
    	}
    	page.append("</html>");
    	page.append("</body>");

    	System.out.println(page.toString());
    	return(page.toString());
    }
	
	public static String convertURLToPath(String resource) {
		//This method will not translate ascii decimals values 0-15 (they only have 1 hex digit)
		//In fact this method will probably screw up the connection if they are encountered
		
		resource.replaceAll("\\+", " "); //Pluses are sometimes spaces

		if (!resource.contains("%")) 
			return(resource);
		
		System.out.print("Converting URI: " + resource + " --> "); 
		
		int fromIndex = 0;
		int percentIndex = 0;
		while ((percentIndex = resource.indexOf("%", fromIndex)) != -1) { //
			char hexChar1 = resource.charAt(percentIndex+1);
			char hexChar2 = resource.charAt(percentIndex+2);
			String hexVal = String.valueOf(hexChar1) + String.valueOf(hexChar2); //If the string contains "%20", hexVal now holds "20"
                        String charVal = "";
			charVal += (char)Integer.parseInt(hexVal, 16); //Convert "20" into a ' '
                        if (charVal.equals("\\"))
                          charVal = "\\\\"; //Since java re will error out with one slash
			resource = resource.replaceAll("%" + hexChar1 + hexChar2, charVal + "");
			fromIndex = percentIndex; //start from current %, find another %xx to convert
		}
		
		System.out.println(resource);
	
		return(resource);
	}
	
	
	public static String makeStatusPage(int code) { 
        return ("<html>"
                +	"<body>"
                        +		"<h1>"
                        +			code 
                        +		"</h1>"
                        +		"<h2>"
                        +			httpStatusMessage.get(code)
                        +		"</h2>"
                        +	"</body>"
                        +"</html>"
                );
	}
	
	
	
	private static void clearHeaderFields() {
		
		acceptEncoding = "";
		host = "";
		date = "";
		allow = "";
		contentType = "Content-Type: text/html" + LF; 
		contentLength = ""; 
	}
	
	public static String generateResponseHeader(int code) {
		
        host = "Host: " + Config.localHost + LF;
        date = "Date: " + dateFormat.format((new GregorianCalendar()).getTime()) + LF;
         
        //Fairly minimal header
        
				String header =  
						  protocol + " " + code + " " + httpStatusMessage.get(code) + CR + LF
						+ connection 
						+ server 
						+ acceptEncoding 
						+ host
						+ contentType
						+ contentLength
						+ date
						+ CR + LF;
				
				clearHeaderFields(); //set to defaults in case we don't use them next time
						
				return(header);
	}
}
