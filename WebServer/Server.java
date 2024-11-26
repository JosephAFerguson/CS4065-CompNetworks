package com.example;//what the maven project calls this to run the file

//All packages needed for socket communication
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;

//time package
import java.time.LocalDate;

//data structures we use for our data storage
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

//logging package
import java.util.logging.Logger;

//json packages
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;

//Class for the program constants, only used for the messageboard and inherited private message board class
final class CONSTANTS {
    public final static int MAX_USERS = 100;
    public final static int MAX_MESSAGES = 1000;
}

//Class to store information related to a message, and all the attributes for one.
final class Message {
    int messageID;
    String sender;
    String postDate;
    String subject;
    String content;
}

//Class for the public message board 
class MessageBoard {
    private HashMap<Integer, Message> messages;
    private Message[] messagesByDate;
    private String[] users;
    private int userInd;
    private int messageInd;

    //Constructor to create the arrays and hashmap to hold the messages and users
    public MessageBoard() {
        messages = new HashMap<>();
        messagesByDate = new Message[CONSTANTS.MAX_MESSAGES];
        users = new String[CONSTANTS.MAX_USERS];
        userInd = 0;
        messageInd = 0;
    }

    /*getters and setters - cont.
    */
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
        //here we make a copy of all the users from our user array, to a new array of length to hold only the used-memory part of the array
        String[] returnUsers = new String[userInd+1];
        for (int i= 0;i< userInd+1;i++)
        {
            returnUsers[i] = users[i];
        }
        return returnUsers;//nulls will be checked on the other side anyway
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
                // Shift elements to the left to remove the user at that index
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
    //returns the last 2 messages, if there aren't any, return null ones
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

    //this is not used currently, was not specified in project description
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
                }//shift left like in remove user
                messagesByDate[messageInd - 1] = null;//set last index to null
                messageInd--;
                break;
            }
        }
    }
}

//Class for the inherited private message board
final class PrivateMessageBoard extends MessageBoard {
    private int groupID;//only change is that we have a groupID and groupName
    private String groupName;

    public PrivateMessageBoard(Integer groupID, String groupName){
        super();
        this.groupID = groupID;
        this.groupName = groupName;
    }
    //getters and setters - cont.
    public Integer getGroupId(){
        return groupID;
    }
    public String getGroupName(){
        return groupName;
    }
}

//Class for the tasks, that the task thread will process, needs to store the hashcode of the connected client socket, for in/out streams
final class Task {
    private int clientSocketHash;
    private JsonObject request;

    public Task(int clientSocketHash, JsonObject request) {
        this.clientSocketHash = clientSocketHash;
        this.request = request;
    }
    //getters and setters - cont.
    public int getClientSocketHash() {
        return clientSocketHash;
    }

    public JsonObject getJsonObject() {
        return request;
    }
}

//Class for the Profile of the user/connected client, needs to be maintained at server level,
//not thread level as in/out streams need to be kept open at all times (they will close the whole connection if not)
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

    //getters and setters cont.
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

//Server/Main class for the file to run, maintains all connections and accepts new ones
public final class Server {
    //Maintain the connections/Profiles for each connected socket, the id mapping the relation is the hashcode
    private static ConcurrentHashMap<Integer, Profile> activeUsers = new ConcurrentHashMap<>();
    //Maintain the queue for the task thread, this is a blocking one needed for multithreading
    private static BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();

    public static void main(String[] argv) throws Exception {

        // Establish Public and Private Message Boards
        MessageBoard messageBoard = new MessageBoard();
        PrivateMessageBoard[] privateGroups = new PrivateMessageBoard[5];
        privateGroups[0] = new PrivateMessageBoard(0, "Engineers");
        privateGroups[1] = new PrivateMessageBoard(1, "Scientists");
        privateGroups[2] = new PrivateMessageBoard(2, "Teachers");
        privateGroups[3] = new PrivateMessageBoard(3, "Blue Collars");
        privateGroups[4] = new PrivateMessageBoard(4, "Retirees");

        //port this will be hosted on
        int port = 6789;

        // The thread will process all tasks, i.e send all notifications and process requests
        Thread taskThread = new Thread(new TaskThread(taskQueue, messageBoard, privateGroups));
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
    //logger for better output on what this thread is acchomplishing
    private static final Logger logger = Logger.getLogger(TaskThread.class.getName());
    private MessageBoard messageBoard;
    private PrivateMessageBoard[] privateGroups;

    //Constructor, basically we set all of our data structures to what the server gives us - THIS IS SHARED DATA
    public TaskThread(BlockingQueue<Task> taskQueue, MessageBoard messageBoard, PrivateMessageBoard[] privateGroups) {
        this.taskQueue = taskQueue;
        this.messageBoard = messageBoard;
        this.privateGroups = privateGroups;
    }

    //The running functionality of the thread
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

    //Send a json response regarding an error that occured in the received json package,a server deny with the error message
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
        // Get the hashcode (hc) associated with this task
        Integer hc = task.getClientSocketHash();

        //check if the user is still connected - cannot perform operations for a disconnected user
        if (!Server.isUserActive(hc)) {
            return;
        }

        //get the output streams and json package associated with the User/profile
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

        //Get the username, and provided arguments for type and action
        String username = User.getUserName();
        String type = jsonObject.getString("type");
        String action = jsonObject.getString("action");

        //Welcome user to server and prompt for username
        if ("ServerWelcome".equals(type)){ //this is server side only, client will not provide this, this is in case of a disconnect
            logger.info("Welcoming new user to server, providing help and start commands");

            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            arrayBuilder.add("Welcome to the Server: Please Join The Public Group by inputting join followed by your choice of username");
            arrayBuilder.add("For info on all other commands, enter help");

            responseJson = Json.createObjectBuilder()
                    .add("type", "ServerAffirm")
                    .add("data-type", "list")
                    .add("data-title", "Welcome")
                    .add("data", arrayBuilder)
                    .add("receivedData", jsonObject)
                    .build();
            //send the welcome message
            sendJsonResponse(out, responseJson);

            //Show user private groups
            JsonArrayBuilder arrayBuilder2 = Json.createArrayBuilder();

            // Add each group to the JSON array
            for (PrivateMessageBoard pg : privateGroups) {
                String showcase = "Group Name: " + pg.getGroupName() + " - ID:" + pg.getGroupId();
                arrayBuilder2.add(showcase);
            }
            JsonObject responseJson2;
            
            responseJson2 = Json.createObjectBuilder()
                    .add("type", "ServerAffirm")
                    .add("data-type", "list")
                    .add("data-title", "Groups")
                    .add("data", arrayBuilder2)
                    .add("receivedData", jsonObject)
                    .build();

            //send the json with the private groups 
            sendJsonResponse(out, responseJson2);
            return;

        // Handling all clientRequest requests
        // One can observe that each action will match a Client possible option
        } else if ("ServerRemove".equals(type)) {//this is server side only, client will not provide this 
            // Notify all users //This needs to be done before we remove the user
            Message blankM = new Message();
            notifyAllUsers(false, blankM, username + " has left the message board");
            
            //Get List of Private Groups user is in
            ArrayList<Integer> whichGroupsUserIn = new ArrayList<Integer>();
            for(int i =0;i<5;i++){
                if(privateGroups[i].getUser(username)){
                    whichGroupsUserIn.add(i);
                }
            }
            //notify the private groups as well
            notifyAllPrivateUsers(whichGroupsUserIn, false, blankM, username + " has left the private message board: ");
            
            // Remove the user from the private message groups and the public message board and nullify the socket's username
            // profile
            for(int i = 0; i<5;i++){
                privateGroups[i].removeUser(username);
            }
            messageBoard.removeUser(username);
            User.setUsername(null);
            return;//no serveraffirm as user disconnected
        } else if ("clientRequest".equals(type)) {//all client requests / commands will be processed here
            if ("help".equals(action)){
                //provide list of commands
                JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
                arrayBuilder.add("Command [param1] [param2, etc] - Info on Command");
                arrayBuilder.add("join [username] - Lets you join the public group with a specified unique username");
                arrayBuilder.add("post [messageSubject(no spaces)] [content(spaces allowed)] - Lets you post a message to the public group");
                arrayBuilder.add("users - Lets you see a list of users in the public group");
                arrayBuilder.add("leave - Lets you leave the public group, which will also kick you from any private groups as well");
                arrayBuilder.add("message [messageID] - Lets you retrieve the content of a message with a given MessageID");
                arrayBuilder.add("exit - Lets you disconnect from the server entirely");
                arrayBuilder.add("groups - Lets you see a list of private groups, provided you are in the public group");
                arrayBuilder.add("grouppost [groupID] [messageSubject(no spaces)] [content(spaces allowed)] - Lets you post a message to the private group specified with the groupID, with the provided subject and content, given that the user is in the private group");
                arrayBuilder.add("groupjoin [groupID] - Lets you join a private group with the specified groupID");
                arrayBuilder.add("groupusers [groupID] - Lets you see the list of users from a private group with the specified groupID, provided you are in the private group");
                arrayBuilder.add("groupleave [groupID] - Lets you leave a private group with the specified groupID, provided you are in the private group");
                arrayBuilder.add("groupmessage [groupID] [messageID] - Lets you see the content of a private group message from a private group with the specified groupID and messageID, provided you are in the private group");

                responseJson = Json.createObjectBuilder()
                        .add("type", "ServerAffirm")
                        .add("data-type", "list")
                        .add("data-title", "Commands")
                        .add("data", arrayBuilder)
                        .add("receivedData", jsonObject)
                        .build();
                sendJsonResponse(out, responseJson);
                return;
            } else if ("join".equals(action)) {
                // Handles the client join functionality

                logger.info("Performing join operation.");
                // The request must include a username, otherwise throw an error
                if (!jsonObject.containsKey("username")) {
                    String errorMessage = "In order to perform join command, you must include key 'username' in request.";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                //user provided a username
                username = jsonObject.getString("username");
                User.setUsername(username);
                // Checking if the user already exists.
                // If they don't, add the user.
                // Otherwise, throw an error
                if (!messageBoard.getUser(username)) {

                    messageBoard.addUser(username);
                    //this will be sent over
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
                 * We also need to show the new user the list of active users
                 */
                Message blankM = new Message();
                notifyAllUsers(false, blankM , username + " has entered the message board");
                
                //get last 2 messages to show user
                Message[] last2 = messageBoard.getLast2();
                
                if(last2[0] != null){
                    // Send message to client
                    JsonObject messageJson1 = Json.createObjectBuilder()
                    .add("type", "ServerNotification")
                    .add("data-type", "message")
                    .add("group", "Public")
                    .add("message-id", last2[0].messageID)
                    .add("sender", last2[0].sender)
                    .add("post-date", last2[0].postDate)
                    .add("message-subject", last2[0].subject)
                    .add("data", last2[0].content)
                    .build();
                    sendJsonResponse(User.getOut(), messageJson1);
                }
                if(last2[1]!=null){
                    // Send message to client
                    JsonObject messageJson2 = Json.createObjectBuilder()
                    .add("type", "ServerNotification")
                    .add("data-type", "message")
                    .add("group", "Public")
                    .add("message-id", last2[1].messageID)
                    .add("sender", last2[1].sender)
                    .add("post-date", last2[1].postDate)
                    .add("message-subject", last2[1].subject)
                    .add("data", last2[1].content)
                    .build();
                    sendJsonResponse(User.getOut(), messageJson2);
                }

                //get all users for the new client
                String[] allUsers = messageBoard.getAllUsers();

                // Convert String[] to JsonArray using JsonArrayBuilder
                JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
                for (String user : allUsers) {
                    if (user == null) {
                        break;
                    }
                    arrayBuilder.add(user);
                }

                JsonObject usersJson = Json.createObjectBuilder()
                    .add("type", "ServerNotification")
                    .add("data-type", "list")
                    .add("data-title", "Users")
                    .add("data", arrayBuilder.build())
                    .build();
                    sendJsonResponse(User.getOut(), usersJson);

                
            } else if ("leave".equals(action)) {

                if (username.equals(null)) { // handle a user trying to leave when they aren't in the group anyway
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
                Message blankM = new Message();
                notifyAllUsers(false, blankM, username + " has left the message board");
                
                //Get List of Private Groups user is in
                ArrayList<Integer> whichGroupsUserIn = new ArrayList<Integer>();
                for(int i =0;i<5;i++){
                    if(privateGroups[i].getUser(username)){
                        whichGroupsUserIn.add(i);
                    }
                }
                notifyAllPrivateUsers(whichGroupsUserIn, false, blankM, username + " has left the private message board: ");
                // Remove the user from the private message groups and the public message board and nullify the socket's username
                // profile
                for(int i = 0; i<5;i++){
                    privateGroups[i].removeUser(username);
                }
                // Remove user
                messageBoard.removeUser(username);
                User.setUsername(null);
            } else if ("postMessage".equals(action)) {// Handles post Message request
                /* From now on all the commands(so to say) 
                 * will have the same checks/testing for valid json input
                 * only necessary comments will be provided
                 */
                if (!jsonObject.containsKey("messageContent") || !jsonObject.containsKey("messageSubject")) {
                    String errorMessage = "In order to post a public message you must include messageContent and messageSubject";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                logger.info("Performing message post operation.");

                String messageContent = jsonObject.getString("messageContent");
                String messageSubject = jsonObject.getString("messageSubject");

                if (username.equals(null)) {
                    String errorMessage = "To post a message the user must be in the public group, try performing the join first";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }

                //Make the actual message
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
                notifyAllUsers(true, message, username + " posted: " + messageContent);// needs to be changed
            } else if ("getMessage".equals(action)) {// handles a get Message with message ID request
                //checks as always
                if (username.equals(null)) {
                    String errorMessage = "To get a message the user must be in the public group, try performing the join first";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                if (!jsonObject.containsKey("messageID")) {
                    String errorMessage = "In order to get a message the key 'messageID' must be included in the request";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                //check if the message exists
                int messageID = jsonObject.getInt("messageID");
                Message message = new Message();
                if (messageBoard.tryMessageID(messageID)) {
                    message = messageBoard.getMessage(messageID);
                } else {
                    String errorMessage = "Message ID does not exist in this message board";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                //send the message back
                responseJson = Json.createObjectBuilder()
                        .add("type", "ServerAffirm")
                        .add("data-type", "message")
                        .add("group", "Public")
                        .add("message-id", message.messageID)
                        .add("sender", message.sender)
                        .add("post-date", message.postDate)
                        .add("message-subject", message.subject)
                        .add("data", message.content)
                        .add("receivedData", jsonObject)
                        .build();
                sendJsonResponse(out, responseJson);
                return;
            } else if ("getUsers".equals(action)) {
                //checks as always
                if (username.equals(null)) {
                    String errorMessage = "To get a list of users the user must be in the public group, try performing the join first";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                //get all the users
                String[] users = messageBoard.getAllUsers();
                JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();

                // Add each user to the JSON array
                for (String user : users) {
                    if (user == null) {//check for nulls
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

            } else if ("getGroups".equals(action)){
                //User is requesting groups in which he can join
                //privateGroups
                JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();

                // Add each group to the JSON array
                for (PrivateMessageBoard pg : privateGroups) {
                    String showcase = "Group Name: " + pg.getGroupName() + " - ID:" + pg.getGroupId();
                    arrayBuilder.add(showcase);
                }

                responseJson = Json.createObjectBuilder()
                        .add("type", "ServerAffirm")
                        .add("data-type", "list")
                        .add("data-title", "Groups")
                        .add("data", arrayBuilder)
                        .add("receivedData", jsonObject)
                        .build();

                sendJsonResponse(out, responseJson);
                return;

            } else if ("groupJoin".equals(action)){
                //User is to join a group
                //privateGroups

                //User needs to provide groupID
                if (!jsonObject.containsKey("groupID")) {
                    String errorMessage = "In order to to join a group, groupID must be provided";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                int groupID = jsonObject.getInt("groupID");

                //Handle case upon user not having username yet
                if (username==null) {
                    String errorMessage = "You must join the public group and have a username prior to joining a private group";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }

                //Handle case upon user already being in group
                if (privateGroups[groupID].getUser(username)) {
                    String errorMessage = "To join a group you must not already be in the group and have a unique username for that group";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }

                //add user to group
                privateGroups[groupID].addUser(username);

                //send affirm
                responseJson = Json.createObjectBuilder()
                        .add("type", "ServerAffirm")
                        .add("data-type", "text")
                        .add("data", "User request to join private group successful")
                        .add("receivedData", jsonObject)
                        .build();

                sendJsonResponse(out, responseJson);

                //give him the past two messages from the private group
                /*
                 * Here we need to add functionality to notify all other users of the new user
                 * We also need to show the new user to that 2 latest messages
                 * We also need to show the new user the list of active users
                 */

                Message blankM = new Message();
                ArrayList<Integer> pgs = new ArrayList<Integer>();
                pgs.add(groupID);
                notifyAllPrivateUsers(pgs, false, blankM, username + " has joined the private message board : ");
                //change this
                //notifyAllUsers(false, blankM , username + " has entered the message board");
                Message[] last2 = privateGroups[groupID].getLast2();
                
                if(last2[0] != null){
                    // Send message to client
                    JsonObject messageJson1 = Json.createObjectBuilder()
                    .add("type", "ServerNotification")
                    .add("data-type", "message")
                    .add("group", privateGroups[groupID].getGroupName())
                    .add("message-id", last2[0].messageID)
                    .add("sender", last2[0].sender)
                    .add("post-date", last2[0].postDate)
                    .add("message-subject", last2[0].subject)
                    .add("data", last2[0].content)
                    .build();
                    sendJsonResponse(User.getOut(), messageJson1);
                }
                if(last2[1]!=null){
                    // Send message to client
                    JsonObject messageJson2 = Json.createObjectBuilder()
                    .add("type", "ServerNotification")
                    .add("data-type", "message")
                    .add("group", privateGroups[groupID].getGroupName())
                    .add("message-id", last2[1].messageID)
                    .add("sender", last2[1].sender)
                    .add("post-date", last2[1].postDate)
                    .add("message-subject", last2[1].subject)
                    .add("data", last2[1].content)
                    .build();
                    sendJsonResponse(User.getOut(), messageJson2);
                }

                //notify private group client joined of join
                String[] allUsers = privateGroups[groupID].getAllUsers();

                // Convert String[] to JsonArray using JsonArrayBuilder
                JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
                for (String user : allUsers) {
                    if (user == null) {
                        break;
                    }
                    arrayBuilder.add(user);
                }
                //send the information back
                JsonObject usersJson = Json.createObjectBuilder()
                    .add("type", "ServerNotification")
                    .add("data-type", "list")
                    .add("data-title", "Private: ID - " + privateGroups[groupID].getGroupId() + " - Name - " + privateGroups[groupID].getGroupName() + " Users")
                    .add("data", arrayBuilder.build())
                    .build();
                    sendJsonResponse(User.getOut(), usersJson);
                return;

            } else if ("groupPostMessage".equals(action)){
                //checks as usual
                if (!jsonObject.containsKey("messageContent") || !jsonObject.containsKey("messageSubject") || !jsonObject.containsKey("groupID")) {
                    String errorMessage = "In order to post a private group message you must include the groupID, messageContent, and messageSubject";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                int groupID = jsonObject.getInt("groupID");
                if (username.equals(null)){
                    String errorMessage = "In order to post a private group message you must be in the public group first.";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                if (!privateGroups[groupID].getUser(username)){
                    String errorMessage = "In order to post a private group message you must be in the private group first.";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                }
                //get the message params
                String messageContent = jsonObject.getString("messageContent");
                String messageSubject = jsonObject.getString("messageSubject");
                int messageID = messageBoard.getMessageID();
                Message message = new Message();
                message.content = messageContent;
                message.subject = messageSubject;
                message.messageID = messageID;
                message.postDate = LocalDate.now().toString();
                message.sender = username;

                //add the message and send affirm
                privateGroups[groupID].addMessage(messageID, message);
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
                ArrayList<Integer> pgs = new ArrayList<Integer>();
                pgs.add(groupID);//add the group message was posted in
                notifyAllPrivateUsers(pgs, true, message, username + " posted: " + messageContent);// needs to be changed
                return;
            } else if ("getGroupUsers".equals(action)){
                //checks as usual
                if (username.equals(null)) {
                    String errorMessage = "To get a list of private group users the user must be in the public group, try performing the join first";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                if (!jsonObject.containsKey("groupID")){
                    String errorMessage = "To get a list of private group users the groupID must be provided";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                int groupID = jsonObject.getInt("groupID");
                String[] users = privateGroups[groupID].getAllUsers();
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
                        .add("data-title", "Private: ID - " + privateGroups[groupID].getGroupId() + " - Name - " + privateGroups[groupID].getGroupName() + " Users:")
                        .add("data", arrayBuilder)
                        .add("receivedData", jsonObject)
                        .build();
                sendJsonResponse(out, responseJson);
                return;

            } else if ("groupLeave".equals(action)){
                //checks as usual
                if (username.equals(null)) {
                    String errorMessage = "To leave a private group you must first be in the public group and in a private group";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                if (!jsonObject.containsKey("groupID")){
                    String errorMessage = "To leave a private group the groupID must be provided";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                int groupID = jsonObject.getInt("groupID");

                if(!privateGroups[groupID].getUser(username)){
                    String errorMessage = "To leave a private group you must be in the private group first.";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                
                //make the private group int array to pass to the notify all private users function
                ArrayList<Integer> pgs = new ArrayList<Integer>();
                pgs.add(groupID);
                Message blankM = new Message();

                privateGroups[groupID].removeUser(username);
                notifyAllPrivateUsers(pgs, false, blankM, username + " has left the private: ");
                // Build the response JSON object
                responseJson = Json.createObjectBuilder()
                        .add("type", "ServerAffirm")
                        .add("data-type", "text")
                        .add("data", "Client request to leave private group successful")
                        .add("receivedData", jsonObject)
                        .build();

                sendJsonResponse(out, responseJson);
                return;
            } else if ("getGroupMessage".equals(action)){
                //checks as usual
                if (username.equals(null)) {
                    String errorMessage = "To get a list of private group users the user must be in the public group, try performing the join first";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                if (!jsonObject.containsKey("groupID")||!jsonObject.containsKey("messageID")){
                    String errorMessage = "To get a list of private group users the groupID and messageID must be provided";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                int groupID = jsonObject.getInt("groupID");

                if(!privateGroups[groupID].getUser(username)){
                    String errorMessage = "To get a message from a private group you must be in the private group first.";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }
                //get the message from the private board and send response
                int messageID = jsonObject.getInt("messageID");

                Message message = new Message();
                if (privateGroups[groupID].tryMessageID(messageID)) {
                    message = privateGroups[groupID].getMessage(messageID);
                } else {
                    String errorMessage = "Message ID does not exist in this message board";
                    sendErrorJsonResponse(out, jsonObject, errorMessage);
                    return;
                }

                responseJson = Json.createObjectBuilder()
                        .add("type", "ServerAffirm")
                        .add("data-type", "message")
                        .add("group", "Private: ID - " + privateGroups[groupID].getGroupId() + " - Name - " + privateGroups[groupID].getGroupName())
                        .add("message-id", message.messageID)
                        .add("sender", message.sender)
                        .add("post-date", message.postDate)
                        .add("message-subject", message.subject)
                        .add("data", message.content)
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
        sendJsonResponse(out, responseJson);//last case in which there is a forgotten return statement to send the affirm
    }
    private void notifyAllPrivateUsers(ArrayList<Integer> whichPrivateGroups, Boolean M, Message message, String notificationMessage){
        if(M) //if it is a new message or not
        {
            //need notify users in a private group(s) about new message
            for(int groupID : whichPrivateGroups){

                //inefficient, but we go through each user profile connected to the server,
                //test if they are apart of the specific privateGroup, and try sending them the data if they are
                for (Profile userProfile : Server.getActiveUsers().values()) {

                    String name = userProfile.getUserName();//get name of connected user

                    if(privateGroups[groupID].getUser(name)){//test if they are in privateGroup
                        try {
                            BufferedWriter clientOut = userProfile.getOut();
                            JsonObject messageJson = Json.createObjectBuilder()
                                    .add("type", "ServerNotification")
                                    .add("data-type", "message")
                                    .add("group", "Private: ID - " + privateGroups[groupID].getGroupId() + " - Name - " + privateGroups[groupID].getGroupName())
                                    .add("message-id", message.messageID)
                                    .add("message-subject", message.subject)
                                    .add("post-date", message.postDate)
                                    .add("sender", message.sender)
                                    .add("data", message.content)
                                    .build();
        
                            clientOut.write(messageJson.toString());
                            clientOut.flush();
                        } catch (IOException e) {
                            logger.info("Error notifying users: " + e.getMessage());
                        }
                    }
                }
            }
        }
        else
        {
            //need to notify users in a private group(s) about new notification
            //need notify users in a private group(s) about new message
            for(int groupID : whichPrivateGroups){
                //same thing as in above if case
                for (Profile userProfile : Server.getActiveUsers().values()) {

                    String name = userProfile.getUserName();//get name of connected user

                    if(privateGroups[groupID].getUser(name)){
                        try {
                            BufferedWriter clientOut = userProfile.getOut();
                            JsonObject notificationJson = Json.createObjectBuilder()
                                    .add("type", "ServerNotification")
                                    .add("data-type", "text")
                                    .add("data", notificationMessage + "Private: ID - " + privateGroups[groupID].getGroupId() + " - Name - " + privateGroups[groupID].getGroupName())
                                    .build();
        
                            clientOut.write(notificationJson.toString());
                            clientOut.flush();
                        } catch (IOException e) {
                            logger.info("Error notifying users: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }
    private void notifyAllUsers(Boolean M, Message message, String notificationMessage) {
        if(M) // If it is a new message post or not
        {
            //go through all active users and send them the notification
            for (Profile userProfile : Server.getActiveUsers().values()) {
                try {
                    BufferedWriter clientOut = userProfile.getOut();
                    JsonObject messageJson = Json.createObjectBuilder()
                            .add("type", "ServerNotification")
                            .add("data-type", "message")
                            .add("group", "Public")
                            .add("message-id", message.messageID)
                            .add("message-subject", message.subject)
                            .add("post-date", message.postDate)
                            .add("sender", message.sender)
                            .add("data", message.content)
                            .build();

                    clientOut.write(messageJson.toString());
                    clientOut.flush();
                } catch (IOException e) {
                    logger.info("Error notifying users: " + e.getMessage());
                }
            }
        }
        else
        {
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
}

//class for the one - to many(depending on number of clients) threads listening to a client endlessly
//puts "tasks" on the task queue for the task thread to do
final class ListenThread implements Runnable {
    private static final Logger logger = Logger.getLogger(ListenThread.class.getName());
    private Socket socket;
    private BlockingQueue<Task> taskQueue;

    // Constructor
    public ListenThread(Socket socket, BlockingQueue<Task> taskQueue) {
        this.socket = socket;
        this.taskQueue = taskQueue;
        //user is connected, add a task to welcome them
        JsonObject welcomeJson = Json.createObjectBuilder()
            .add("type", "ServerWelcome")
            .add("action", "ServerWelcome")
            .build();
        //try to add to the task queue
        Task welcomeTask = new Task(this.socket.hashCode(), welcomeJson);
        try {
            taskQueue.put(welcomeTask); // Blocks if the queue is full
            logger.info("[Client " + socket.hashCode() + "] Task added to queue.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Preserve the interrupt status
            logger.warning("[Client " + socket.hashCode() + "] Interrupted while adding task to queue.");
        }
    }

    //the listening functionality for the thread
    @Override
    public void run() {
        try {
            //get the input stream
            BufferedReader in = Server.getUserProfile(this.socket.hashCode()).getIn();

            while (!Thread.currentThread().isInterrupted()) {//while this thread can run, run
                
                String clientRequest = in.readLine();//listen for input and get it

                // Break the loop if the stream is closed or null data is received
                if (clientRequest == null) {
                    logger.info("[Client " + socket.hashCode() + "] Connection closed by client.");
                    break;
                }
                //read the json 
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
                    JsonObject removeJson = Json.createObjectBuilder()
                        .add("type", "ServerRemove")
                        .add("action", "ServerRemove")
                        .build();
                //send a task to the task thread to remove the user from the public/private groups client was in
                Task removeTask = new Task(this.socket.hashCode(), removeJson);
                try {
                    taskQueue.put(removeTask); // Blocks if the queue is full
                    logger.info("[Client " + socket.hashCode() + "] Task added to queue.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Preserve the interrupt status
                    logger.warning("[Client " + socket.hashCode() + "] Interrupted while adding task to queue.");
                }
                socket.close();//closes everything as well
                Server.removeUserProfile(this.socket.hashCode());//removes from the server
                logger.info("[Client " + socket.hashCode() + "] Connection closed and user removed.");
            } catch (IOException e) {
                logger.warning("[Client " + socket.hashCode() + "] Error closing socket: " + e.getMessage());
            }
        }
    }

}
