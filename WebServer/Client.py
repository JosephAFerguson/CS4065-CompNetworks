from ast import parse
import socket
import json
import threading
import time

class HTTPClient:
    def __init__(self, host="localhost", port=6789):
        self.host = host
        self.port = port
        self.clientSocket = None

    def connect(self):
        """Establishes a connection to the server, with error handling."""
        if self.clientSocket is None:
            try:
                self.clientSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.clientSocket.connect((self.host, self.port))
                print(f"Connected to {self.host} on port {self.port}")
            except Exception as e:
                print(f"Connection failed: {e}")

    def send_post_request(self, path, data):
        """Send an HTTP POST request with JSON"""
        if not self.clientSocket:
            print("Client is not connected.")
            return
        jsonData = json.dumps(data)
        contentLength = len(jsonData)
        
        request = (
            f"POST {path} HTTP/1.1\r\n"
            f"Host: {self.host}\r\n"
            "Content-Type: application/json\r\n"
            f"Content-Length: {contentLength}\r\n"
            "Connection: keep-alive\r\n\r\n"  # Set connection to keep-alive so the connection persists
            f"{jsonData}"
        )
        self.clientSocket.sendall(request.encode("utf-8"))
        print(f"POST request sent to {path} with JSON data: {data}")

    def receive_response(self):
        """Receive the server's response, handling timeouts."""
        response = b""
        self.clientSocket.settimeout(10)  # Timeout after 10 seconds
        try:
            while True:
                data = self.clientSocket.recv(4096)
                if not data:
                    break
                response += data
        except socket.timeout:
            print("Timeout reached while receiving data.")
        except Exception as e:
            print(f"Error receiving response: {e}")
        return response.decode("utf-8")

    def close(self):
        """Close the connection."""
        if self.clientSocket:
            self.clientSocket.close()
            self.clientSocket = None
            print("Connection closed")


def buildJSON(args) -> dict:
    """
    Creates the JSON which will be sent to the server
    @param type: str = The type of the request
    @param action: str = The action to be performed on the server side
    """
    ret = {}
    for i in range(0,len(args)-1, 2):
        ret[args[i]] = args[i+1]
    return ret

def listenForMessages(client: HTTPClient) -> None:
    """
    Listens for messages from the server and when it catches them it will
    output them based on the message
    @param client: HTTPClient = The client which is receiving the messages
    @returns: None
    """
    global mbActive
    while True:
        # Receive Response
        response = client.receive_response()
        with mbLock:
            splitResponse = response.split("\r\n\r\n")
            if len(splitResponse) > 1:
                body = splitResponse[1]
                responseJSON = json.loads(body)
            else:
                print("ERROR: No body in response from server")
                return True
            # ServerAffirm is returned when action is successful
            if (responseJSON['type'] == "ServerAffirm"):
                print(f"Action {responseJSON['receivedData']['action']} was performed successfully.")
            else:
                print(f"ERROR: Action {responseJSON['receivedData']['action']} returned error {responseJSON['error']}.")
            mbActive = True
            #time.sleep(0.5) # Waiting half a second so it doesn't run on an empty body

def executeCommand(client: HTTPClient, command: str) -> bool:
    """
    Executes the command from the CLI
    @param client: HTTPClient = The client which is sending the requests
    @param command: command = The name of the command to execute
    @returns: bool = Whether the CLI should continue execution
    """
    global mbActive
    parsedComms = command.split(" ")
    command = parsedComms[0]
    if command == "connect":
        client.connect()
        listeningThread.start() # If the client is connected, start listening
        mbActive = True
        return True
    elif command == "join":
        if (len(parsedComms)==2):
            message = buildJSON(["type", "clientRequest", "action", "join", "username", parsedComms[1]])
        else:
            message = buildJSON(["type", "clientRequest", "action", "join"])
        client.send_post_request("/users", message)
    elif command == "post":
        if (len(parsedComms) == 3):
            messageSubject = parsedComms[1]
            messageContent = parsedComms[2]
            message = buildJSON(["type", "clientRequest", "action", "postMessage", "messageSubject", messageSubject, "messageContent", messageContent])
            client.send_post_request("/users", message)
        else:
            print("ERROR: You must submit the subject and the content for the message to be sent.")
    elif command == "exit":
        client.close()
        return False
    else:
        print("Invalid Command.")

    # The message back from the server is handled on the listening thread
    # Need to disable message board while waiting for response from server
    mbActive = False
    return True


SERVER_ADDRESS = "localhost"
SERVER_PORT = 6789

mbLock = threading.Lock() # The lock which controls whether reading from messaging board or outputting messages from server
mbActive = True # This is True if message board good to go. False if need to wait until server message read 

if __name__ == "__main__":
    client = HTTPClient()

    # Creating the listening thread
    listeningThread = threading.Thread(target=listenForMessages, args=[client])
    listeningThread.daemon = True # Exit thread when main program exits

    # Main loop for the message board
    running = True
    while running:
        if mbActive:
            with mbLock:
                command = input("Message Board> ")
                running = executeCommand(client, command)
