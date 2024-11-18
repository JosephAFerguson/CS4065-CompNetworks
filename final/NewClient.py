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
        if self.clientSocket:
            print("Already connected.")
            return
        try:
            self.clientSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.clientSocket.connect((self.host, self.port))
            print(f"Connected to {self.host} on port {self.port}")
        except Exception as e:
            print(f"Connection failed: {e}")
            self.clientSocket = None


    def receive_response(self):
        """Receive the server's response, handling timeouts."""
        response = ""
        try:
            response = self.clientSocket.recv(4096).decode("utf-8")
            if not response:
                return
        except socket.timeout:
            print("Timeout reached while receiving data.")
        except Exception as e:
            print(f"Error receiving response: {e}")
        return response.strip()  # Strip any trailing whitespace or newlines


    def close(self):
        if self.clientSocket:
            self.clientSocket.close()
            self.clientSocket = None
            print("Connection closed")



def buildJSON(args) -> dict:
    """
    Creates the JSON which will be sent to the server
    """
    ret = {}
    for i in range(0, len(args) - 1, 2):
        ret[args[i]] = args[i + 1]
    return ret


def listenForMessages(client: HTTPClient) -> None:
    """
    Continuously listens for messages from the server and processes them.
    """
    while True:
        try:
            if not client.clientSocket:
                print("ERROR: Client is not connected to the server.")
                break
            response = client.receive_response()
            if response:
                print(f"Message from server: {response}")
            else:
                print("No response received.")
        except socket.timeout:
            print("Waiting for more data...")
        except Exception as e:
            print(f"ERROR in listening thread: {e}")
            break



def executeCommand(client: HTTPClient, command: str) -> bool:
    """
    Executes the command from the CLI
    """
    global mbActive
    parsedComms = command.split(" ")
    command = parsedComms[0]
    if command == "connect":
        client.connect()
        # Start listening for messages after connecting
        listeningThread.start()
        return True
    elif command == "join":
        
        if len(parsedComms) == 2:
            message = buildJSON(["type", "clientRequest", "action", "join", "username", parsedComms[1]])
        else:
            message = buildJSON(["type", "clientRequest", "action", "join"])

        # Send request without waiting for immediate response
        client.clientSocket.sendall((json.dumps(message)+ "\n").encode("utf-8"))
        print(client.receive_response())
        print(f"Join request sent: {message}")
        return True
    elif command == "post":
        if len(parsedComms) == 3:
            messageSubject = parsedComms[1]
            messageContent = parsedComms[2]
            message = buildJSON(["type", "clientRequest", "action", "postMessage", "messageSubject", messageSubject, "messageContent", messageContent])
            client.clientSocket.sendall((json.dumps(message)+ "\n").encode("utf-8"))
            print(f"Post request sent: {message}")
        else:
            print("ERROR: You must submit the subject and the content for the message to be sent.")
        print(client.receive_response())
        return True
    elif command == "exit":
        client.close()
        return False
    else:
        print("Invalid Command.")
        return True


SERVER_ADDRESS = "localhost"
SERVER_PORT = 6789

mbLock = threading.Lock()  # The lock which controls whether reading from messaging board or outputting messages from server
mbActive = True  # This is True if message board good to go. False if need to wait until server message read

if __name__ == "__main__":
    client = HTTPClient()

    # Creating the listening thread
    listeningThread = threading.Thread(target=listenForMessages, args=(client,))
    listeningThread.daemon = True  # Exit thread when main program exits
    # Main loop for the message board
    running = True
    while running:
        try:
            command = input("Message Board> ")
            running = executeCommand(client, command)
        except KeyboardInterrupt:
            print("\nExiting.")
            running = False

    # Ensure the thread terminates gracefully
    if listeningThread.is_alive():
        listeningThread.join()
    print("Exited cleanly.")

