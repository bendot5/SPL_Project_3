#include <iostream>
#include <sstream>
#include <string>
#include <regex>
#include "../include/ConnectionHandler.h"
#include "../include/event.h"
#include "../include/KeyboardHandler.h"
#include <thread>
#include <mutex>
#include <vector>
#include <future>
#include <sys/select.h>
#include <unistd.h>

std::mutex mtxStart;

std::condition_variable cvStart;
bool connectionReady = false;
std::atomic<bool> running(true);  // Atomic flag to gracefully exit threads

bool inputAvailable() {
    fd_set set;
    struct timeval timeout;
    FD_ZERO(&set);
    FD_SET(STDIN_FILENO, &set);
    timeout.tv_sec = 0;
    timeout.tv_usec = 100000;  // 100 milliseconds

    return select(STDIN_FILENO + 1, &set, NULL, NULL, &timeout) > 0;
}

void keyboardHandlerFunction(KeyboardHandler &keyboardHandler, std::vector<std::string> &line, ConnectionHandler* connectionHandler) {
    std::string userCommand;
	while (running.load()) {
		
		// if (std::cin.peek() != EOF) {  
        //     std::getline(std::cin, userCommand); // Read user input
		// }
		if (inputAvailable()) {  
            std::getline(std::cin, userCommand); // Read user input
        }

		if((!userCommand.empty()) && (line.empty())){
			{
				line = keyboardHandler.commandToFrame(userCommand);
				userCommand.clear(); // Clear after processing
				while (!line.empty()) {
					std::string element = line.back();
					line.pop_back();
					if ((connectionHandler->hasHostPort()) && (!connectionHandler->sendLine(element))) { // Use pointer to call `sendLine`
						std::cout << "Disconnected. Exiting...\n" << std::endl;
						running.store(false);
						connectionHandler->close();
						cvStart.notify_all();  // Notify waiting thread
						return; 
					}
				}
			}
		}
		if ((!connectionReady) && (keyboardHandler.getConnectionReady())) {
			{
				std::lock_guard<std::mutex> lock(mtxStart);
				connectionReady = true;
			}
			cvStart.notify_one();  // Notify the connection handler thread
		}
	
    }
}

void connectionHandlerFunction(KeyboardHandler &keyboardHandler, ConnectionHandler* connectionHandler, std::string &answer) {
    // Wait until connection is ready
	while (running.load()) {
		if (!connectionReady){
			{
				std::unique_lock<std::mutex> lock(mtxStart);
				cvStart.wait(lock, []{ return connectionReady; });  // Avoids spurious wakeups
			}
		}
        // Process received lines
        if (answer.empty()) {
			if ((connectionHandler->hasHostPort()) && (!connectionHandler->getLine(answer))) { // Use pointer to call `getLine`
				std::cout << "Disconnected. Exiting...\n" << std::endl;
				running.store(false);
                connectionHandler->close();
                cvStart.notify_all();  // Notify waiting thread
                return; 
			}
			if(!answer.empty()){
				std::string uiResponse = keyboardHandler.translateAnswerToResponse(answer);
				if(!uiResponse.empty()){
					std::cout << uiResponse << std::endl;
					uiResponse.clear();
				}
				answer.clear(); // Clear after processing
			}
        }
		if ((connectionReady) && (!keyboardHandler.getConnectionReady())){
			connectionReady = false;
		}
    }
}

int main(int argc, char *argv[]) {
	std::cout << "Hello World" << std::endl;
	std::string answer;
	std::vector<std::string> line;

    ConnectionHandler *connectionHandlerPtr = new ConnectionHandler();
	
	KeyboardHandler keyboardHandler(connectionHandlerPtr);

	// Create threads for keyboard and connection handling
	std::thread th1(keyboardHandlerFunction, std::ref(keyboardHandler), std::ref(line), connectionHandlerPtr);
	std::thread th2(connectionHandlerFunction,std::ref(keyboardHandler), connectionHandlerPtr, std::ref(answer));
	
	th1.join();
	th2.join();

	connectionHandlerPtr->close();  // Close the socket only after threads are done

	delete connectionHandlerPtr;
 
	return 0;
}
