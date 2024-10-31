# CS4065-CompNetworks
CS4065 Computer Networks and Networked Computing
Project 2 - A Simple Bulletin Board Using Socket Programming
Instructor: Giovani Abuaitah
Notes: JavaFX needs to be downloaded from https://gluonhq.com/products/javafx/ 

## Message Board Commands:
* join = Connect to server
* exit = Close the connection to the server
* viewBoard = View the last 2 messages that were posted


## Notes: 
the javax. package needs downloaded from https://gluonhq.com/products/javafx/ 
the org.json package imported in Server.java needs the following:
          in settings.json in vscode or whatever ide, the lib/json.jar file needs referenced.

## JSON Structure
Client -> Server:
{
    "type": The type of the request. I.e. "clientRequest",
    "action": The action to be performed on the server. I.e. "join", "viewBoard",
    
}