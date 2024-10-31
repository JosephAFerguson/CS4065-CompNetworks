package ProgAssign2.WebServer;
import javax.json.*;
import org.json;
import java.io.* ;
import java.net.* ;
import java.util.* ;

public final class Server 
{
    public static void main(String argv[]) throws Exception
    {
        int port = 6789;
        //Establish the listen socket
        ServerSocket serverSocket = new ServerSocket(port);
        //Process HTTP service requests in an infinite loop
        while(true)
        {
            //Listen for a TCP connection request.
            Socket socket = serverSocket.accept();

            //Construct an object to process the HTTP request message
            JSONRequest request = new JSONRequest(socket);
            //Create a new thread to process the request
            Thread thread = new Thread(request);
            //Start the thread
            thread.start();
        }        
    }
}
final class JSONRequest implements Runnable
{
    Socket socket;

    //Constructor
    public JSONRequest(Socket socket) throws Exception
    {
        this.socket = socket;
    }

    private static void sendBack(JsonObject jBack) throws Exception
    {
        //send a json back
    }
    //Implement the run() method of the Runnable interface
    public void run()
    {
        try
        {
            process();
        }
        catch (Exception e)
        {
            System.out.println(e);
        }
    }
    private void process() throws Exception
    {
        //Do something to process json request
        try {
            // Step 1: Get the input stream from the socket
            InputStream inputStream = socket.getInputStream();
            
            // Step 2: Wrap the input stream in an InputStreamReader
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
            
            // Step 3: Create a JsonReader from the InputStreamReader
            try (JsonReader jsonReader = Json.createReader(inputStreamReader)) {
                // Read the JSON object
                JsonObject jsonObject = jsonReader.readObject();
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //Close streams and socket
        socket.close();
    }
}

