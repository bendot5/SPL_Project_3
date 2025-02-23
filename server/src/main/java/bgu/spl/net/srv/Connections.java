package bgu.spl.net.srv;

import java.io.IOException;

public interface Connections<T> {

    int generateMessageId();

    boolean connectUser (int connectionId, String userName);

    int connectHandler (ConnectionHandler<T> handler);

    boolean subscribeChannel (String channel, String subscriptionId, String userName);
    
    boolean unsubscribeChannelById(String subscriptionId, String userName);

    boolean unsubscribeChannelByName(String channel, String userName);

    void send(int connectionId, T msg);

    boolean send(String channel, String userName, T msg );

    int getConnectionIdForUser (String userName);

    String getSubscriptionId (String userName, String channel);

    void disconnectUser(int connectionId);

    void disconnectClient(int connectionId);
}
