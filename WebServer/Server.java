package com.example;

import javax.json.*;

import java.util.HashMap;
import java.util.concurrent.*;
import java.util.logging.*;
import java.io.*;
import java.time.LocalDate;
import java.net.*;

final class CONSTANTS {
    public final static int MAX_USERS = 100;
    public final static int MAX_MESSAGES = 1000;
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

    public synchronized int getMessageID() {
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

    public synchronized boolean tryMessageID(int id) {
        return messages.containsKey(id);
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

final class Task {
    private int clientSocketHash;
    private JsonObject request;

    public Task(int clientSocketHash, JsonObject request) {
        this.clientSocketHash = clientSocketHash;
        this.request = request;
    }

    public int getClientSocketHash() {
        return clientSocketHash;
    }

    public JsonObject getJsonObject() {
        return request;
    }
}

final class Profile {
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String username;

    public Profile(Socket socket) {
        this.socket = socket;
        this.username = null;
        try {
            this.out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
            this.in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public BufferedReader getIn() {
        return in;
    }

    public BufferedWriter getOut() {
        return out;
    }

    public void closeAll() {
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    public void setUsername(String uname) {
        username = uname;
    }

    public String getUserName() {
        return username;
    }
}

public final class Server {
    private static ConcurrentHashMap<Integer, Profile> activeUsers = new ConcurrentHashMap<>();
    private static BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();

    public static void main(String[] argv) throws Exception {

        // Establish Message Board
        MessageBoard messageBoard = new MessageBoard();
        int port = 6789;

        // The thread will will send all notifications and process requests
        Thread taskThread = new Thread(new TaskThread(taskQueue, messageBoard));
        taskThread.start();

        // Establish the listen socket
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server is listening on port " + port);

        // Listens tp requests in an infinite loop
        while (true) {
            // Listen for a TCP connection request.
            Socket socket = serverSocket.accept();

            // Add to our map of connections
            int socketHash = socket.hashCode();
            Profile newOne = new Profile(socket);
            activeUsers.put(socketHash, newOne);

            // Construct an object to process the HTTP request message
            ListenThread request = new ListenThread(socket, taskQueue);

            // Create a new thread to process the request
            Thread thread = new Thread(request);

            // Start the thread
            thread.start();
        }
    }

    public static synchronized ConcurrentHashMap<Integer, Profile> getActiveUsers() {
        return activeUsers;
    }

    public static synchronized void removeUserProfile(Integer hashCode) {
        if (activeUsers.containsKey(hashCode)) {
            activeUsers.get(hashCode).closeAll();
            activeUsers.remove(hashCode);
        }
    }

    public static synchronized Profile getUserProfile(Integer hashCode) {
        return activeUsers.get(hashCode);
    }

    public static synchronized boolean isUserActive(Integer hashCode) {
        return activeUsers.containsKey(hashCode);
    }
}

class TaskThread implements Runnable {
    private BlockingQueue<Task> taskQueue;
    private static final Logger logger = Logger.getLogger(TaskThread.class.getName());
    private MessageBoard messageBoard;

    public TaskThread(BlockingQueue<Task> taskQueue, MessageBoard messageBoard) {
        this.taskQueue = taskQueue;
        this.messageBoard = messageBoard;
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Take a task from the queue (blocking call, waits if queue is empty)
                Task task = taskQueue.take();

                // Process the task
                try {
                    // Process the request for the client socket
                    processTask(task);

                } catch (IOException e) {
                    logger.warning("Failed to send notification: " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            System.out.println("Notification thread interrupted");
        }
    }

    private void sendErrorJsonResponse(BufferedWriter out, JsonObject jsonObject, String error) throws IOException {
        logger.info("JSON Error: " + error);
        // violates protocol
        JsonObject responseJson = Json.createObjectBuilder().build();
        responseJson = Json.createObjectBuilder(responseJson)
                .add("type", "ServerDeny")
                .add("data-type", "text")
                .add("data", error)
                .add("receivedData", jsonObject)
                .build();

        // Convert the response JSON to string and send it
        String jsonResponse = responseJson.toString();
        out.write(jsonResponse);
        out.flush();
        logger.info("Sent response: " + jsonResponse);
    }

    private void sendJsonResponse(BufferedWriter out, JsonObject jsonObject) throws IOException {
        String jsonResponse = jsonObject.toString();
        out.write(jsonResponse);
        out.flush();
        logger.info("Sent response: " + jsonResponse);
    }

    private void processTask(Task task) throws IOException {
        // Our objects to work with
        Integer hc = task.getClientSocketHash();
        if (!Server.isUserActive(hc)) {
            return;
        }
        Profile User = Server.getUserProfile(hc);
        BufferedWriter out = User.getOut();
        JsonObject jsonObject = task.getJsonObject();
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
        String username = User.getUserName();
        String type = jsonObject.getString("type");
        String action = jsonObject.getString("action");

        // Handling all clientRequest requests
        // One can observe that each action will match a Client possible option
        if ("clientRequest".equals(type)) {
            if ("join".equals(action)) {
                // Handles the client join functionality

                logger.info("Performing join operation.");
                // The request must include a username, otherwise throw an error
                if (!jsonObject.containsKey("username")) {
                    String errorMessage = "In order to perform join command, you must include key 'username' in request.";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }

                username = jsonObject.getString("username");
                User.setUsername(username);
                // Checking if the user already exists.
                // If they don't, add the user.
                // Otherwise, throw an error
                if (!messageBoard.getUser(username)) {
                    messageBoard.addUser(username);

                    responseJson = Json.createObjectBuilder()
                            .add("type", "ServerAffirm")
                            .add("data-type", "text")
                            .add("data", username + " successfully joined the message board.")
                            .add("receivedData", jsonObject)
                            .build();

                } else {
                    String errorMessage = "In order to perform join command, you must join with a unique username.";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                /*
                 * Here we need to add functionality to notify all other users of the new user
                 * We also need to show the new user to that 2 latest messages
                 */
                notifyAllUsers(username + " has entered the message board");
            } else if ("leave".equals(action)) {

                if (username == null) { // handle a user trying to leave when they aren't in the group anyway
                    logger.info("This client is not in the message group: Leave Operation invalid");
                    String errorMessage = "In order to perform leave command, user must first be in message Board";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                // Handles the client leave public group functionality
                logger.info("Performing Leave Operation for user:" + username);

                // Construct message for client
                responseJson = Json.createObjectBuilder()
                        .add("type", "ServerAffirm")
                        .add("data-type", "text")
                        .add("data", username + " successfully left the message board.")
                        .add("receivedData", jsonObject)
                        .build();

                // Notify all users //This needs to be done before we remove the user
                notifyAllUsers(username + " has left the message board");

                // Remove the user from the message board and nullify the socket's username
                // profile
                messageBoard.removeUser(username);
                User.setUsername(null);
            } else if ("postMessage".equals(action)) // Handles post Message request
            {
                if (!jsonObject.containsKey("messageContent") || !jsonObject.containsKey("messageSubject")) {
                    String errorMessage = "In order to perform join command, you must include key 'username', key 'messageContent', key 'messageSubject' in request";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                System.out.println("Performing join operation.");

                String messageContent = jsonObject.getString("messageContent");
                String messageSubject = jsonObject.getString("messageSubject");

                if (username.equals(null)) {
                    String errorMessage = "To post a message the user must be in the public group, try performing the join first";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                int messageID = messageBoard.getMessageID();
                Message message = new Message();
                message.content = messageContent;
                message.subject = messageSubject;
                message.messageID = messageID;
                message.postDate = LocalDate.now().toString();
                message.sender = username;

                messageBoard.addMessage(messageID, message);
                responseJson = Json.createObjectBuilder()
                        .add("type", "ServerAffirm")
                        .add("data-type", "text")
                        .add("data", username + " successfully posted message " + messageID + " to the message board.")
                        .add("receivedData", jsonObject)
                        .build();
                /*
                 * Here we need to add a functionality to notify all other users of the new
                 * message
                 */
                notifyAllUsers(username + " posted: " + messageContent);// needs to be changed
            } else if ("getMessage".equals(action))// handles a get Message with message ID request
            {
                if (username == null) {
                    String errorMessage = "To get a message the user must be in the public group, try performing the join first";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                if (!jsonObject.containsKey("messageID")) {
                    String errorMessage = "In order to get a message the key 'messageID' must be included in the request";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                int messageID = jsonObject.getInt("messageID");
                Message message;
                if (messageBoard.tryMessageID(messageID)) {
                    message = messageBoard.getMessage(messageID);
                } else {
                    String errorMessage = "Message ID does not exist in this message board";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                responseJson = Json.createObjectBuilder()
                        .add("type", "ServerAffirm")
                        .add("data-type", "message")
                        .add("message-id", message.messageID)
                        .add("sender", message.sender)
                        .add("post-date", message.postDate)
                        .add("message-subject", message.subject)
                        .add("data", message.content)
                        .add("receivedData", jsonObject)
                        .build();
            } else if ("getUsers".equals(action)) {
                if (username == null) {
                    String errorMessage = "To get a list of users the user must be in the public group, try performing the join first";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                String[] users = messageBoard.getAllUsers();
                JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();

                // Add each user to the JSON array
                for (String user : users) {
                    if (user == null) {
                        break;
                    }
                    arrayBuilder.add(user);
                }

                // Build the response JSON object
                responseJson = Json.createObjectBuilder()
                        .add("type", "ServerAffirm")
                        .add("data-type", "list")
                        .add("data-title", "Users")
                        .add("data", arrayBuilder)
                        .add("receivedData", jsonObject)
                        .build();
                sendJsonResponse(out, responseJson);
                return;

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

    private void notifyAllUsers(String notificationMessage) {
        for (Profile userProfile : Server.getActiveUsers().values()) {
            try {
                BufferedWriter clientOut = userProfile.getOut();
                JsonObject notificationJson = Json.createObjectBuilder()
                        .add("type", "ServerNotification")
                        .add("data-type", "text")
                        .add("data", notificationMessage)
                        .build();

                clientOut.write(notificationJson.toString());
                clientOut.flush();
            } catch (IOException e) {
                logger.info("Error notifying users: " + e.getMessage());
            }
        }
    }
}

final class ListenThread implements Runnable {
    private static final Logger logger = Logger.getLogger(ListenThread.class.getName());
    private Socket socket;
    private BlockingQueue<Task> taskQueue;

    // Constructor
    public ListenThread(Socket socket, BlockingQueue<Task> taskQueue) {
        this.socket = socket;
        this.taskQueue = taskQueue;
    }

    @Override
    public void run() {
        try {
            BufferedReader in = Server.getUserProfile(this.socket.hashCode()).getIn();

            while (!Thread.currentThread().isInterrupted()) {
                String clientRequest = in.readLine();

                // Break the loop if the stream is closed or null data is received
                if (clientRequest == null) {
                    logger.info("[Client " + socket.hashCode() + "] Connection closed by client.");
                    break;
                }

                JsonObject jsonObject = null;
                try (JsonReader jsonReader = Json.createReader(new StringReader(clientRequest))) {
                    jsonObject = jsonReader.readObject();
                    logger.info("[Client " + socket.hashCode() + "] Received JSON: " + jsonObject.toString());
                } catch (JsonException e) {
                    logger.warning("[Client " + socket.hashCode() + "] Invalid JSON received: " + clientRequest);
                    continue; // Skip to the next input if JSON is invalid
                }

                // Create and enqueue a task for the incoming request
                Task task = new Task(this.socket.hashCode(), jsonObject);
                logger.info("[Client " + socket.hashCode() + "] Created task: " + task);

                try {
                    taskQueue.put(task); // Blocks if the queue is full
                    logger.info("[Client " + socket.hashCode() + "] Task added to queue.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Preserve the interrupt status
                    logger.warning("[Client " + socket.hashCode() + "] Interrupted while adding task to queue.");
                    break; // Exit the loop on interruption
                }
            }
        } catch (IOException e) {
            logger.severe("[Client " + socket.hashCode() + "] IOException in ClientListenerThread: " + e.getMessage());
        } finally {
            // Cleanup resources
            try {
                socket.close();
                Server.removeUserProfile(this.socket.hashCode());
                logger.info("[Client " + socket.hashCode() + "] Connection closed and user removed.");
            } catch (IOException e) {
                logger.warning("[Client " + socket.hashCode() + "] Error closing socket: " + e.getMessage());
            }
        }
    }

}
