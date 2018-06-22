package de.sg_o.app.miioMapServer;

import de.sg_o.app.miio.base.Token;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.*;

public class Server extends Thread{
    private final static Logger LOGGER = Logger.getLogger(Server.class.getName());

    private Maps mapHandler;
    private final int port;
    private boolean running;
    private Token tk;

    /**
     * Create a new server.
     * @param activeMapDirectory The directory where the active maps are stored.
     * @param previousMapsDirectory The directory where the directories of old maps can be found.
     * @param port The port to start the server at.
     * @param tokenFile The token of the device.
     * @param logLevel The log level.
     * @param logFile The file where to store the logs. If null the logs will be output to the console.
     * @throws IOException If the directories are invalid, If the log file is invalid or if the token is invalid.
     */
    public Server(File activeMapDirectory, File previousMapsDirectory, int port, File tokenFile, Level logLevel, File logFile) throws IOException {
        if (logFile != null) {
            Logger globalLogger =  LOGGER.getParent();
            Handler[] handlers = globalLogger.getHandlers();
            for(Handler handler : handlers) {
                globalLogger.removeHandler(handler);
            }
            FileHandler fh = new FileHandler(logFile.getPath());
            globalLogger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        }
        if (logLevel != null) {
            LOGGER.setLevel(logLevel);
        }
        LOGGER.info("Server Creation");
        this.port = port;
        LOGGER.info("Creating map handler");
        this.mapHandler = new Maps(activeMapDirectory, previousMapsDirectory, LOGGER.getLevel());
        LOGGER.info("Created map handler");
        LOGGER.info("Getting token");
        Token tk = getToken(tokenFile);
        if (tk == null) {
            LOGGER.warning("Couldn't get token");
            throw new IOException("No token extracted");
        }
        LOGGER.info("Token loaded");
        this.tk = tk;
    }

    private Token getToken(File tokenFile) throws IOException {
        if (tokenFile == null) {
            LOGGER.warning("Token file not set");
            return null;
        }
        if (!tokenFile.exists()) {
            LOGGER.warning("Token file not existing");
            return null;
        }
        LOGGER.info("Creating buffered reader");
        BufferedReader bufferedReader = new BufferedReader(new FileReader(tokenFile));
        LOGGER.info("Reading line");
        String tkString = bufferedReader.readLine();
        if (tkString == null) {
            LOGGER.warning("Token string not found");
            return null;
        }
        LOGGER.info("Decoding token");
        byte[] decodedBytes = tkString.getBytes("ASCII");
        if (decodedBytes.length != 16){
            LOGGER.warning("Token length not of the correct length: " + decodedBytes.length);
        }
        LOGGER.info("Generating token");
        return new Token(decodedBytes);
    }

    /**
     * Run the server.
     */
    @Override
    public void run() {
        LOGGER.info("Starting server");
        running = true;
        while (running){
            ServerSocket serverSocket;
            try {
                LOGGER.info("Creating server socket");
                serverSocket = new ServerSocket(port);
                LOGGER.info("Created server socket" + serverSocket.toString());
            } catch (Exception e) {
                LOGGER.warning("Couldn't create socket: " + e.toString());
                return;
            }
            while (running) {
                LOGGER.fine("Run loop start");
                try {
                    Socket socket = serverSocket.accept();
                    if (socket == null){
                        LOGGER.warning("Connection null");
                        continue;
                    }
                    LOGGER.info("Connection established");
                    new Thread(new ServerThread(socket, mapHandler, tk, LOGGER.getLevel())).start();
                } catch (IOException ignore) {
                }

            }
        }
    }

    /**
     * Stop the server.
     */
    public void terminate() {
        LOGGER.info("Terminating server");
        running = false;
    }
}
