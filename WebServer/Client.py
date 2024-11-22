import json
import socket
import time
import tkinter as tk
from tkinter import scrolledtext
import threading

def buildJSON(args) -> dict:
    """
    Creates the JSON which will be sent to the server
    """
    ret = {}
    for i in range(0, len(args) - 1, 2):
        ret[args[i]] = args[i + 1]
    return ret

class Client:
    """
    A class to represent the client which is connecting to the server.

    This class uses a client socket to send and receive messages from the server. It allows the user to 
    provide commands konwn by the server which will be executed on the server, then receive the response
    from the server.

    Attributes:
    ----------
    host : str
        The host to connect to the server on using the socket.
    port : int
        The port of the host of the server to connect to using the server.

    Methods:
    -------
    connect():
        Connects to the server using the socket.
    send_message(message)
        Sends a message to the server.
    execute_command(command):
        Executes a command that was received from the user by sending it to the server.
    receive_response():
        Receives the server's response.
    format_response(response):
        Formats the response to show the user.
    format_response_data(response):
        Formats the response based on the data-type of the message.
    close():
        Closes the socket's connection to the server.
    """
    def __init__(self, host="localhost", port=6789):
        self.host = host
        self.port = port
        self.clientSocket = None

    def connect(self):
        """ Connects to the server. """
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
        
    def send_message(self, message: str):
        """ Sends the given message to the server. """
        self.clientSocket.sendall((json.dumps(message) + "\n").encode("utf-8"))

    def execute_command(self, command: str):
        """ Executes the command received from the user by sending to the server. """
        print(f"Executing command: {command}")
        parsedComms = command.split(" ")
        command = parsedComms[0]
        if command == "connect":
            self.connect()
            # Start listening for messages after connecting
            return True
        elif command == "join":
            if len(parsedComms) == 2:
                message = buildJSON(["type", "clientRequest", "action", "join", "username", parsedComms[1]])
            else:
                message = buildJSON(["type", "clientRequest", "action", "join"])
            self.send_message(message)
        elif command == "post":
            if len(parsedComms) >= 3:
                messageSubject = parsedComms[1]
                messageContent = " ".join(parsedComms[2:])
                message = buildJSON(["type", "clientRequest", "action", "postMessage", "messageSubject", messageSubject, "messageContent", messageContent])
                self.send_message(message)
                print(f"Post request sent: {message}")
            else:
                print("ERROR: You must submit the subject and the content for the message to be sent.")
        elif command == "leave":
            if (len(parsedComms))!=1:
                print("ERROR: Leave command does not take any arguments")
            message = buildJSON(["type", "clientRequest", "action", "leave"])
            self.send_message(message)
        elif command == "message":
            if (len(parsedComms)!=2):
                print("ERROR: message command takes 1 parameter only: messageID")
            message = buildJSON(["type", "clientRequest", "action", "getMessage", "messageID",int(parsedComms[1])])
            self.send_message(message)
        elif command == "users":
            #retrieve list of users
            message = buildJSON(["type", "clientRequest", "action", "getUsers"])
            self.send_message(message)
        elif command == "exit":
            #need to perform a %leave if not already done to clean the connection up
            message = buildJSON(["type", "clientRequest", "action", "leave"])
            self.send_message(message)
            #close connection and return false as we are no longer running
            client.close()
            return False
        else:
            print("Invalid Command.")
            return True

        return True

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
        print(response)
        return response.strip()  # Strip any trailing whitespace or newlines
    
    def format_response(self, response):
        """Formats the response to show to the user."""

        #Handle if response is multiple jsons
        multiples = []
        closed = 0
        flag = 0
        content = ""
        for ch in response:
            content += ch
            if ch == "{":
                closed += 1
                flag = True
            elif ch == "}":
                closed -= 1
        
            # When a complete JSON object is found
            if closed == 0 and flag:
                try:
                    multiples.append(json.loads(content))
                    content = ""
                    flag = False
                except json.JSONDecodeError:
                    print(f"Error: Invalid JSON detected in fragment: {content}")
                    content = ""
                    flag = False
                    return
        
        #Now, if we have multiple jsons, format all of them
        returnList = []
        for validJson in multiples:
            data = self.format_response_data(validJson)
            response_type = validJson.get('type', 'Unknown')
            
            if response_type == "ServerAffirm":
                message = f"Server (Affirmation): {data}"
            elif response_type == "ServerNotification":
                message = f"Server (Notification): {data}"
            else:
                message = f"Server (Error): {data}"
            
            print(message)
            returnList.append(message)
        return returnList
    
    def format_response_data(self, response: dict) -> str:
        """Formats the response based on the data-type of the message."""
        print(f"{response=}")  # Debug statement; remove or replace with logging in production.
        
        data_type = response.get('data-type', 'unknown')
        if data_type == "text":
            return response.get('data', "No data available")
        elif data_type == "list":
            return f"{response.get('data-title', 'List')}\n" + "\n".join(
                f" - {item}" for item in response.get('data', [])
            )
        elif data_type == "message":
            message = (
                f"Message-ID : {response.get('message-id', 'Unknown ID')}\n"
                f"Message-Subject : {response.get('message-subject', 'No Subject')}\n"
                f"Post-Date : {response.get('post-date', 'Unknown Date')}\n"
                f"Sender : {response.get('sender', 'Unknown Sender')}\n"
                f"{response.get('data', 'No message data')}\n"
            )
            return message
        else:
            return "Unknown data type"

    def close(self):
        """ Closes the socket's connection to the server. """
        if self.clientSocket:
            self.clientSocket.close()
            self.clientSocket = None
            print("Connection closed")

class ClientGUI:
    """
    A class to represent the graphical user interface for a client in a message board application.

    This class provides the GUI for intereacting with the server via the client messaging system.
    It allows the user to send commands and get the responses from the server and display them.

    Attributes:
    ----------
    root : tkinter.Tk
        The root window for the application.
    client : Client
        The client that is connected to the server via a socket.

    Methods:
    -------
    display_message(message):
        Appends a message to the board and updates the display.
    send_message(event=None):
        Sends the user's message to the server and updates the display.
    start_receiving():
        Starts a thread to continuously listen for messages from the server.
    """
    def __init__(self, root, client: Client):
        self.root = root
        self.client = client
        self.root.title("localhost:6789 Bulletin Board")

        # Create a scrollable text area to display messages
        self.text_area = scrolledtext.ScrolledText(root, wrap=tk.WORD, state='disabled', width=50, height=20)
        self.text_area.grid(row=0, column=0, columnspan=2, padx=10, pady=10)

        # Entry box for user input
        self.input_box = tk.Entry(root, width=40)
        self.input_box.grid(row=1, column=0, padx=10, pady=10)
        self.input_box.bind("<Return>", self.send_message)

        # Send button
        self.send_button = tk.Button(root, text="Send", command=self.send_message)
        self.send_button.grid(row=1, column=1, padx=10, pady=10)

    def display_message(self, message):
        """Display a message in the text area."""
        self.text_area.config(state='normal')
        self.text_area.insert(tk.END, f"{message}\n")
        self.text_area.yview(tk.END)  # Auto-scroll to the end
        self.text_area.config(state='disabled')

    def send_message(self, event=None):
        """Send a message to the server."""
        message = self.input_box.get()
        if message:
            # Call your existing send_message function here
            # send_message(message)
            self.display_message(f"You: {message}")  # Display your own message
            running = self.client.execute_command(message)
            self.input_box.delete(0, tk.END)
            if not running:
                self.quit()

    def start_receiving(self):
        """Continuously check for messages from the server."""
        def receive_loop():
            while True:
                if self.client.clientSocket:
                    time.sleep(1)
                    # Call your existing receive_message function here
                    response = self.client.receive_response()
                    formattedResponse = self.client.format_response(response)
                    # response = "Server: Example response"  # Placeholder for server message
                    #Response may contain multiple
                    if formattedResponse is not None:
                        for resp in formattedResponse:
                            self.display_message(resp)
        
        threading.Thread(target=receive_loop, daemon=True).start()

    def quit(self):
        self.client.close()
        print("Closing the GUI")
        self.root.destroy()

if __name__ == "__main__":
    root = tk.Tk()
    client = Client()
    client_gui = ClientGUI(root, client)
    client_gui.start_receiving()
    root.mainloop()
