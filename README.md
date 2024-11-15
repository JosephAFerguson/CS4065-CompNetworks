# CS4065-CompNetworks
CS4065 Computer Networks and Networked Computing
Project 2 - A Simple Bulletin Board Using Socket Programming
Instructor: Giovani Abuaitah

## Message Board Commands:
* join = Connect to server
* exit = Close the connection to the server
* viewBoard = View the last 2 messages that were posted

## Notes: 
pip install keepalive-socket
""the javax. package needs downloaded from https://gluonhq.com/products/javafx/ "" -This may not be needed anymore
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


## JSON Structure
Client -> Server:
{
    "type": The type of the request. I.e. "clientRequest",
    "action": The action to be performed on the server. I.e. "join", "viewBoard",
    
}
