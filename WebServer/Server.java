package com.example;

import javax.json.JsonObject;
import javax.json.JsonReader;
//import javax.swing.*;//to be used for our later gui if added
import javax.json.Json;
import java.util.HashMap;
import java.io.*;
import java.net.*;

final class CONSTANTS 
{
    public final static int MAX_USERS = 100;
    public final static int MAX_MESSAGES = 1000; 
}

public final class Server 
{
    public static void main(String[] argv) throws Exception 
    {
        MessageBoard messageBoard = new MessageBoard();
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
            JSONRequest request = new JSONRequest(socket, messageBoard);

            // Create a new thread to process the request
            Thread thread = new Thread(request);

            // Start the thread
            thread.start();
        }
    }
}
final class Message
{
    int messageID;
    String sender;
    String postDate;
    String subject;
    String content;
}
final class MessageBoard 
{
    private HashMap<Integer, Message> messages;
    private Message[] messagesByDate;
    private String[] users;
    private int userInd;
    private int messageInd;

    public MessageBoard() 
    {
        messages = new HashMap<>();
        messagesByDate = new Message[CONSTANTS.MAX_MESSAGES];
        users = new String[CONSTANTS.MAX_USERS];
        userInd = 0;
        messageInd = 0;
    }

    public boolean getUser(String username) 
    {
        for (int i = 0; i < userInd; i++) 
        {
            if (users[i].equals(username)) 
            {
                return true;
            }
        }
        return false;
    }
    public String[] getAllUsers()
    {
        return users;
    }

    public void addUser(String username) 
    {
        if (userInd < CONSTANTS.MAX_USERS) 
        {
            users[userInd] = username;
            userInd++;
        } else 
        {
            System.out.println("Max user limit reached. Cannot add more users.");
        }
    }

    public void removeUser(String username) 
    {
        for (int i = 0; i < userInd; i++) 
        {
            if (users[i].equals(username)) 
            {
                // Shift elements to the left
                for (int j = i; j < userInd - 1; j++) 
                {
                    users[j] = users[j + 1];
                }
                users[userInd - 1] = null; 
                userInd--;
                break; 
            }
        }
    }

    public void addMessage(int id, Message message) {

        if (messageInd < CONSTANTS.MAX_MESSAGES) 
        {
            messages.put(id, message);
            messagesByDate[messageInd] = message;
            messageInd++;
        } else 
        {
            System.out.println("Max message limit reached. Cannot add more messages.");
        }
    }

    public Message[] getLast2() 
    {
        Message[] retMessages = new Message[2];
        if (messageInd >= 2)
        {
            retMessages[0] = messagesByDate[messageInd - 1];
            retMessages[1] = messagesByDate[messageInd - 2];
        } else if (messageInd == 1) 
        {
            retMessages[0] = messagesByDate[messageInd - 1];
            retMessages[1] = null; 
        } else 
        {
            retMessages[0] = retMessages[1] = null; 
        }
        return retMessages;
    }

    public Message getMessage(int id) 
    {
        return messages.get(id);
    }

    public void deleteMessage(int id) 
    {
        if (!messages.containsKey(id)) 
        {
            System.out.println("Message ID not found.");
            return;
        }
        messages.remove(id);
        for (int i = 0; i < messageInd; i++) 
        {
            if (messagesByDate[i].messageID == id) 
            {
                for (int j = i; j < messageInd - 1; j++) 
                {
                    messagesByDate[j] = messagesByDate[j + 1];
                }
                messagesByDate[messageInd - 1] = null; 
                messageInd--;
                break; 
            }
        }
    }
}
    

final class JSONRequest implements Runnable 
{
    private Socket socket;
    private MessageBoard messageBoard;

    // Constructor
    public JSONRequest(Socket socket, MessageBoard messageBoard) 
    {
        this.socket = socket;
        this.messageBoard = messageBoard;
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

    private void processRequest() 
    {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) 
        {
            
            // Read the request line
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
    
        } catch (IOException e) 
        {
            System.err.println("Error processing request: " + e.getMessage());
        } 
        finally 
        {
            try 
            {
                socket.close();
            } catch (IOException e) 
            {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
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
        buildJsonResponse(out, jsonObject);  
    }
    private void sendErrorJsonResponse(BufferedWriter out, JsonObject jsonObject) throws IOException 
    {
        //violates protocol
        JsonObject responseJson = Json.createObjectBuilder().build();
        responseJson = Json.createObjectBuilder(responseJson)
        .add("type", "ServerDeny")
        .add("receivedData", jsonObject)
        .build();

        // Convert the response JSON to string and send it
        String jsonResponse = responseJson.toString();
        out.write("HTTP/1.1 400 BAD RESPONSE\r\n");
        out.write("Content-Type: application/json\r\n");
        out.write("Content-Length: " + jsonResponse.length() + "\r\n");
        out.write("Connection: close\r\n\r\n");
        out.write("\r\n");
        out.write(jsonResponse);
        out.flush();
        System.out.println("Sent response: " + jsonResponse);
    }
    private void sendJsonResponse(BufferedWriter out, JsonObject jsonObject) throws IOException 
    {
        String jsonResponse = jsonObject.toString();
        out.write("HTTP/1.1 200 OK\r\n");
        out.write("Content-Type: application/json\r\n");
        out.write("Content-Length: " + jsonResponse.length() + "\r\n");
        out.write("Connection: close\r\n\r\n");  // Set connection close header
        out.write(jsonResponse);
        out.flush();
        System.out.println("Sent response: " + jsonResponse);
    }
    
    private void buildJsonResponse(BufferedWriter out, JsonObject jsonObject) throws IOException 
    {
        JsonObject responseJson;
    
        if (!jsonObject.isEmpty() && jsonObject.containsKey("type") && jsonObject.containsKey("action")) 
        {
            String type = jsonObject.getString("type");
            String action = jsonObject.getString("action");
    
            if ("clientRequest".equals(type)) {
                if ("join".equals(action) && jsonObject.containsKey("username")) 
                {
                    String username = jsonObject.getString("username");
    
                    if (!messageBoard.getUser(username)) 
                    {
                        messageBoard.addUser(username);
                        responseJson = Json.createObjectBuilder()
                                           .add("type", "ServerAffirm")
                                           .add("receivedData", jsonObject)
                                           .build();
                    } 
                    else 
                    {
                        responseJson = Json.createObjectBuilder()
                                           .add("type", "ServerDeny")
                                           .add("error", "Username already exists.")
                                           .build();
                    }
                } 
                else 
                {
                    responseJson = Json.createObjectBuilder()
                                       .add("type", "ServerDeny")
                                       .add("error", "Invalid action or missing username.")
                                       .build();
                }
            } 
            else 
            {
                responseJson = Json.createObjectBuilder()
                                   .add("type", "ServerDeny")
                                   .add("error", "Invalid request type.")
                                   .build();
            }
            sendJsonResponse(out, responseJson);
        } else {
            sendErrorJsonResponse(out, jsonObject);
        }
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
