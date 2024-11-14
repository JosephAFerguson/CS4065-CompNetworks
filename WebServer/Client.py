from ast import parse
import socket
import json
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

def executeCommand(client: HTTPClient, command: str) -> bool:
    """
    Executes the command from the CLI
    @param client: HTTPClient = The client which is sending the requestscon
    @param command: command = The name of the command to execute
    @returns: bool = Whether the CLI should continue execution
    """
    parsedComms = command.split(" ")
    command = parsedComms[0]
    if command == "connect":
        client.connect()
    elif command == "join":
        if (len(parsedComms)==2):
            message = buildJSON(["type", "clientRequest", "action", "join", "username", parsedComms[1]])
        else:
            message = buildJSON(["type", "clientRequest", "action", "join"])
        client.send_post_request("/users", message)
    elif command == "viewBoard":
        message = buildJSON(["type", "clientRequest", "action", "viewBoard"])
        client.send_post_request("/users", message)
    elif command == "exit":
        client.close()
        return False
    else:
        print("Invalid Command.")
    
    # Receive Response
    response = client.receive_response()
    print(f"Response received: {response}")
    return True


SERVER_ADDRESS = "localhost"
SERVER_PORT = 6789

if __name__ == "__main__":
    client = HTTPClient()

    running = True
    while running:
        command = input("Message Board> ")
        running = executeCommand(client, command)
