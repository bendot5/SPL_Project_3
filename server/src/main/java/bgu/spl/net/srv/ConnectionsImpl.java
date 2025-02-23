package bgu.spl.net.srv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import bgu.spl.net.api.StompMessagingProtocolImpl;

public class ConnectionsImpl<T> implements Connections<T> {
    private ConcurrentHashMap<String,ConnectionHandler<T>> connectionIdToHandlerMap = new ConcurrentHashMap<String,ConnectionHandler<T>>();
    private ConcurrentHashMap<String,String> connectionIdToUserMap = new ConcurrentHashMap<String,String>();
    private ConcurrentHashMap<String,ConcurrentHashMap<String,String>> channelToUsersMap = new ConcurrentHashMap<String,ConcurrentHashMap<String,String>>();
    private ConcurrentHashMap<String,ConcurrentHashMap<String,String>> userToChannelIdMap = new ConcurrentHashMap<String,ConcurrentHashMap<String,String>>();
    private AtomicInteger connectionIdCounter = new AtomicInteger(0);
    private AtomicInteger messageIdCounter = new AtomicInteger(0);

    public boolean connectUser (int connectionId, String userName){
        boolean result = false;
        if(this.connectionIdToUserMap.get(connectionId + "") == null){
            boolean userIsLoggedIn = false;
            for(String key : this.connectionIdToUserMap.keySet()){
                if(this.connectionIdToUserMap.get(key).equals(userName)){
                    userIsLoggedIn = true;
                }
            }
            if(!userIsLoggedIn){
                this.connectionIdToUserMap.put(connectionId + "", userName);
                result = true;
            }
        }
        return result;

    }

    public int connectHandler (ConnectionHandler<T> handler){
        int id = this.connectionIdCounter.get();
        connectionIdCounter.incrementAndGet();
        this.connectionIdToHandlerMap.putIfAbsent(id + "", handler);
        return id; 
    }

    public int generateMessageId (){
        int id = this.messageIdCounter.get();
        messageIdCounter.incrementAndGet();
        return id; 
    }

    @Override
    public boolean subscribeChannel (String channel, String subscriptionId, String userName){
        boolean result = false;
        this.channelToUsersMap.putIfAbsent(channel, new ConcurrentHashMap<String,String>());
        this.userToChannelIdMap.putIfAbsent(userName, new ConcurrentHashMap<String,String>());
        
        if(this.channelToUsersMap.get(channel).get(userName) == null){
            //we assume that the subscription id is generated uniquely by the client
            this.channelToUsersMap.get(channel).put(userName, subscriptionId); 
            this.userToChannelIdMap.get(userName).put(subscriptionId, channel);   
            result = true;
        }
        return result;
    }

    public String getSubscriptionId (String userName, String channel){
        String result = "";
        if(this.userToChannelIdMap.get(userName)!=null){
            Map<String,String> userMap = this.userToChannelIdMap.get(userName);
            for (String key : userMap.keySet()) {
                if (userMap.get(key).equals(channel)) {
                    result = key;
                }
            }
        }
        return result;
    }

    @Override
    public void send(int connectionId, T msg) {
        this.connectionIdToHandlerMap.get(connectionId + "").send(msg);
    }

    @Override
    public boolean send(String channel,String userName, T msg) {
        boolean result = false;
        if (this.channelToUsersMap.get(channel) != null) {
            if(this.channelToUsersMap.get(channel).get(userName) != null){
                //send messages to allusers in userMap
                Map<String,String> userMap = this.channelToUsersMap.get(channel);
                for (String user : userMap.keySet()){
                    if(!user.equals(userName)){
                        int connectionId = getConnectionIdForUser(user);
                        if(connectionId != -1){
                            T newMsg = StompMessagingProtocolImpl.generateMsg(this.getSubscriptionId(user, channel), this.generateMessageId(), channel, msg);
                            send(connectionId, newMsg);
                        }
                    }
                }
                result = true;
            }
        } 
        return result;
    }

    public int getConnectionIdForUser (String userName){
        int id = -1;
        for (Map.Entry<String, String> entry : this.connectionIdToUserMap.entrySet()) {
            if (entry.getValue() == userName) {
                id = Integer.parseInt(entry.getKey());
            }
        }
        return id;
    }

    public void disconnectUser(int connectionId) {
        String userName = this.connectionIdToUserMap.remove(connectionId + "");
        if (userName!=null){
            for (String channel : this.channelToUsersMap.keySet()) {
                this.unsubscribeChannelByName(channel, userName);
            }
            this.userToChannelIdMap.remove(userName);
        }
    }
    
    public boolean unsubscribeChannelById(String subscriptionId, String userName) {
        boolean result = false;
        if(this.userToChannelIdMap.get(userName)!=null){
            if (this.userToChannelIdMap.get(userName).get(subscriptionId) != null) {
                result = this.unsubscribeChannelByName(this.userToChannelIdMap.get(userName).get(subscriptionId), userName);
                if(result){
                    this.userToChannelIdMap.get(userName).remove(subscriptionId);
                }
            }
        }
        return result;
    }

    //this method only removes user from channelToUsersMap and not fron userToChannelIdMap
    public boolean unsubscribeChannelByName(String channel, String userName) {
        boolean result = false;
        Map<String, String> userMap = this.channelToUsersMap.get(channel);
        if (userMap != null) {
            if(userMap.get(userName) != null){
                // Remove the user from the channel
                userMap.remove(userName);
                result = true;
                // If the channel is now empty, remove the channel
                if (userMap.isEmpty()) {
                    this.channelToUsersMap.remove(channel);
                }
            }
        }
        return result;
    }

    @Override
    public void disconnectClient(int connectionId) {
        //removes client from registration
        this.connectionIdToHandlerMap.remove(connectionId + "");
        this.connectionIdToUserMap.remove(connectionId + "");

    }
    
}
