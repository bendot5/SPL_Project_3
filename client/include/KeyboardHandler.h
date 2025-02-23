#pragma once

#include "../include/event.h"
#include "../include/ConnectionHandler.h"
#include <string>

class KeyboardHandler {
private:

	ConnectionHandler* connectionHandler_;
    bool connectionReady_;
    size_t subscribeIdCounter_;
    std::string currentUser_;
    std::vector<std::string> receipts;
    std::map<std::string, std::string> channelSubscribeIdMap_;
    std::map<std::pair<std::string,std::string>,std::vector<Event>> userChannelReports_;

public:
    
    KeyboardHandler(ConnectionHandler *connectionHandler);
    
    KeyboardHandler(const KeyboardHandler&) = delete;

    KeyboardHandler& operator=(const KeyboardHandler&) = delete;

    void setConnectionReady(bool connectionReady);
	
    bool getConnectionReady();
    
    size_t getNumOfActive (std::string user, std::string channel);

    size_t getNumOfForcesArrivals (std::string user, std::string channel);

    std::string epoch_to_date(int dateTime);
    std::string trim(const std::string& str);
	//get command and translate it to a frame
    std::vector<std::string> commandToFrame(std::string &command);

	//get answer frame and translate it to ui response
    std::string translateAnswerToResponse(std::string &answer);

}; //class KeyboardHandler
