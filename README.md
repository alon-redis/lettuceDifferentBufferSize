# lettuceDifferentBufferSize
use Lettuce redis client to create slow connections with different buffer sizes

# Redis Buffer Size Test

A Java tool to test Redis server behavior with different buffer sizes, slow clients, and TLS support.

## Features
- Multiple concurrent Redis connections
- Configurable key sizes with linear growth
- Slow client simulation
- TLS support with insecure certificate handling
- Database flush control

## Quick Start

```bash
# Build the project
mvn clean package

# Run without TLS
java -jar target/redis-lettuce-client-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 6379 3 1 2 5 false false

# Run with TLS
java -jar target/redis-lettuce-client-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 6379 3 1 2 5 false true
```

## Parameters

```
java -jar <jar-file> <redis-host> <redis-port> <num-connections> <initial-size> <delta> <sleep-time> <noflush> <use-tls>
```

Example explained:
- `localhost`: Redis server address
- `6379`: Redis port
- `3`: Create 3 connections
- `1`: First key size 1MB
- `2`: Increase each next key by 2MB
- `5`: Sleep 5 seconds while fetching
- `false`: Flush DB before starting
- `true/false`: Enable/disable TLS

This will create:
- Key 1: 1MB
- Key 2: 3MB (1+2)
- Key 3: 5MB (1+2+2)

## TLS Configuration
- Uses insecure TLS (accepts all certificates)
- No client certificate required
- No certificate validation
- Suitable for testing with self-signed certificates

## Notes
- For production use, implement proper certificate validation
- TLS support works with both population and fetch stages
- The fetch stage uses raw TLS sockets


# Installation Guide for Redis Lettuce Client on Ubuntu 20.04

## Prerequisites

1. Update system packages:
```bash
sudo apt update
sudo apt upgrade -y
```

2. Install OpenJDK 11:
```bash
sudo apt install openjdk-11-jdk -y
```

3. Verify Java installation:
```bash
java -version
```

4. Install Redis server:
```bash
sudo apt install redis-server -y
```

5. Verify Redis installation:
```bash
redis-cli ping
```
You should receive "PONG" as a response.

## Project Setup

1. Create a new directory for your project:
```bash
mkdir redis-lettuce-client
cd redis-lettuce-client
```

2. Create a new Maven project by creating a `pom.xml` file:
```bash
touch pom.xml
```

3. Copy the github content of `pom.xml` to the local file:

4. Install Maven:
```bash
sudo apt install maven -y
```

5. Create the source directory and Java file:
```bash
mkdir -p src/main/java
```

6. Copy the provided Java code into `src/main/java/differentBufferSize.java`

## Building and Running

1. Build the project:
```bash
mvn clean package
```

2. Run the application (example with parameters):
```bash
java -jar target/redis-lettuce-client-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 6379 5 1 1 10 false
```

Parameters in order:
- Redis host (e.g., localhost)
- Redis port (default: 6379)
- Number of connections
- Initial key size (in MB)
- Delta (size increment in MB)
- Sleep time (in seconds)
- noflush flag (true/false)

## Troubleshooting

1. If Redis server isn't running:
```bash
sudo systemctl start redis-server
```

2. Check Redis server status:
```bash
sudo systemctl status redis-server
```

3. To modify Redis configuration:
```bash
sudo nano /etc/redis/redis.conf
```

4. Common Redis configuration changes:
```bash
# Increase max memory
maxmemory 2gb

# Change client output buffer limits
client-output-buffer-limit normal 0 0 0
```

5. After configuration changes, restart Redis:
```bash
sudo systemctl restart redis-server
```

## Memory Requirements

Ensure your system has enough memory to handle the data you're planning to store. The total memory required will be:
```
Total Memory = (Initial Key Size + (Number of Connections - 1) * Delta) * Number of Connections MB
```

For example, with 5 connections, 1MB initial size, and 1MB delta:
```
Total Memory = (1 + (5-1) * 1) * 5 = 25 MB
```
