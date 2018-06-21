package de.sg_o.app.miioMapServer;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class Run {
    public static void main(String[] args) throws IOException {
        File currentMap = new File("/run/shm/");
        File oldMap = new File("/mnt/data/rockrobo/rrlog");
        File log = new File("/mnt/data/server/logs/mapServer.log");
        if (!log.getParentFile().exists()){
            //noinspection ResultOfMethodCallIgnored
            log.getParentFile().mkdirs();
        }
        Level lv = Level.INFO;
        if (args.length > 0){
            boolean debugFound = false;
            for (String d : args){
                if (debugFound){
                    lv = parseLevel(d.toLowerCase());
                } else {
                    if (d.equals("-d")) debugFound = true;
                }
            }
        }
        Server s0 = new Server(currentMap, oldMap, 54331, new File("/mnt/data/miio/device.token"), lv, log);
        s0.run();
    }

    private static Level parseLevel(String in){
        switch (in){
            case "warning":
                return Level.WARNING;
            case "info":
                return Level.INFO;
            case "fine":
                return Level.FINE;
            case "finer":
                return Level.FINER;
            case "finest":
                return Level.FINEST;
            case "all":
                return Level.ALL;
            case "off":
                return Level.OFF;
        }
        return Level.INFO;
    }
}
