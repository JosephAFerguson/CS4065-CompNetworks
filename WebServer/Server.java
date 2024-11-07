package com.example;

import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.Json;
import java.io.*;
import java.net.*;

public final class Server 
{
    public static void main(String[] argv) throws Exception 
    {
        int port = 6789;

        // Establish the listen socket
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server is listening on port " + port);

        // Process HTTP service requests in an infinite loop
        while (true) 
        {
            // Listen for a TCP connection request.
            Socket socket = serverSocket.accept();

            // Construct an object to process the HTTP request message
            JSONRequest request = new JSONRequest(socket);

            // Create a new thread to process the request
            Thread thread = new Thread(request);

            // Start the thread
            thread.start();
        }
    }
}

final class JSONRequest implements Runnable 
{
    private Socket socket;

    // Constructor
    public JSONRequest(Socket socket) 
    {
        this.socket = socket;
    }

    // Implement the run() method of the Runnable interface
    public void run() 
    {
        try 
        {
            processRequest();
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        }
    }

    private void processRequest() throws Exception 
    {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

        // Read the first line of the request
        String requestLine = in.readLine();
        System.out.println("Request: " + requestLine);

        if (requestLine.startsWith("POST")) 
        {
            handlePostRequest(in, out);
        } 
        else 
        {
            sendNotFound(out);
        }

        // Close streams and socket
        in.close();
        out.close();
        socket.close();
    }

    private void handlePostRequest(BufferedReader in, BufferedWriter out) throws IOException {
        // Read headers and skip them
        String line;
        int contentLength = 0;

        while (!(line = in.readLine()).isEmpty()) 
        {
            System.out.println("Header: " + line);
            if (line.startsWith("Content-Length")) 
            {
                contentLength = Integer.parseInt(line.split(": ")[1]);
            }
        }

        // Read the JSON body
        char[] body = new char[contentLength];
        in.read(body);
        String jsonString = new String(body);

        // Parse the JSON data
        JsonObject jsonObject;
        try (JsonReader jsonReader = Json.createReader(new StringReader(jsonString))) 
        {
            jsonObject = jsonReader.readObject();
            System.out.println("Received JSON: " + jsonObject.toString());
        }

        // Send Response(s) JSON(s)
        sendJsonResponse(out, jsonObject);  
    }

    private void sendJsonResponse(BufferedWriter out, JsonObject jsonObject) throws IOException 
    {
        JsonObject responseJson = Json.createObjectBuilder().build();
        String type = jsonObject.getString("type");

        if ("clientRequest".equals(type)) // Use .equals() to compare strings
        {  
            String action = jsonObject.getString("action");
            //and so on
            if("join".equals(action))
            {
                String groupID = jsonObject.getString("groupID");
                //try request 
                responseJson = Json.createObjectBuilder(responseJson)
                    .add("type", "ServerAffirm")
                    .add("receivedData", jsonObject)
                    .build();
                
            }
            else
            {
                responseJson = Json.createObjectBuilder(responseJson)
                    .add("type", "ServerAffirm")
                    .add("receivedData", jsonObject)
                    .build();
            }
        } else {
            System.out.print(type);
            // If not a "join" type, deny the request
            responseJson = Json.createObjectBuilder(responseJson)
                .add("type", "ServerDeny")
                .add("receivedData", jsonObject)
                .build();
        }

        // Convert the response JSON to string and send it
        String jsonResponse = responseJson.toString();
        out.write("HTTP/1.1 200 OK\r\n");
        out.write("Content-Type: application/json\r\n");
        out.write("Content-Length: " + jsonResponse.length() + "\r\n");
        out.write("\r\n");
        out.write(jsonResponse);
        out.flush();
        System.out.println("Sent response: " + jsonResponse);
    }

    private void sendNotFound(BufferedWriter out) throws IOException 
    {
        String notFoundMessage = "404 Not Found";
        out.write("HTTP/1.1 404 Not Found\r\n");
        out.write("Content-Length: " + notFoundMessage.length() + "\r\n");
        out.write("\r\n");
        out.write(notFoundMessage);
        out.flush();
    }
}
