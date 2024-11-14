package com.example;

import javax.json.JsonObject;
import javax.json.JsonReader;
//import javax.swing.*;//to be used for our later gui if added
import javax.json.Json;
import java.util.HashMap;
import java.io.*;
import java.time.LocalDate;
import java.net.*;

final class CONSTANTS {
    public final static int MAX_USERS = 100;
    public final static int MAX_MESSAGES = 1000;
}

public final class Server {
    public static void main(String[] argv) throws Exception {
        MessageBoard messageBoard = new MessageBoard();
        int port = 6789;

        // Establish the listen socket
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server is listening on port " + port);

        // Process HTTP service requests in an infinite loop
        while (true) {
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

final class Message {
    int messageID;
    String sender;
    String postDate;
    String subject;
    String content;
}

final class MessageBoard {
    private HashMap<Integer, Message> messages;
    private Message[] messagesByDate;
    private String[] users;
    private int userInd;
    private int messageInd;

    public MessageBoard() {
        messages = new HashMap<>();
        messagesByDate = new Message[CONSTANTS.MAX_MESSAGES];
        users = new String[CONSTANTS.MAX_USERS];
        userInd = 0;
        messageInd = 0;
    }
    public synchronized int getMessageID(){
        return messageInd;
    }
    public synchronized boolean getUser(String username) {
        for (int i = 0; i < userInd; i++) {
            if (users[i].equals(username)) {
                return true;
            }
        }
        return false;
    }

    public synchronized String[] getAllUsers() {
        return users;
    }

    public synchronized void addUser(String username) {
        if (userInd < CONSTANTS.MAX_USERS) {
            users[userInd] = username;
            userInd++;
        } else {
            System.out.println("Max user limit reached. Cannot add more users.");
        }
    }

    public synchronized void removeUser(String username) {
        for (int i = 0; i < userInd; i++) {
            if (users[i].equals(username)) {
                // Shift elements to the left
                for (int j = i; j < userInd - 1; j++) {
                    users[j] = users[j + 1];
                }
                users[userInd - 1] = null;
                userInd--;
                break;
            }
        }
    }

    public synchronized void addMessage(int id, Message message) {

        if (messageInd < CONSTANTS.MAX_MESSAGES) {
            messages.put(id, message);
            messagesByDate[messageInd] = message;
            messageInd++;
        } else {
            System.out.println("Max message limit reached. Cannot add more messages.");
        }
    }

    public synchronized Message[] getLast2() {
        Message[] retMessages = new Message[2];
        if (messageInd >= 2) {
            retMessages[0] = messagesByDate[messageInd - 1];
            retMessages[1] = messagesByDate[messageInd - 2];
        } else if (messageInd == 1) {
            retMessages[0] = messagesByDate[messageInd - 1];
            retMessages[1] = null;
        } else {
            retMessages[0] = retMessages[1] = null;
        }
        return retMessages;
    }

    public synchronized Message getMessage(int id) {
        return messages.get(id);
    }

    public synchronized void deleteMessage(int id) {
        if (!messages.containsKey(id)) {
            System.out.println("Message ID not found.");
            return;
        }
        messages.remove(id);
        for (int i = 0; i < messageInd; i++) {
            if (messagesByDate[i].messageID == id) {
                for (int j = i; j < messageInd - 1; j++) {
                    messagesByDate[j] = messagesByDate[j + 1];
                }
                messagesByDate[messageInd - 1] = null;
                messageInd--;
                break;
            }
        }
    }
}

final class JSONRequest implements Runnable {
    private Socket socket;
    private MessageBoard messageBoard;
    private String user;

    // Constructor
    public JSONRequest(Socket socket, MessageBoard messageBoard) {
        this.socket = socket;
        this.messageBoard = messageBoard;
        this.user = null;
    }

    // Implement the run() method of the Runnable interface
    public void run() {
        try {
            processRequest();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processRequest() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            // Read the request line
            String requestLine = in.readLine();
            System.out.println("Request: " + requestLine);

            if (requestLine.startsWith("POST")) {
                handlePostRequest(in, out);
            } else {
                sendNotFound(out);
            }

        } catch (IOException e) {
            System.err.println("Error processing request: " + e.getMessage());
        }
        // finally {
        // try {
        // System.out.println("Attempting to close socket.");
        // socket.close();
        // } catch (IOException e) {
        // System.err.println("Error closing socket: " + e.getMessage());
        // }
        // }
    }

    private void handlePostRequest(BufferedReader in, BufferedWriter out) throws IOException {
        // Read headers and skip them
        String line;
        int contentLength = 0;

        while (!(line = in.readLine()).isEmpty()) {
            System.out.println("Header: " + line);
            if (line.startsWith("Content-Length")) {
                contentLength = Integer.parseInt(line.split(": ")[1]);
            }
        }

        // Read the JSON body
        char[] body = new char[contentLength];
        in.read(body);
        String jsonString = new String(body);

        // Parse the JSON data
        JsonObject jsonObject;
        try (JsonReader jsonReader = Json.createReader(new StringReader(jsonString))) {
            jsonObject = jsonReader.readObject();
            System.out.println("Received JSON: " + jsonObject.toString());
        }

        // Send Response(s) JSON(s)
        buildJsonResponse(out, jsonObject);
    }

    /**
     * Used to send a 400 BAD RESPONSE with an error message in the json
     * 
     * @param out        The BufferedWrite to output the JSON message
     * @param jsonObject The JsonObject that holds the request object
     * @param error      The String of the error message to include in the JSON of
     *                   the Server response
     * @throws IOException
     */
    private void sendErrorJsonResponse(BufferedWriter out, JsonObject jsonObject, String error) throws IOException {
        System.out.println("JSON Error: " + error);
        // violates protocol
        JsonObject responseJson = Json.createObjectBuilder().build();
        responseJson = Json.createObjectBuilder(responseJson)
                .add("type", "ServerDeny")
                .add("receivedData", jsonObject)
                .add("error", error)
                .build();

        // Convert the response JSON to string and send it
        String jsonResponse = responseJson.toString();
        out.write("HTTP/1.1 400 BAD RESPONSE\r\n");
        out.write("Content-Type: application/json\r\n");
        out.write("Content-Length: " + jsonResponse.length() + "\r\n");
        out.write("Connection: keep-alive\r\n\r\n");
        //out.write("\r\n");
        out.write(jsonResponse);
        out.flush();
        System.out.println("Sent response: " + jsonResponse);
    }

    /**
     * Used to send a 200 OK with a JSON body
     * 
     * @param out
     * @param jsonObject
     * @throws IOException
     */
    private void sendJsonResponse(BufferedWriter out, JsonObject jsonObject) throws IOException {
        String jsonResponse = jsonObject.toString();
        out.write("HTTP/1.1 200 OK\r\n");
        out.write("Content-Type: application/json\r\n");
        out.write("Content-Length: " + jsonResponse.length() + "\r\n");
        out.write("Connection: keep-alive\r\n\r\n"); // Set connection close header
        out.write(jsonResponse);
        out.flush();
        System.out.println("Sent response: " + jsonResponse);
    }

    // TODO:
    // Want to more clearly be able to see each command a client can put
    // More logging needed on server side
    // Comments describing code
    // Less nesting
    private void buildJsonResponse(BufferedWriter out, JsonObject jsonObject) throws IOException {
        JsonObject responseJson;
        // Error Cases
        // Empty JSON object Error
        if (jsonObject.isEmpty()) {
            sendErrorJsonResponse(out, jsonObject, "Empty JSON object");
            return;
        }
        // JSON object lacking type key Error
        if (!jsonObject.containsKey("type")) {
            sendErrorJsonResponse(out, jsonObject, "Request missing key 'type'");
            return;
        }
        // JSON object lacking action key Error
        if (!jsonObject.containsKey("action")) {
            sendErrorJsonResponse(out, jsonObject, "Request missing key 'action'");
            return;
        }

        String type = jsonObject.getString("type");
        String action = jsonObject.getString("action");

        // Handling all clientRequest requests
        // One can observe that each action will match a Client possible option
        if ("clientRequest".equals(type)) {
            if ("join".equals(action)) {
                // Handles the client join functionality

                System.out.println("Performing join operation.");
                // The request must include a username, otherwise throw an error
                if (!jsonObject.containsKey("username")) {
                    String errorMessage = "In order to perform join command, you must include key 'username' in request.";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }

                String username = jsonObject.getString("username");

                // Checking if the user already exists.
                // If they don't, add the user.
                // Otherwise, throw an error
                if (!messageBoard.getUser(username)) {
                    messageBoard.addUser(username);
                    this.user = username;
                    responseJson = Json.createObjectBuilder()
                            .add("type", "ServerAffirm")
                            .add("receivedData", jsonObject)
                            .build();
                } else {
                    String errorMessage = "In order to perform join command, you must join with a unique username.";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
            } 
            else if ("postMessage".equals(action)) //Handles post Message request
            {
                if (!jsonObject.containsKey("messageContent") || !jsonObject.containsKey("messageSubject")) {
                    String errorMessage = "In order to perform join command, you must include key 'username', key 'messageContent', key 'messageSubject' in request";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                System.out.println("Performing join operation.");

                String messageContent = jsonObject.getString("messageContent");
                String messageSubject = jsonObject.getString("messageSubject");

                if (this.user == null){
                    String errorMessage = "To post a message the user must be in the public group, try performing the join first";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                if(!messageBoard.getUser(this.user))
                {
                    String errorMessage = "This user is not in the public group";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }

                int messageID = messageBoard.getMessageID();
                Message message = new Message();
                message.content = messageContent;
                message.subject = messageSubject;
                message.messageID = messageID;
                message.postDate = LocalDate.now().toString();
                message.sender = this.user;

                messageBoard.addMessage(messageID, message);
                responseJson = Json.createObjectBuilder()
                            .add("type", "ServerAffirm")
                            .add("receivedData", jsonObject)
                            .build();

            } else {
                // Handles the situation in which the action is invalid
                // The action should match one of the possible actions for the client
                String errorMessage = "Invalid request action. The received action was " + action
                        + " which is invalid.";
                sendErrorJsonResponse(out, jsonObject, errorMessage);
                return;
            }
        } else {
            // Request type is not a clientRequest, which is currently an issue due
            // to not supporting anything beyond that
            String errorMessage = "Invalid request type. The received type was " + type + " which is invalid.";
            sendErrorJsonResponse(out, jsonObject, errorMessage);
            return;
        }

        sendJsonResponse(out, responseJson);
    }

    private void sendNotFound(BufferedWriter out) throws IOException {
        String notFoundMessage = "404 Not Found";
        out.write("HTTP/1.1 404 Not Found\r\n");
        out.write("Content-Length: " + notFoundMessage.length() + "\r\n");
        out.write("\r\n");
        out.write(notFoundMessage);
        out.flush();
    }
}
