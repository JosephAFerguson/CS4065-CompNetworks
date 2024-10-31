import socket
import json
import time

# Class which will act as the client
class HTTPClient:
    def __init__(self, host="localhost", port=6789):
        self.host = host # The host to connect to. Defaulted to localhost
        self.port = port # The port on the server to connect to. Defaulted to 6789
        self.clientSocket = None
    
    def connect(self):
        """Establishes a connection to the server"""
        self.clientSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.clientSocket.connect((self.host, self.port))
        print(f"Connected to {self.host} on port {self.port}")

    def send_get_request(self, path="/"):
        """Send an HTTP GET request."""
        request = f"GET {path} HTTP/1.1\r\nHost: {self.host}\r\nConnection: keep-alive\r\n\r\n"
        self.clientSocket.sendall(request.encode("utf-8"))
        print(f"GET request sent to {path}")

    def send_post_request(self, path, data):
        """Send an HTTP POST request with JSON"""
        # Encoding data as JSON
        jsonData = json.dumps(data)
        contentLength = len(jsonData)

        # Construct the HTTP POST request with JSON content type
        request = (
            f"POST {path} HTTP/1.1\r\n"
            f"Host: {self.host}\r\n"
            "Content-Type: application/json\r\n"
            f"Content-Length: {contentLength}\r\n"
            "Connection: keep-alive\r\n\r\n"
            f"{jsonData}"
        )
        self.clientSocket.sendall(request.encode("utf-8"))
        print(f"POST request sent to {path} with JSON data: {data}")

    def receive_response(self):
        """Receive the server's response."""
        response = b""
        while True:
            data = self.clientSocket.recv(4096)
            if not data:
                break
            response += data
        return response.decode("utf-8")
    
    def close(self):
        """Close the connection"""
        if self.clientSocket:
            self.clientSocket.close()
            self.clientSocket = None
            print("Connection closed")

SERVER_ADDRESS = "localhost" # The server to connect the client to
SERVER_PORT = 6789 # The port the server is running on

if __name__ == "__main__":
    TEST_DOMAIN = "webhook.site"
    TEST_PORT = 80
    PATH = "/"

    # Create client instance
    client = HTTPClient(TEST_DOMAIN, TEST_PORT)

    # Connect to the server
    client.connect()
   
    # Send a POST with JSON data
    jsonData = {'test': 123, 'test2': 456}
    client.send_post_request(PATH , jsonData)

    # Send a GET
    client.send_get_request(PATH)

    # Close the connection
    client.close()