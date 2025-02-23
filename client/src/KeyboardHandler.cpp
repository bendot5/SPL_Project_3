#include "../include/event.h"
#include "../include/KeyboardHandler.h"

#include <fstream>
#include <string>

KeyboardHandler::KeyboardHandler(ConnectionHandler* connectionHandler) : 
    connectionHandler_(connectionHandler),
    // connectionHandler_(connectionHandler ? std::make_unique<ConnectionHandler>(*connectionHandler) : nullptr),
    connectionReady_(false), 
    subscribeIdCounter_(0), 
    currentUser_(""),
    receipts(),
    channelSubscribeIdMap_(),
    userChannelReports_(){}

bool KeyboardHandler::getConnectionReady(){return connectionReady_;}

void KeyboardHandler::setConnectionReady(bool connectionReady){connectionReady_ = connectionReady;}

std::string KeyboardHandler::epoch_to_date(int dateTime){
 time_t time = dateTime;
 struct tm* timeinfo = localtime(&time);
 char buffer[20];
 strftime(buffer, sizeof(buffer), "%d/%m/%y %H:%M" , timeinfo);
 return std::string(buffer);
}

size_t KeyboardHandler::getNumOfActive (std::string user, std::string channel){
    size_t counter = 0;
    std::pair<std::string,std::string> currentPair = std::make_pair(user,channel);
    auto iterator = this->userChannelReports_.find(currentPair);
    if ((iterator != this->userChannelReports_.end()) && (!this->userChannelReports_[currentPair].empty())){
        for (const auto& e : iterator->second){
            std::map<std::string,std::string> generalInformation = e.get_general_information();
            for (const auto& entry : generalInformation) {
                const std::string& key = entry.first;
                const std::string& value = entry.second;
                if ((trim(key).find("active") != std::string::npos) && (trim(value).find("true") != std::string::npos)){
                    counter++;
                }
            }
        }
    }
    return counter;
}

size_t KeyboardHandler::getNumOfForcesArrivals (std::string user, std::string channel){
    size_t counter = 0;
    std::pair<std::string,std::string> currentPair = std::make_pair(user,channel);
    auto iterator = this->userChannelReports_.find(currentPair);
    if ((iterator != this->userChannelReports_.end()) && (!this->userChannelReports_[currentPair].empty())){
        for (const auto& e : iterator->second){
            std::map<std::string,std::string> generalInformation = e.get_general_information();
            for (const auto& entry : generalInformation) {
                const std::string& key = entry.first;
                const std::string& value = entry.second;
                if ((trim(key).find("forces_arrival_at_scene") != std::string::npos) && (trim(value).find("true") != std::string::npos)){
                    counter++;
                }
            }
        }
    }
    return counter;
}

std::vector<std::string> KeyboardHandler::commandToFrame(std::string &command) {

	    std::vector<std::string> frame;
        if(!command.empty()){
            std::istringstream iss(command);
            std::vector<std::string> tokens;
            std::string token;
            while (std::getline(iss, token, ' ')){
                tokens.push_back(token);
            }
            if(!tokens.empty()){
                if (!connectionReady_){
                    //accept only login 
                    if ((tokens[0] == "login") && (tokens.size() == 4)) { //should get CONNECTED / ERROR frame back
                        size_t colonPos = tokens[1].find(':');
                        std::string host = tokens[1].substr(0,colonPos);
                        short port = static_cast<short>(std::stoi(tokens[1].substr(colonPos + 1)));
                        std::string username = tokens[2];
                        std::string password = tokens[3];
                        connectionHandler_->setHostPort(host,port);
                        if (!connectionHandler_->connect()) {
                            std::cout << "Could not connect to server" << std::endl;
                        }
                        else {
                            //loop read commands and handle them
                            this->setConnectionReady(true);
                            this->currentUser_ = username;
                            //send frame to connect and prints login succeful / other in case of error
                            //please notice that incase of error the server throws the client and close the socket, so we shouldn't worry about typeing different ports
                            frame = {"CONNECT\naccept-version:1.2\nhost:stomp.cs.bgu.ac.il\nlogin:" + username + "\npasscode:" + password + "\n\n"};
                        }   
                    } 
                    else {
                            std::cout<< "Please login first" << std::endl;
                    }     
                } 
                else {
                    //accept other frames
                    if ((tokens[0] == "login") && (tokens.size() == 4)) {
                        std::cout << "The client is already logged in, log out before trying again" << std::endl;
                    }
                    else if((tokens[0] == "join") && (tokens.size() == 2)){ //should get a RECEIPT / ERROR frame back
                        std::string channel = trim(tokens[1]);
                        std::string receiptId = std::to_string(this->receipts.size());
                        this->receipts.push_back(command);
                        if(channelSubscribeIdMap_[channel].empty()){
                            channelSubscribeIdMap_[channel] = std::to_string(subscribeIdCounter_);
                            subscribeIdCounter_ = subscribeIdCounter_ + 1;
                        }
                        std::string id = channelSubscribeIdMap_[channel];
                        frame ={"SUBSCRIBE\ndestination:" + channel + "\nid:" + id + "\nreceipt:" + receiptId + "\n\n"};
                    }
                    else if((tokens[0] == "exit") && (tokens.size() == 2)){ //should get a RECEIPT / ERROR frame back
                        std::string channel = tokens[1];
                        std::string id = "-1";
                        if(!channelSubscribeIdMap_[channel].empty()){
                            id = channelSubscribeIdMap_[channel];
                        }
                            std::string receiptId = std::to_string(this->receipts.size());
                            this->receipts.push_back(command);
                            frame ={"UNSUBSCRIBE\nid:" + id + "\nreceipt:" + receiptId + "\n\n"};
                    }           
                    else if((tokens[0] == "report") && (tokens.size() == 2)){ //should get a RECEIPT / ERROR frame back
                        std::string jsonPath = tokens[1];
                        names_and_events ne = parseEventsFile(jsonPath);
                        std::string id = "-1";
                        if(!channelSubscribeIdMap_[trim(ne.channel_name)].empty()){
                            id = channelSubscribeIdMap_[trim(ne.channel_name)];
                        }
                        if(id!="-1"){
                            for (Event e : ne.events){
                                e.setEventOwnerUser(this->currentUser_);
                                std::string generalInformation ="";
                                for (const auto& pair : e.get_general_information()){
                                    generalInformation = generalInformation + "\t" + pair.first + " : " + pair.second + "\n"; 
                                }
                                std::string eventFrame = "SEND\ndestination: " + trim(ne.channel_name) + "\n\nuser:" + e.getEventOwnerUser() + "\ncity:" + e.get_city() + "\nevent name:" + e.get_name() + "\ndate time:" + std::to_string(e.get_date_time()) + "\ngeneral information:\n" + generalInformation + "description:\n" + e.get_description() +"\n";
                                frame.push_back(eventFrame);
                                this->userChannelReports_[std::make_pair(e.getEventOwnerUser(),ne.channel_name)].push_back(e) ; 
                            }
                        }
                        else {
                            std::cout << "user cannot report on a channel he's not subscribed to\n"; 
                        }
                    } 
                    else if((tokens[0] == "summary") && (tokens.size() == 4)){ //should get a RECEIPT / ERROR frame back
                        std::string id = "-1";
                        std::string channel = tokens[1];
                        std::string user = tokens[2];
                        std::string filePath = tokens[3];
                        if(!channelSubscribeIdMap_[channel].empty()){
                            id = channelSubscribeIdMap_[channel];
                        }
                        if(id!="-1"){
                            if(!channelSubscribeIdMap_[channel].empty()){
                                std::pair<std::string,std::string> currentPair = std::make_pair(user,channel);
                                if ((this->userChannelReports_.find(currentPair) != this->userChannelReports_.end()) && (!this->userChannelReports_[currentPair].empty())){
                                    std::sort(this->userChannelReports_[currentPair].begin(), this->userChannelReports_[currentPair].end(),
                                    [](const Event& a, const Event& b){
                                        if(a.get_date_time() == b.get_date_time()) {
                                            return a.get_name() < b.get_name();
                                        }
                                        return a.get_date_time() < b.get_date_time();
                                    });
                                    auto iterator = this->userChannelReports_.find(currentPair);
                                    if(iterator != this->userChannelReports_.end()) {
                                        size_t activeSize = this->getNumOfActive(user, channel);
                                        size_t forcesArrivalsSize = this->getNumOfForcesArrivals(user, channel);
                                        size_t reportCounter= 1;
                                        std::string writeToFile = "Channel " + channel + "\nStats :\n Total : " + std::to_string(iterator->second.size()) + "\nactive : " + std::to_string(activeSize) + "\n forces arrival at scene : " + std::to_string(forcesArrivalsSize) + "\n\nEvent Reports :\n\n";
                                        for (const auto& e : iterator->second){
                                            std::string report = "Report_" + std::to_string(reportCounter) + " :\n\tcity : " + e.get_city() + "\n\tdate time : " + epoch_to_date(e.get_date_time()) + "\n\tevent name : " + e.get_name() + "\n\tsummary : " + e.get_description().substr(0,27);
                                            if(e.get_description().size() > 27) {
                                                report = report + "...";
                                            }
                                            report = report + "\n\n";
                                            reportCounter++;
                                            writeToFile = writeToFile + report;
                                        }
                                        {
                                            std::ofstream file(filePath);
                                            file << writeToFile;
                                        }
                                    }
                                }
                            }
                        }
                        else{
                            std::cout << "user cannot get reports from a channel he's not subscribed to\n"; 
                        }
                    }
                    else if((tokens[0] == "logout") && (tokens.size() == 1)){ //should get a RECEIPT / ERROR frame back
                        std::string receiptId = std::to_string(this->receipts.size());
                        this->receipts.push_back(command);
                        frame ={"DISCONNECT\nreceipt:" + receiptId + "\n\n"};
                    }
                }
            }
        }
		
	return frame;
}

std::string KeyboardHandler::translateAnswerToResponse(std::string &answer) {
	std::string response;
    if(!answer.empty()){
        std::string headers = answer.substr(0,answer.find("\n\n"));
        std::string body = answer.substr(answer.find("\n\n") + 2);
        if(!headers.empty()){
            std::istringstream isstr(headers);
            std::vector<std::string> tokens;
            std::string token;
            while (std::getline(isstr, token, '\n')){
                tokens.push_back(token);
            }
            if(tokens[0] == "CONNECTED"){ 
                response = "Login successful";
            }
            else if(tokens[0] == "MESSAGE"){
                std::string dest = "";
                std::string destHeader ="destination:";//might be without space after 
                for (std::string tok: tokens){

                    if (tok.find(destHeader) != std::string::npos)
                    {
                        dest = tok;
                    } 
                }
                size_t destPos = dest.find(destHeader) + destHeader.length();
                std::string finalDest = trim(dest.substr(destPos));
                Event newEvent = Event(body); 
                this->userChannelReports_[std::make_pair(newEvent.getEventOwnerUser(),finalDest)].push_back(newEvent);  
            }
            else if(tokens[0] == "RECEIPT"){
                std::string receipt = "";
                std::string receiptHeader = "receipt-id:";
                for (std::string tok: tokens){
                    if (tok.find(receiptHeader) != std::string::npos)
                    {
                        receipt = tok;
                    } 
                }
                if(!receipt.empty()){
                    size_t receiptIdpos = receipt.find(receiptHeader) + receiptHeader.length();
                    std::string receiptId = receipt.substr(receiptIdpos);
                    std::istringstream isst(this->receipts[std::stoi(receiptId)]);
                    std::vector<std::string> originalCommand;
                    std::string word;
                    while (std::getline(isst, word, ' ')){
                        originalCommand.push_back(word);
                    }
                    if(!originalCommand.empty()){
                        if(originalCommand[0] == "join"){
                            response = "Joined channel " + originalCommand[1];
                        }
                        else if(originalCommand[0] == "exit"){
                            if(!channelSubscribeIdMap_[originalCommand[1]].empty()){
                                channelSubscribeIdMap_.erase(originalCommand[1]);
                            }
                            for (auto it = userChannelReports_.begin(); it != userChannelReports_.end(); ) {
                                const std::pair<std::string, std::string>& key = it->first;
                                // Check your condition
                                if (key.second == originalCommand[1]) {
                                    it = userChannelReports_.erase(it);  // Erases the record and returns the next iterator
                                } else {
                                    ++it;  // Move to the next record
                                }
                            }
                            response = "Exited channel " + originalCommand[1];
                        }
                        else if(originalCommand[0] == "logout"){
                            response = "Logout successfully";
                            this->setConnectionReady(false);
                            this->connectionHandler_->close();
                            this->connectionHandler_->setHostPort("",0);
                        }
                    }
                }                
            }
            else if(tokens[0] == "ERROR"){ 
                std::string messageHeader = "message:";
                response = tokens[1].substr(messageHeader.length());
                this->connectionHandler_->close();
            }
        }
    } 
	return response;
}
std::string KeyboardHandler::trim(const std::string& str) {
    size_t first = str.find_first_not_of(" \t\n\r\f\v");
    if (first == std::string::npos) return "";
    size_t last = str.find_last_not_of(" \t\n\r\f\v");
    return str.substr(first, last - first + 1);
}

