---

# Project 3: Client-Server Communication (Java & C++)

## Overview

This project implements a real-time messaging system using the STOMP protocol. The server is written in Java, supporting both **Thread-Per-Client** and **Reactor** patterns, while the client is developed in C++.

## Key Features

- **Client-Server Communication**: Built a scalable Java server and a responsive C++ client.
- **STOMP Protocol**: Ensured efficient message delivery between users.
- **Multi-Threading**: Supported multiple clients with concurrent message processing.
- **Subscription System**: Allowed users to subscribe to channels, report events, and receive updates.

## Technologies Used

- Java (Server) & C++ (Client)
- STOMP Protocol
- Multithreading & Reactor Pattern
- Socket Programming

## Installation & Usage

```sh
# Reopen in container

# Run the Java Server
# Go to server dir
mvn compile
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.stomp.StompServer" -Dexec.args="7777 tpc"

# Build and Run the C++ Client
# Go to client dir
make
./bin/StompEMIClient
```
## For further instructions about testing the functionality of the code : advice me 052-7323229 / bendotan52@gmail.com



