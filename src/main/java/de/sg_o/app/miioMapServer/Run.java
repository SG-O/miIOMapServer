package de.sg_o.app.miioMapServer;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * This class runs a basic server.
 */
public class Run {
    private static final int TIMEOUT = 5000;
    private static final int NUMBER_OF_NO_MESSAGE = 5;

    private enum Args {
        UNKNOWN("", ""),
        MULTIPLE("-", ""),
        CURRENT_MAP("c", "Specify the path where the directory where current map is stored."),
        OLD_MAP("o", "Specify the path where the directory where old maps directories are stored."),
        LOG_FILE("l", "Specify the file the log should be written to."),
        LOG_LEVEL("d", "Specify the log level. warning; info; fine; finer; finest; all; off"),
        TOKEN("t", "Specify the path to the file where the token is stored in"),
        VERSION("v", "Print the version of this program"),
        HELP("h", "Prints this help");


        private final String arg;
        private final String description;
        private static Map<String, Args> map = new HashMap<>();

        Args(String arg, String description) {
            this.arg = arg;
            this.description = description;
        }

        static {
            for (Args st : Args.values()) {
                map.put(st.arg, st);
            }
        }

        public static Args valueOfString(String arg) {
            Args st = map.get(arg);
            if (st == null) return Args.UNKNOWN;
            return st;
        }

        @Override
        public String toString() {
            return "-" + this.arg + ": " + this.description;
        }

        public static Args[] getAll(){
            return map.values().toArray(new Args[]{});
        }
    }

    /**
     * Main method for the server.
     * @param args The run arguments. Run with "-?" to see all options.
     * @throws IOException If something with the server creation went wrong.
     */
    public static void main(String[] args) throws IOException {
        File currentMap = new File("/run/shm/");
        File oldMap = new File("/mnt/data/rockrobo/rrlog");
        File log = new File("/mnt/data/server/logs/mapServer.log");
        File token = new File("/mnt/data/miio/device.token");
        if (!log.getParentFile().exists()){
            //noinspection ResultOfMethodCallIgnored
            log.getParentFile().mkdirs();
        }
        Level lv = Level.INFO;
        for (Map.Entry<Args, String> e : parseArgs(args)){
            if (e.getKey().equals(Args.LOG_LEVEL)) lv =parseLevel(e.getValue());
            if (e.getKey().equals(Args.CURRENT_MAP)) currentMap = new File(e.getValue());
            if (e.getKey().equals(Args.OLD_MAP)) oldMap = new File(e.getValue());
            if (e.getKey().equals(Args.LOG_FILE)) {
                if (e.getValue().equals("")) {
                    log = null;
                } else {
                    log = new File(e.getValue());
                }
            }
            if (e.getKey().equals(Args.TOKEN)) token = new File(e.getValue());
            if (e.getKey().equals(Args.VERSION)) {
                printVersion();
                return;
            }
            if (e.getKey().equals(Args.HELP)) {
                printVersion();
                printHelp();
                return;
            }
        }
        Server s0 = new Server(currentMap, oldMap, 54331, TIMEOUT, NUMBER_OF_NO_MESSAGE, token, lv, log);
        s0.run();
    }

    private static List<Map.Entry<Args, String>> parseArgs(String[] args){
        List<Map.Entry<Args, String>> argMap = new LinkedList<>();
        if (args == null) return argMap;
        if (args.length < 1) return argMap;
        for (int i = 0; i < args.length; i++){
            String d = args[i];
            if (d.startsWith("-")){
                if (d.length() < 2) continue;
                Args arg = Args.valueOfString(d.substring(1, 2));
                if (arg.equals(Args.UNKNOWN)) {
                    argMap.add(new AbstractMap.SimpleEntry<>(arg, d));
                    continue;
                }
                if (arg.equals(Args.MULTIPLE)) {
                    if (d.length() < 3) continue;
                    argMap.add(new AbstractMap.SimpleEntry<>(arg, d.substring(2)));
                    continue;
                }
                i++;
                if (i >= args.length) {
                    argMap.add(new AbstractMap.SimpleEntry<>(arg, ""));
                    break;
                }
                if (args[i].startsWith("-")) {
                    i--;
                    argMap.add(new AbstractMap.SimpleEntry<>(arg, ""));
                    continue;
                }
                argMap.add(new AbstractMap.SimpleEntry<>(arg, args[i]));
            } else {
                if (d.length() < 1) continue;
                argMap.add(new AbstractMap.SimpleEntry<>(Args.UNKNOWN, d));
            }
        }
        return argMap;
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

    private static void printHelp(){
        System.out.println();
        System.out.println("Program arguments:");
        System.out.println();
        Args[] all = Args.getAll();
        for (Args arg : all){
            if (arg.equals(Args.UNKNOWN) || arg.equals(Args.MULTIPLE)) continue;
            System.out.println(arg.toString());
        }
    }

    private static void printVersion(){
        final Properties properties = new Properties();
        try {
            properties.load(Run.class.getResourceAsStream("project.properties"));
            System.out.println(properties.getProperty("artifactId") + ": " + properties.getProperty("version"));
        } catch (Exception ignored) {
        }
    }
}
