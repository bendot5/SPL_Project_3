package bgu.spl.net.api;

import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.UserManagement;

public interface StompMessagingProtocol<T> extends MessagingProtocol<T> {
	/**
	 * Used to initiate the current client protocol with it's personal connection ID and the connections implementation
	**/
    @Override
    void start(int connectionId, Connections<T> connections, UserManagement userManagement);

    T process(T message); //originally was void
	
	/**
     * @return true if the connection should be terminated
     */
    boolean shouldTerminate();
}
