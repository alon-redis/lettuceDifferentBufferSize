import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.support.ConnectionPoolSupport;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import javax.net.ssl.*;
import java.net.Socket;
import java.io.OutputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class differentBufferSize {

    private static final int MB_TO_BYTES = 1048576;

    // Create insecure SSL context that accepts all certificates
    private static SSLContext createInsecureSSLContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext;
    }

    private static RedisURI createRedisURI(String host, int port, boolean useTLS) {
        RedisURI.Builder builder = RedisURI.builder()
            .withHost(host)
            .withPort(port);
        
        if (useTLS) {
            builder.withSsl(true)
                   .withVerifyPeer(false);  // Disable certificate verification
        }
        
        return builder.build();
    }

    public static void populateData(String redisHost, int redisPort, int numConnections, 
                                  int initialKeySize, int delta, boolean useTLS) {
        List<RedisClient> clients = new ArrayList<>();
        List<StatefulRedisConnection<String, String>> connections = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(numConnections);

        try {
            // Create Redis URI with TLS if enabled
            RedisURI redisURI = createRedisURI(redisHost, redisPort, useTLS);

            // Create separate clients and connections for each task
            for (int i = 0; i < numConnections; i++) {
                RedisClient client = RedisClient.create(redisURI);
                clients.add(client);
                StatefulRedisConnection<String, String> connection = client.connect();
                connections.add(connection);
            }

            // Submit tasks using individual connections
            for (int i = 0; i < numConnections; i++) {
                final int index = i;
                final StatefulRedisConnection<String, String> conn = connections.get(i);
                
                executor.submit(() -> {
                    try {
                        RedisCommands<String, String> syncCommands = conn.sync();
                        String key = "key_" + (index + 1);
                        int valueSize = (initialKeySize + index * delta) * MB_TO_BYTES;
                        
                        // Create value of specified size
                        char[] chars = new char[valueSize];
                        java.util.Arrays.fill(chars, 'x');
                        String value = new String(chars);
                        
                        // Set the value in Redis
                        syncCommands.set(key, value);
                        System.out.println("Set key: " + key + " with size: " + valueSize + " bytes");
                    } catch (Exception e) {
                        System.err.println("Error setting key_" + (index + 1) + ": " + e.getMessage());
                    }
                });
            }

            executor.shutdown();
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

        } catch (Exception e) {
            System.err.println("Error in population stage: " + e.getMessage());
        } finally {
            // Close all connections and clients
            for (StatefulRedisConnection<String, String> connection : connections) {
                try {
                    connection.close();
                } catch (Exception e) {
                    System.err.println("Error closing connection: " + e.getMessage());
                }
            }
            for (RedisClient client : clients) {
                try {
                    client.shutdown();
                } catch (Exception e) {
                    System.err.println("Error shutting down client: " + e.getMessage());
                }
            }
            System.out.println("All connections closed after populating data.");
        }
    }

    public static void fetchDataSlowly(String redisHost, int redisPort, int numConnections, 
                                     int sleepTime, boolean useTLS) {
        ExecutorService executor = Executors.newFixedThreadPool(numConnections);

        try {
            SSLContext sslContext = useTLS ? createInsecureSSLContext() : null;
            SSLSocketFactory sslSocketFactory = useTLS ? sslContext.getSocketFactory() : null;

            for (int i = 1; i <= numConnections; i++) {
                final int index = i;
                executor.submit(() -> {
                    Socket socket = null;
                    try {
                        // Create either SSL or regular socket
                        if (useTLS) {
                            socket = sslSocketFactory.createSocket(redisHost, redisPort);
                        } else {
                            socket = new Socket(redisHost, redisPort);
                        }

                        OutputStream outputStream = socket.getOutputStream();
                        // Properly format RESP protocol command
                        String command = "*2\r\n$3\r\nGET\r\n$" + ("key_" + index).length() + 
                                      "\r\nkey_" + index + "\r\n";
                        outputStream.write(command.getBytes());
                        outputStream.flush();

                        // Simulate slow response reading
                        for (int j = 0; j < sleepTime * 10; j++) {
                            Thread.sleep(100);
                            System.out.println("Sleeping for key_" + index);
                        }

                        System.out.println("Sent GET command for: key_" + index + 
                                         " but reading response very slowly or not at all.");
                    } catch (Exception e) {
                        System.err.println("Error with connection " + index + ": " + e.getMessage());
                    } finally {
                        if (socket != null) {
                            try {
                                socket.close();
                            } catch (Exception e) {
                                System.err.println("Error closing socket: " + e.getMessage());
                            }
                        }
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error creating SSL context: " + e.getMessage());
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    public static void main(String[] args) {
        if (args.length < 8) {
            System.out.println("Usage: java differentBufferSize <redis_host> <redis_port> " +
                             "<num_connections> <initial_key_size_mb> <delta_mb> " +
                             "<sleep_time_seconds> <noflush> <use_tls>");
            System.exit(1);
        }

        String redisHost = args[0];
        int redisPort = Integer.parseInt(args[1]);
        int numConnections = Integer.parseInt(args[2]);
        int initialKeySize = Integer.parseInt(args[3]);
        int delta = Integer.parseInt(args[4]);
        int sleepTime = Integer.parseInt(args[5]);
        boolean noflush = Boolean.parseBoolean(args[6]);
        boolean useTLS = Boolean.parseBoolean(args[7]);

        if (!noflush) {
            RedisURI redisURI = createRedisURI(redisHost, redisPort, useTLS);
            RedisClient client = RedisClient.create(redisURI);
            try (StatefulRedisConnection<String, String> connection = client.connect()) {
                RedisCommands<String, String> syncCommands = connection.sync();
                syncCommands.flushall();
                System.out.println("Flushed all Redis databases.");
            } finally {
                client.shutdown();
            }
        }

        System.out.println("Starting population stage...");
        populateData(redisHost, redisPort, numConnections, initialKeySize, delta, useTLS);

        System.out.println("Starting fetch stage...");
        fetchDataSlowly(redisHost, redisPort, numConnections, sleepTime, useTLS);
    }
}
