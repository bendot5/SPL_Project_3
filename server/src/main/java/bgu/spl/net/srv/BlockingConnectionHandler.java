package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    private final MessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;
    private Connections<T> serverConnections;
    private int connectionId = -1;
    private UserManagement userMangement;
    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, MessagingProtocol<T> protocol, Connections<T> connections, UserManagement users) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        this.serverConnections = connections;
        this.userMangement = users;
    }

    public void setConnectionId(int connectionId){
        this.connectionId = connectionId;
    }

    public int getConnectionId(){
        return this.connectionId;
    }


    @Override
    public void run() {
        try (Socket sock = this.sock) { //just for automatic closing
            int read;
            setConnectionId(this.serverConnections.connectHandler(this));
            this.protocol.start(this.getConnectionId(), this.serverConnections, this.userMangement);
            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());
            while (!protocol.shouldTerminate() && (connected && ((read = in.read()) >= 0))) {
                T nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    T response = protocol.process(nextMessage);
                    if (response != null) {
                        out.write(encdec.encode(response));
                        out.flush();
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        connected = false;
        //not graceful shutdown
        this.serverConnections.disconnectUser(this.getConnectionId());
        this.serverConnections.disconnectClient(this.getConnectionId());
        if((sock!=null) && (!sock.isClosed())){
            sock.close();
        }
        Thread.currentThread().interrupt();
    }

    @Override
    public void send(T msg) {
        try{
            if(msg!=null){
                out.write(encdec.encode(msg));
                out.flush();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
