from ast import parse
import socket
import json
import threading
import time
# import tkinter as tk
# from tkinter import scrolledtext

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

# class ClientGUI:
#     def __init__(self, root):
#         self.root = root
#         self.root.title("Client Messaging Application")

#         # Create a scrollable text area to display messages
#         self.text_area = scrolledtext.ScrolledText(root, wrap=tk.WORD, state='disabled', width=50, height=20)
#         self.text_area.grid(row=0, column=0, columnspan=2, padx=10, pady=10)

#         # Entry box for user input
#         self.input_box = tk.Entry(root, width=40)
#         self.input_box.grid(row=1, column=0, padx=10, pady=10)
#         self.input_box.bind("<Return>", self.send_message)

#         # Send button
#         self.send_button = tk.Button(root, text="Send", command=self.send_message)
#         self.send_button.grid(row=1, column=1, padx=10, pady=10)

def buildJSON(args) -> dict:
    """
    Creates the JSON which will be sent to the server
    """
    ret = {}
    for i in range(0, len(args) - 1, 2):
        ret[args[i]] = args[i + 1]
    return ret


# def listenForMessages(client: HTTPClient) -> None:
#     """
#     Continuously listens for messages from the server and processes them.
#     """
#     while True:
#         try:
#             if not client.clientSocket:
#                 print("ERROR: Client is not connected to the server.")
#                 break
#             response = client.receive_response()
#             if response:
#                 print(f"Message from server: {response}")
#             else:
#                 print("No response received.")
#         except socket.timeout:
#             print("Waiting for more data...")
#         except Exception as e:
#             print(f"ERROR in listening thread: {e}")
#             break

def listenForMessages(client: HTTPClient) -> None:
    """
    Listens for messages from the server and when it catches them it will
    output them based on the message
    @param client: HTTPClient = The client which is receiving the messages
    @returns: None
    """
    global mbActive
    responseCache = [] # Cache which is used to hold resposnes that have not had the opportunity to be written
    while True:
        # Receive Response
        response = client.receive_response()
        response = json.loads(response)

        responseCache.append(response)

        if mbLock.locked():
            continue

        print(f"{response=}")
        with mbLock:
            while len(responseCache) != 0:
                print(f"{responseCache=}")
                response = responseCache.pop()
                # ServerAffirm is returned when action is successful
                if (response['type'] == "ServerAffirm"):
                    print(f"Action {response['receivedData']['action']} was performed successfully.")
                elif(response['type'] == "ServerNotification"):
                    print(f"Message from Server: {response['data']}")
                else:
                    print(f"ERROR: Action {response['receivedData']['action']} returned error {response['error']}.")
            mbActive = True
                #time.sleep(0.5) # Waiting half a second so it doesn't run on an empty body


def executeCommand(client: HTTPClient, command: str):
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
        mbActive = True
        return True
    elif command == "join":
        
        if len(parsedComms) == 2:
            message = buildJSON(["type", "clientRequest", "action", "join", "username", parsedComms[1]])
        else:
            message = buildJSON(["type", "clientRequest", "action", "join"])

        # Send request without waiting for immediate response
        client.clientSocket.sendall((json.dumps(message)+ "\n").encode("utf-8"))
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
    elif command == "leave":
        if (len(parsedComms))!=1:
            print("ERROR: Leave command does not take any arguments")
        message = buildJSON(["type", "clientRequest", "action", "leave"])
        client.clientSocket.sendall((json.dumps(message)+ "\n").encode("utf-8"))
    elif command == "message":
        if (len(parsedComms)!=2):
            print("ERROR: message command takes 1 parameter only: messageID")
        message = buildJSON(["type", "clientRequest", "action", "getMessage", "messageID",int(parsedComms[1])])
        client.clientSocket.sendall((json.dumps(message)+ "\n").encode("utf-8"))
    elif command == "users":
        #retrieve list of users
        message = buildJSON(["type", "clientRequest", "action", "getUsers"])
        client.clientSocket.sendall((json.dumps(message)+ "\n").encode("utf-8"))
    elif command == "exit":
        #need to perform a %leave if not already done to clean the connection up
        message = buildJSON(["type", "clientRequest", "action", "leave"])
        client.clientSocket.sendall((json.dumps(message)+ "\n").encode("utf-8"))
        #close connection and return false as we are no longer running
        client.close()
        return False
    else:
        print("Invalid Command.")
        mbActive = True
        return True

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
    listeningThread = threading.Thread(target=listenForMessages, args=(client,))
    listeningThread.daemon = True  # Exit thread when main program exits
    # Main loop for the message board
    running = True
    while running:
        if mbActive:
            with mbLock:
                # try:
                command = input("Message Board> ")
                running = executeCommand(client, command)
                # except KeyboardInterrupt:
                #     print("\nExiting.")
                #     running = False

    # Ensure the thread terminates gracefully
    if listeningThread.is_alive():
        listeningThread.join()
    print("Exited cleanly.")

