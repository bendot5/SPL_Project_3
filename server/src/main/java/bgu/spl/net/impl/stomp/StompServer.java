package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.StompMessagingProtocolImpl;
import bgu.spl.net.impl.echo.LineMessageEncoderDecoder;
import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        // TODO: implement this
        //gets 3 arguments TPC/Reactor, port, encdec
        // System.out.println("Hello World");
        if (args.length >=2){
            int serverPort = Integer.parseInt(args[0]);
            String serverType = args[1];
            if(serverType.equalsIgnoreCase("TPC")){
                Server.threadPerClient(
                    serverPort, //port
                    () -> new StompMessagingProtocolImpl(), //protocol factory
                    LineMessageEncoderDecoder::new //message encoder decoder factory
                ).serve();
            } else if(serverType.equalsIgnoreCase("reactor")){
                Server.reactor(
                    3, // number of threads, didn't mention how many they want...
                    serverPort, //port
                    () -> new StompMessagingProtocolImpl(), //protocol factory
                    LineMessageEncoderDecoder::new //message encoder decoder factory
                ).serve();
            }
        }
    }
}
