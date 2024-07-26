package bgu.spl.net.srv;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl <T> implements Connections <T> {

    private ConcurrentHashMap<Integer, ConnectionHandler<T>> idToConnectionHandler; 

    public ConnectionsImpl() {
        idToConnectionHandler = new ConcurrentHashMap<>();
    }
    
    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) { 
        idToConnectionHandler.putIfAbsent(connectionId, handler); 
    }

    @Override
    public boolean send(int connectionId, T msg) { 
        if (idToConnectionHandler.containsKey(connectionId)) { 
            idToConnectionHandler.get(connectionId).send(msg); 
            return true;
        }
        return false;
    }

    @Override
    public void disconnect(int connectionId) { 
        if (idToConnectionHandler.containsKey(connectionId)) { 
            try {
                idToConnectionHandler.get(connectionId).close();
            } catch (IOException e) {e.printStackTrace();} 
            idToConnectionHandler.remove(connectionId); 
        }
    }
}
