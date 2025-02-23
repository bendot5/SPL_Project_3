CFLAGS:=-c -Wall -Weffc++ -g -std=c++11 -Iinclude
LDFLAGS:=-lboost_system -lpthread
# TODO change the makefile for StompEMIlient
all: .PHONY StompEMIClient

StompEMIClient: bin/event.o bin/ConnectionHandler.o bin/KeyboardHandler.o bin/StompClient.o
	g++ -o bin/StompEMIClient bin/*.o $(LDFLAGS)

bin/event.o: src/event.cpp
	g++ $(CFLAGS) -o bin/event.o src/event.cpp

bin/ConnectionHandler.o: src/ConnectionHandler.cpp
	g++ $(CFLAGS) -o bin/ConnectionHandler.o src/ConnectionHandler.cpp

bin/KeyboardHandler.o: src/KeyboardHandler.cpp
	g++ $(CFLAGS) -o bin/KeyboardHandler.o src/KeyboardHandler.cpp

bin/StompClient.o: src/StompClient.cpp
	g++ $(CFLAGS) -o bin/StompClient.o src/StompClient.cpp

.PHONY: clean
clean:
	rm -f bin/*
