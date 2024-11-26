## CS4065-CompNetworks
CS4065 Computer Networks and Networked Computing
Project 2 - A Simple Bulletin Board Using Socket Programming
Instructor: Giovani Abuaitah

## Team Members:
Joe Ferguson, Eli Rindler, Braden Monnin

## How to Compile and Run:
tkinter needs installed for python. If you have python 3.7 or later this is already installed. If need to install tkinter
the command pip install tk can be used.

We used vscode as the editor for this. The extension pack: "Extension Pack for Java v0.29.0" needs installed in vscode.
In the same directory as the client.py file, create a maven project, it will ask for 2 things and provide default values, just hit enter for each of the default values.
Navigate to the new /demo/src/main/java~ and edit the main.java(you can rename it) to contain the code from this repository.
Add the following inside the pom.xml found in /demo:

<dependencies>
    <!-- javax.json dependency -->
    <dependency>
        <groupId>javax.json</groupId>
        <artifactId>javax.json-api</artifactId>
        <version>1.1.4</version>  <!-- Or any newer version -->
    </dependency>

    <!-- Implementation for javax.json -->
    <dependency>
        <groupId>org.glassfish</groupId>
        <artifactId>javax.json</artifactId>
        <version>1.1.4</version> <!-- Or any newer version -->
    </dependency>

    <!-- For logging or other dependencies -->
    <!-- You can include other dependencies as needed -->
</dependencies>

To compile and run the server, if using vscode on the Java file inside of the maven project the run button can be clicked to 
compile and run the server code.

To run the python code Client.py can be open and the run button can be clicked. To do this by the terminal, python WebServer/Client.py can be used.


## Message Board Commands:
* help = Explains what the available commands do.
* connect = Connect to server at localhost:6789.
* join [username] = Connect to public server with username as [username].
* post [subject] [content...] = Post a message to the public message board with the subject specified as [subject] and the content(which can include spaces) as [content...].
* users = Get a list of the users on the public message board.
* leave = Leave the public message board.
* message [messageID] = Gets the message content for the message with the id [messageID].
* exit = Close the connection to the server at localhost:6789.
* groups = Gets a list of all of the private groups that can be joined.
* groupjoin [groupID] = Joins the group of the specified [groupID].
* grouppost [groupID] [subject] [content...] = Posts the message with [subject] as the subject and [content...] as the content (which can contain spaces) into the private message group with id [groupID].
* groupusers [groupID] = Gets the list of users for the private group of id [groupID].
* groupleave [groupID] = Leaves the private group of id [groupID].
* groupmessage [groupID] [messageID] = In private group with id [groupID] it gets the content of the message with private message id of [messageID].


## JSON Structure
{
    "type": The type of the request. I.e. "clientRequest",
    "data-type": The type of the data being sent. I.e. "text", "list", "message".
    "data": The data of the messsage. This could represent different things based on the data-type. 
    I.e. text for text, list for list, message content for message.
    ...
    There are also data-type specific fields which are highlighted below.
}

Data Types:
* text
{"type":"ServerAffirm", "data-type":"text", "data":"alskfjalksdfj  aslkfasldfj asldfj alskdjf alksdfjaklsjfalkdfa", "receivedData": ...}

* list
{"type":"ServerAffirm", "data-type":"list", "data-title":"Users", "data":["ejr", "ejr2", "ejr3"], "receivedData": ...}

* message
{"type":"ServerAffirm", "data-type":"message", "message-id":123456, "sender":"ejr", "post-date": "11-20-2024 19:39", "message-subject": "This is the subject of the message", "data": "This is the body of the message", "receivedData": ...}

## Major Issues
* Problem: Client could not accept commands and listen for responses from the server at the same time. Solution: Splitting the commands actions and the listening for response actions onto separate threads. This enabled the client to take in commands whilst also listening for the server responses and outputting them.
* Problem: The client needs to output varying types of information. For example it needs to output text, lists(with titles), and messages. This caused one field in the JSON protocol to not be enough to properly output the information in a scalable way. Solution: Adding the data-type field. This allowed as to divide functionality based on the data-types. Only text, list, and message data types were needed and it allowed us to split the functionality and provide other fields based on the data-type. It also allowed us to output the data differently depending on the data-type.
* Problem: Client input to Server was blocking as the .readline() method for the BufferedReader will block forever unless it sees a terminating newline character. Solution: Add the terminating newline character to each json message to the server.
* Problem: Server terminating client connection based on unforseen reasons. Reasons turned out to be that the java wrapper classes for the input/output streams close the connection socket completely if the wrapper classes are closed or destroyed. This means that, when a method is over, the program/operating system will destroy the memory for the in/out streams therefore closing the socket completely. The solution, in part with the solution for the server multithreading, was to keep track of all the active connections, make a Profile class, which contains the in/out streams, so that they are always open until the client disconnects. Therefore the server will never terminate a client connection (in theory).
* Problem: Server had issue with the endless listening connected to client, could not maintain constant listening and processing the requests. Several hypotheses were made as to this, but in all the solution came about by doing something completely different with the multithreading on the server. Solution: Add a new thread, born from the server, which does all the processsing of the client requests. Requests are called Tasks, which maintain the socket hashcode (which is unique), as to which client made the request and which request they made, stored in a Blocking Queue, which the processing/task thread endlessly tries to "take" a task and process it. This lets the listening thread(s) always listen, and upon receiving input, add a task to the shared Queue.
