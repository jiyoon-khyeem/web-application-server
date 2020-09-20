package webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.Database;
import model.User;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
        	
        	BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        	
        	String line = br.readLine();
        	log.debug("request line : {}", line);
        	
        	if(line == null) {
        		return;
        	}
        	
        	
        	// 경로 구하기 -> util에 넣고 공통으로 쓰는 게 낫겠지	
        	String url = HttpRequestUtils.getUrl(line);
        	Map<String, String> headers = new HashMap<String, String>();
        	
        	boolean logined = false;
        	
        	while(!line.equals("")) {
        		log.debug("header : {}", line);
        		line = br.readLine();
        		
        		if(line.contains("Cookie")) {
        			logined = isLogin(line);
        		}
        		
        		String[] headerTokens = line.split(": ");
        		if(headerTokens.length == 2) {
        			headers.put(headerTokens[0], headerTokens[1]);
        		}
        	}
        	
        	
        	log.debug("Content-Length : {}", headers.get("Content-Length"));
        	
        	if(url.startsWith("/user/create")) {
        		String requestBody = IOUtils.readData(br, Integer.parseInt(headers.get("Content-Length")));
        		log.debug("Request Body : {}", requestBody);
        		Map<String, String> params = HttpRequestUtils.parseQueryString(requestBody);
        		User user = new User(params.get("userId"), params.get("password"), params.get("name"), params.get("email"));
        		log.debug("User : {}", user);
        		Database.addUser(user);
        	
                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos);
        	} else if(url.equals("/user/login")) {
        		String requestBody = IOUtils.readData(br, Integer.parseInt(headers.get("Content-Length")));
        		log.debug("Request Body : {}", requestBody);
        		Map<String, String> params = HttpRequestUtils.parseQueryString(requestBody);
        		User user = new User(params.get("userId"), params.get("password"), params.get("name"), params.get("email"));
        		log.debug("UserId : {}, password : {}", params.get("userId"), params.get("password"));        		
        		user = Database.getUser(params.get("userId"));
        		if(user == null) {
        			log.debug("User Not Found");
        			responseResource(out, "/user/login_failed.html");      	
        		} else if(user.getPassword().equals(params.get("password"))) {
        			log.debug("login Success!!");
        			DataOutputStream dos = new DataOutputStream(out);
        			response302LoginSuccessHeader(dos, "logined=true");       	
        		} else {
        			log.debug("Password Mismatch");
        			responseResource(out, "/user/login_failed.html");      	
        		}
        		
        		
        	} else if(url.equals("/user/list")) {
        		if(!logined) {
        			responseResource(out, "/user/login.html");
        			return;
        		}
        		Collection<User> users = Database.findAll();
        		StringBuilder sb = new StringBuilder();
        		sb.append("<table border = '1'>");
        		for(User user : users) {
        			sb.append("<tr>");
        			sb.append("<td>" + user.getUserId() + "</td>");
        			sb.append("<td>" + user.getName() + "</td>");
        			sb.append("<td>" + user.getEmail() + "</td>");
        			sb.append("</tr>");
        		}
        		sb.append("</table>");
        		byte[] body = sb.toString().getBytes();
        		DataOutputStream dos = new DataOutputStream(out);
        		response200Header(dos, body.length);
        		responseBody(dos, body);
        	
        	} else if(url.endsWith(".css")){
        		DataOutputStream dos = new DataOutputStream(out);
        		byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
        		respose200CssHeader(dos, body.length);
        		responseBody(dos, body);
        	} else {
                DataOutputStream dos = new DataOutputStream(out);
                byte[] body = Files.readAllBytes(new File("./webapp"+url).toPath());
                response200Header(dos, body.length);
                responseBody(dos, body);        		
        	}
        
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    
    private boolean isLogin(String line) {
    	String[] headerToken = line.split(": ");
    	Map<String, String> cookies = HttpRequestUtils.parseCookies(headerToken[1].trim());
    	String value = cookies.get("logined");
    	if(value == null) {
    		return false;
    	}
    	return Boolean.parseBoolean(value);
    }
    
    private void respose200CssHeader(DataOutputStream dos, int lengthOfBodyContent) {
    	try {
    		dos.writeBytes("HTTP/1.1 200 OK \r\n");
    		dos.writeBytes("Content-Type: text/css\r\n");
    		dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
    		dos.writeBytes("\r\n");
    	} catch (IOException e) {
    		log.error(e.getMessage());
    	}
    }
    
    private void responseResource(OutputStream out, String url) throws IOException {
    	DataOutputStream dos = new DataOutputStream(out);
    	byte[] body = Files.readAllBytes(new File("./webapp"+url).toPath());
    	response200Header(dos, body.length);
    	responseBody(dos, body);
    } 
    
    
    private void response302LoginSuccessHeader(DataOutputStream dos, String cookie) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: /index.html\r\n");
            dos.writeBytes("Set-Cookie:" + cookie + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }    
    
    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302Header(DataOutputStream dos) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: /index.html\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }    
    
    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
