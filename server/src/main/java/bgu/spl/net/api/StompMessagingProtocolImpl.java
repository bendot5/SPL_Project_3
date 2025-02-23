package bgu.spl.net.api;

import java.util.HashMap;
import java.util.Map;

import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.UserManagement;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String>{
    private int connectionId = -1;
    private Connections<String> serverConnections;
    private boolean shouldTerminate = false;
    private UserManagement userManagement;
    private String connectedUserName;
    
    public void setConnectedUserName(String userName){
        this.connectedUserName = userName;
    }

    @Override
    public void start(int connectionId, Connections<String> connections, UserManagement users) {
        this.connectionId = connectionId;
        this.serverConnections = connections;
        this.userManagement = users;
    }

    @Override
    public String process(String message) {
        //getting message when string ends in '\0' - null character
        //split frame to command, headers and body
        String[] parts = message.toString().split("\n\n",2);
        String frameBody = null;
        if (parts.length > 1){
            frameBody = parts[1];
        }
        String[] commandAndHeaders = parts[0].split("\n",2); //Output: [Command, Header1\nHeader2\nHeader3...]
        String stompCommandString = commandAndHeaders[0];
        String[] frameHeaders = null;
        if(commandAndHeaders.length > 1){
            frameHeaders = commandAndHeaders[1].split("\n");
        }
        
        String response = null;
        //need to add check if username is not empty else sends error that user needs to connect
        switch(stompCommandString){
            case "CONNECT" : 
                response = handleConnect(frameHeaders, frameBody);
                break;
            case "SEND":
                response = handleSend(frameHeaders, frameBody);
                break;
            case "SUBSCRIBE":
                response = handleSubscribe(frameHeaders, frameBody);
                break;
            case "UNSUBSCRIBE":
                response = handleUnsubscribe(frameHeaders, frameBody);
                break;
            case "DISCONNECT":
                response = handleDisconnect(frameHeaders, frameBody);
                break;
        }
        return response;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private String handleConnect(String[] headers, String body){
        //need to add if absent details of user to a map of user management that sits in server
        Map<String,String> headerMap = parseHeaders(headers);
        boolean toConnect = false;
        String response = null;
        //****** need to add check if version is 1.2, if not send an error */
        if((headerMap.get("login")!=null) && (headerMap.get("passcode")!=null)){
            toConnect = this.userManagement.validateUser(headerMap.get("login"), headerMap.get("passcode"));
        }
        else {
            System.out.println("Server failed to parse connected frame / headers login, passcode weren't provided"); //logs for debug
        }
        if (toConnect){
            boolean succeed = this.serverConnections.connectUser(this.connectionId, headerMap.get("login"));
            if (succeed){
                //send Successful Connection
                this.setConnectedUserName(headerMap.get("login"));
                String[] responseHeaders = null;
                if (headerMap.get("receipt") != null){
                    responseHeaders = new String[] { "version:1.2" , "receipt-id:" + headerMap.get("receipt")};
                } else {
                    responseHeaders = new String[] { "version:1.2" };
                }
                response = buildResponse("CONNECTED" ,responseHeaders ,null);
            } else {
                //send error message to user - already logged in / someone else is logged in the current session
                response = buildResponse("ERROR" ,new String[] { "message:User already logged in" } ,buildMessageBodyToError("CONNECT",headers,body,null));
                this.shouldTerminate = true;
                //--- if it's the client / user can be determined by a bit more complex design (2 methods to connect for connections)
            }
        } else {
            //password is wrong, try again
            response = buildResponse("ERROR" ,new String[] { "message:Wrong password" } ,buildMessageBodyToError("CONNECT",headers,body,null));
            this.shouldTerminate = true;
        }
        return response;
    }
    private String handleSubscribe(String[] headers, String body){
        Map<String,String> headerMap = parseHeaders(headers);
        boolean toSubscribe = false;
        String response = null;
        if((headerMap.get("destination")!=null) && (headerMap.get("id")!=null)){
            toSubscribe = this.serverConnections.subscribeChannel(headerMap.get("destination").replace("/", " ").trim(), headerMap.get("id"), this.connectedUserName);
            if(toSubscribe){
                //subscription was successful
                String[] responseHeaders = null;
                if (headerMap.get("receipt") != null){
                    responseHeaders = new String[] { "message:user "+ this.connectedUserName + " was successfully subscribed to channel" , "receipt-id:" + headerMap.get("receipt")};
                } else {
                    responseHeaders = new String[] { "message:user "+ this.connectedUserName + " was successfully subscribed to channel" };
                }
                response = buildResponse("RECEIPT" ,responseHeaders ,null);
            } else {
                //subscription failed
                response = buildResponse("ERROR" ,new String[] { "message:user is already subscribed to this channel" } ,buildMessageBodyToError("SUBSCRIBE",headers,body,null));
                this.serverConnections.disconnectUser(this.connectionId);
                this.serverConnections.disconnectClient(this.connectionId);
                this.shouldTerminate = true;
            }
        }
        else {
            System.out.println("Server failed to parse connected frame / headers destination, id weren't provided"); //logs for debug
        }
        return response;
    }
    private String handleUnsubscribe(String[] headers, String body){
        Map<String,String> headerMap = parseHeaders(headers);
        boolean toUnsubscribe =false;
        String response = null;
        if(headerMap.get("id")!=null){
            toUnsubscribe = this.serverConnections.unsubscribeChannelById(headerMap.get("id"), this.connectedUserName);
            if (toUnsubscribe) {
                //successful unsubscription
                String[] responseHeaders = null;
                if (headerMap.get("receipt") != null){
                    responseHeaders = new String[] { "message:user "+ this.connectedUserName + " was succefully unsubscribed from channel" , "receipt-id:" + headerMap.get("receipt")};
                } else {
                    responseHeaders = new String[] { "message:user "+ this.connectedUserName + " was succefully unsubscribed from channel" };
                }
                response = buildResponse("RECEIPT" ,responseHeaders ,null);
            } else {
                //failed to unsubscribe
                response = buildResponse("ERROR" ,new String[] { "message:user cannot unsubscribe from a channel he's not subscribed to" } ,buildMessageBodyToError("UNSUBSCRIBE",headers,body,null));
                this.serverConnections.disconnectUser(this.connectionId);
                this.serverConnections.disconnectClient(this.connectionId);
                this.shouldTerminate = true;
            }
        } else {
            System.out.println("Server failed to parse connected frame / header id wasn't provided"); //logs for debug
        }
        return response;
    }
    private String handleDisconnect(String[] headers, String body){
        Map<String,String> headerMap = parseHeaders(headers);
        String response = null;
        if (headerMap.get("receipt")!=null) {
            //Graceful Shutdown
            this.serverConnections.disconnectUser(this.connectionId);
            this.serverConnections.disconnectClient(this.connectionId);
            String[] responseHeaders = new String[] { "message:user "+ this.connectedUserName + " was successfully disconnected" , "receipt-id:" + headerMap.get("receipt")};
            response = buildResponse("RECEIPT" ,responseHeaders ,null);
            this.setConnectedUserName(null);
            this.shouldTerminate = true;
        } else {
            System.out.println("Server failed to parse connected frame / header receipt wasn't provided (required when disconnecting)"); //logs for debug
        }
        return response;
    }
    private String handleSend(String[] headers, String body){
        Map<String,String> headerMap = parseHeaders(headers);
        boolean toSend = false;
        String response = null;
        if(headerMap.get("destination")!=null){
            toSend = this.serverConnections.send(headerMap.get("destination").replace("/", " ").trim(), this.connectedUserName, body);
            if (toSend) {
                //successful send
                String[] responseHeaders = null;
                if (headerMap.get("receipt") != null){
                    responseHeaders = new String[] { "message:message was send successfully to channel " + headerMap.get("destination") , "receipt-id:" + headerMap.get("receipt")};
                } else {
                    responseHeaders = new String[] { "message:message was send successfully to channel " + headerMap.get("destination") };
                }
                response = buildResponse("RECEIPT" ,responseHeaders ,null);
            } else {
                //failed to send
                response = buildResponse("ERROR" ,new String[] { "message:user is not subscribed to channel" } ,buildMessageBodyToError("SEND",headers,body,null));
                this.serverConnections.disconnectUser(this.connectionId);
                this.serverConnections.disconnectClient(this.connectionId);
                this.shouldTerminate = true;
            }
        } else {
            System.out.println("Server failed to parse connected frame / header destination wasn't provided"); //logs for debug
        }
        return response;
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T generateMsg(String subscriptionId, int messageId, String channel, T body){
        String[] subscriptionHeaders = new String[] { "subscription:" + subscriptionId,"message-id:" + messageId ,"destination:" + channel };
        String subscriptionMessage = StompMessagingProtocolImpl.buildResponse("MESSAGE" ,subscriptionHeaders , (String)body);
        return (T)subscriptionMessage;
    }
    public static Map<String, String> parseHeaders(String[] headers) {
        Map<String, String> headerMap = new HashMap<>();
        for (String header : headers) {
            String[] parts = header.split(":", 2); // Split on the first colon
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                headerMap.put(key, value);
            }
        }
        return headerMap;
    }
    public static String buildMessageBodyToError(String originalCommand, String[] originalHeaders, String originalBody, String extraNewBody) {
        StringBuilder response = new StringBuilder();
        //Start with line to seperate
        response.append("-----\n");
        // Append with the original command
        response.append(originalCommand);
        response.append("\n");

        // Append headers
        if (originalHeaders != null) {
            for (String header : originalHeaders) {
                response.append(header).append("\n");
            }
        }
    
        // Add a blank line to separate headers from the body
        response.append("\n");
    
        // Append the body
        if (originalBody != null) {
            response.append(originalBody).append("\n");
        }
    
        //End with line to seperate
        response.append("-----");

        if (extraNewBody != null) {
            response.append("\n").append(extraNewBody);
        }

        return response.toString();
    }

    public static String buildResponse(String command, String[] headers, String body) {
        StringBuilder response = new StringBuilder();
        
        // Start with the command
        
        response.append(command);
        response.append("\n");

        // Append headers
        if (headers != null) {
            for (String header : headers) {
                response.append(header).append("\n");
            }
        }
    
        // Add a blank line to separate headers from the body
        response.append("\n");
    
        // Append the body
        if (body != null) {
            // response.append("The message:\n").append(body).append("\n");
            response.append(body).append("\n");

        }
        return response.toString();
    }
    
}
