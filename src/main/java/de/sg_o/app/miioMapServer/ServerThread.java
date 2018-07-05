package de.sg_o.app.miioMapServer;

import de.sg_o.app.miio.base.Token;
import de.sg_o.app.miio.util.ByteArray;
import de.sg_o.proto.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The ServerThread class handles a single clients requests.
 */
@SuppressWarnings("WeakerAccess")
public class ServerThread extends Thread {
    private final static Logger LOGGER = Logger.getLogger(ServerThread.class.getName());

    private Socket socket;
    private Maps mapHandler;
    private Token tk;
    private boolean authenticated = false;

    /**
     * Create a new server thread.
     * @param socket The socket of the new client.
     * @param mapHandler The map handler.
     * @param tk The devices token.
     * @param logLevel The log level.
     * @throws IOException If the the socket is invalid, the token is invalid or the map handler is invalid.
     */
    public ServerThread(Socket socket, Maps mapHandler, Token tk, Level logLevel) throws IOException {
        super("MapServerThread");
        if (logLevel != null) {
            LOGGER.setLevel(logLevel);
        }
        LOGGER.info("Generating server thread");
        if (socket == null) {
            LOGGER.warning("Socket null");
            throw new IOException();
        }
        this.socket = socket;
        if (mapHandler == null) {
            LOGGER.warning("Map handler null");
            throw new IOException();
        }
        this.mapHandler = mapHandler;
        if (tk == null) {
            LOGGER.warning("Token null");
            throw new IOException("No token provided");
        }
        this.tk = tk;
    }

    /**
     * Start the server thread.
     */
    public void run() {
        LOGGER.info("Starting server thread");
        InputStream inputStream;
        OutputStream outputStream;
        try {
            LOGGER.info("Getting input stream");
            inputStream = socket.getInputStream();
            LOGGER.info("Getting output stream");
            outputStream = socket.getOutputStream();
            LOGGER.info("Got all streams");
        } catch (IOException e) {
            LOGGER.warning("Error getting streams: " + e.toString());
            forceClose();
            return;
        }
        MapRequestProto.MapRequest request;
        while (socket.isConnected()) {
            try {
                LOGGER.info("Trying to receive request");
                //noinspection StatementWithEmptyBody
                while ((request = MapRequestProto.MapRequest.parseDelimitedFrom(inputStream)) == null) {
                }
                LOGGER.info("Got request");
                sendResponse(request, outputStream);
            } catch (IOException e) {
                LOGGER.warning("Error receiving request: " + e.toString());
                forceClose();
                return;
            }
        }
    }

    private void sendResponse(MapRequestProto.MapRequest req, OutputStream output){
        if (req == null) return;
        if (output == null) return;
        LOGGER.info("Parsing Code");
        MapRequestProto.MapRequest.RequestCode code = req.getCode();
        switch (code){
            case MAP_INFO:
                LOGGER.info("MAP_INFO detected");
                sendInfo(output);
                break;
            case GET_ACTIVE_MAP:
                LOGGER.info("GET_ACTIVE_MAP detected");
                sendActiveMap(output);
                break;
            case GET_PREVIOUS_MAP:
                LOGGER.info("GET_PREVIOUS_MAP detected");
                sendPreviousMap(output);
                break;
            case GET_OLD_MAP:
                LOGGER.info("GET_OLD_MAP detected");
                sendOldMap(req.getOpt(), output);
                break;
            case GET_ACTIVE_MAP_SLAM:
                LOGGER.info("GET_ACTIVE_MAP_SLAM detected");
                sendActiveMapSlam(req.getOptInt(), output);
                break;
            case GET_PREVIOUS_MAP_SLAM:
                LOGGER.info("GET_PREVIOUS_MAP_SLAM detected");
                sendPreviousMapSlam(output);
                break;
            case GET_OLD_MAP_SLAM:
                LOGGER.info("GET_OLD_MAP_SLAM detected");
                sendOldMapSlam(req.getOpt(), output);
                break;
            case AUTHENTICATE:
                LOGGER.info("AUTHENTICATE detected");
                authenticate(req.getOpt(), output);
                break;
            case END_COMMUNICATION:
                forceClose();
                break;
        }
    }

    private void authenticate(String auth, OutputStream output) {
        if (output == null) {
            LOGGER.warning("OutputStream null");
            return;
        }
        MapRequestProto.MapRequest.Builder resp = MapRequestProto.MapRequest.newBuilder();
        if (auth == null) {
            try {
                LOGGER.warning("No encrypted auth message received");
                resp.setOpt(ByteArray.bytesToHex(tk.encrypt("error".getBytes("ASCII"))));
                resp.build().writeDelimitedTo(output);
                forceClose();
                return;
            } catch (IOException e) {
                forceClose();
            }
        }
        LOGGER.info("Decoding auth message");
        byte[] msg = tk.decrypt(ByteArray.hexToBytes(auth));
        LOGGER.info("Decoded auth message");
        authenticated = Arrays.equals(new byte[]{104, 101, 108, 108, 111}, msg);
        LOGGER.info("Compared auth message");
        resp.setCode(MapRequestProto.MapRequest.RequestCode.AUTHENTICATE);
        try {
            if (authenticated) {
                LOGGER.info("Authentication success");
                resp.setOpt(ByteArray.bytesToHex(tk.encrypt("ok".getBytes("ASCII"))));
                resp.build().writeDelimitedTo(output);
            } else {
                LOGGER.info("Authentication failed");
                resp.setOpt(ByteArray.bytesToHex(tk.encrypt("error".getBytes("ASCII"))));
                resp.build().writeDelimitedTo(output);
                forceClose();
            }
        } catch (IOException e) {
            LOGGER.warning("Couldn't send authentication response");
            forceClose();
        }
    }

    private void sendInfo(OutputStream output) {
        if (output == null) {
            LOGGER.warning("OutputStream null");
            return;
        }
        LOGGER.info("Updating active map");
        mapHandler.updateActiveMap();
        LOGGER.info("Updating previous maps");
        mapHandler.updatePreviousMaps();
        MapInfoProto.MapInfo.Builder builder = MapInfoProto.MapInfo.newBuilder();
        if (authenticated) {
            LOGGER.info("Adding information");
            builder.setActiveMapAvailable(mapHandler.hasActiveMap());
            builder.addAllOldMaps(mapHandler.getPreviousMaps());
            builder.setError(constructError(MapErrorProto.MapError.ErrorCode.NONE, ""));
        } else {
            LOGGER.warning("Not authenticated");
            builder.setActiveMapAvailable(false);
            builder.setError(constructError(MapErrorProto.MapError.ErrorCode.NOT_AUTHENTICATED, ""));
        }
        try {
            LOGGER.info("Sending info");
            builder.build().writeDelimitedTo(output);
        } catch (IOException ignore) {
            LOGGER.warning("Couldn't send information");
        }
    }

    private void sendActiveMap(OutputStream output) {
        if (output == null) {
            LOGGER.warning("OutputStream null");
            return;
        }
        LOGGER.info("Updating active map");
        mapHandler.updateActiveMap();
        LOGGER.info("Sending active map");
        sendMap(mapHandler.getActiveMap(), output, MapErrorProto.MapError.ErrorCode.MAP_NOT_AVAILABLE);
    }

    private void sendActiveMapSlam(int start, OutputStream output) {
        if (output == null) {
            LOGGER.warning("OutputStream null");
            return;
        }
        LOGGER.info("Sending active map slam");
        sendSlam(mapHandler.getActivePathFrom(start), output, MapErrorProto.MapError.ErrorCode.MAP_NOT_AVAILABLE);
    }

    private void sendPreviousMap(OutputStream output) {
        if (output == null) {
            LOGGER.warning("OutputStream null");
            return;
        }
        LOGGER.info("Updating previous map");
        mapHandler.updatePreviousMaps();
        LOGGER.info("Sending previous map");
        sendMap(mapHandler.getLastMap(), output, MapErrorProto.MapError.ErrorCode.MAP_NOT_AVAILABLE);
    }

    private void sendPreviousMapSlam(OutputStream output) {
        if (output == null) {
            LOGGER.warning("OutputStream null");
            return;
        }
        LOGGER.info("Updating previous map");
        mapHandler.updatePreviousMaps();
        LOGGER.info("Sending previous map slam");
        sendSlam(mapHandler.getLastPath(), output, MapErrorProto.MapError.ErrorCode.MAP_NOT_AVAILABLE);
    }

    private void sendOldMap(String name, OutputStream output) {
        if (output == null) {
            LOGGER.warning("OutputStream null");
            return;
        }
        if (name == null) {
            MapPackageProto.MapPackage.Builder builder = MapPackageProto.MapPackage.newBuilder();
            LOGGER.warning("Name not provided");
            builder.setError(constructError(MapErrorProto.MapError.ErrorCode.COMMUNICATION_ERROR, ""));
            try {
                builder.build().writeDelimitedTo(output);
            } catch (IOException ignore) {
                LOGGER.warning("Couldn't send error message");
            }
            return;
        }
        LOGGER.info("Sending old map: " + name);
        sendMap(mapHandler.getOldMap(name), output, MapErrorProto.MapError.ErrorCode.MAP_NOT_FOUND);
    }

    private void sendOldMapSlam(String name, OutputStream output) {
        if (output == null) {
            LOGGER.warning("OutputStream null");
            return;
        }
        if (name == null) {
            MapPackageProto.MapPackage.Builder builder = MapPackageProto.MapPackage.newBuilder();
            LOGGER.warning("Name not provided");
            builder.setError(constructError(MapErrorProto.MapError.ErrorCode.COMMUNICATION_ERROR, ""));
            try {
                builder.build().writeDelimitedTo(output);
            } catch (IOException ignore) {
                LOGGER.warning("Couldn't send error message");
            }
            return;
        }
        LOGGER.info("Sending old map slam: " + name);
        sendSlam(mapHandler.getOldPath(name), output, MapErrorProto.MapError.ErrorCode.MAP_NOT_FOUND);
    }

    private void sendMap(MapPackageProto.MapPackage map, OutputStream output, MapErrorProto.MapError.ErrorCode applicableError) {
        if (output == null) {
            LOGGER.warning("OutputStream null");
            return;
        }
        MapPackageProto.MapPackage.Builder builder = MapPackageProto.MapPackage.newBuilder();
        if (authenticated) {
            if (map == null) {
                LOGGER.warning("Map null");
                builder.setError(constructError(applicableError, "Map null"));
            } else {
                LOGGER.info("Generating map package and sending");
                try {
                    map.writeDelimitedTo(output);
                    return;
                } catch (IOException ignore) {
                    LOGGER.warning("Couldn't send map message");
                }
            }
        } else {
            LOGGER.warning("Not authenticated");
            builder.setError(constructError(MapErrorProto.MapError.ErrorCode.NOT_AUTHENTICATED, ""));
        }
        try {
            LOGGER.info("Sending map message");
            builder.build().writeDelimitedTo(output);
        } catch (IOException ignore) {
            LOGGER.warning("Couldn't send map message");
        }
    }

    private void sendSlam(MapSlamProto.MapSlam map, OutputStream output, MapErrorProto.MapError.ErrorCode applicableError) {
        if (output == null) {
            LOGGER.warning("OutputStream null");
            return;
        }
        MapSlamProto.MapSlam.Builder builder = MapSlamProto.MapSlam.newBuilder();
        if (authenticated) {
            if (map == null) {
                LOGGER.warning("Map null");
                builder.setError(constructError(applicableError, "Map null"));
            } else {
                LOGGER.info("Generating map slam and sending");
                try {
                    map.writeDelimitedTo(output);
                    return;
                } catch (IOException ignore) {
                    LOGGER.warning("Couldn't send map slam message");
                }
            }
        } else {
            LOGGER.warning("Not authenticated");
            builder.setError(constructError(MapErrorProto.MapError.ErrorCode.NOT_AUTHENTICATED, ""));
        }
        try {
            LOGGER.info("Sending map slam message");
            builder.build().writeDelimitedTo(output);
        } catch (IOException ignore) {
            LOGGER.warning("Couldn't send map slam message");
        }
    }

    private MapErrorProto.MapError constructError(MapErrorProto.MapError.ErrorCode code, String opt){
        LOGGER.info("Constructing error message");
        MapErrorProto.MapError.Builder error = MapErrorProto.MapError.newBuilder();
        if (code == null) {
            LOGGER.warning("Code null");
            code = MapErrorProto.MapError.ErrorCode.UNKNOWN;
        }
        error.setCode(code);
        if (opt != null) {
            LOGGER.info("No opt string provided");
            error.setOpt(opt);
        }
        LOGGER.info("Building error message");
        return error.build();
    }

    private void forceClose(){
        try {
            LOGGER.info("Closing socket");
            socket.close();
        } catch (IOException e) {
            LOGGER.info("Couldn't close socket: " + e.toString());
        }
    }
}
