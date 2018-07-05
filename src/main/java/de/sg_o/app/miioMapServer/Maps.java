package de.sg_o.app.miioMapServer;

import de.sg_o.proto.MapPackageProto;
import de.sg_o.proto.MapSlamProto;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 * This class handles the parsing of the maps stored on the device.
 */
public class Maps {
    private final static Logger LOGGER = Logger.getLogger(Maps.class.getName());

    private final File activeMapDirectory;
    private final File previousMapsDirectory;

    private MapPackageProto.MapPackage activeMap;
    private File activeMapSlam;
    private MapPackageProto.MapPackage lastMap;
    private MapSlamProto.MapSlam lastPath;
    private int lastMapNumber = 0;
    private long activeMapLastModified = 0;
    private LinkedHashMap<String, File[]> previousMaps = new LinkedHashMap<>();

    /**
     * Create a new Maps object.
     * @param activeMapDirectory The directory where the files for the active map can be found.
     * @param previousMapsDirectory The directory where the directories for the older maps can be found.
     * @param logLevel The log level.
     * @throws IOException When the directories are invalid.
     */
    public Maps(File activeMapDirectory, File previousMapsDirectory, Level logLevel) throws IOException {
        if (logLevel != null) {
            LOGGER.setLevel(logLevel);
        }
        LOGGER.info("Starting map directory checking");
        if (activeMapDirectory == null || previousMapsDirectory == null) throw new IOException();
        if (!activeMapDirectory.exists() || !previousMapsDirectory.exists()) throw new IOException();
        if (!activeMapDirectory.isDirectory() || !previousMapsDirectory.isDirectory()) throw new IOException();
        LOGGER.info("Map directories fine");
        this.activeMapDirectory = activeMapDirectory;
        this.previousMapsDirectory = previousMapsDirectory;
        LOGGER.info("Generating active map");
        generateActiveMap();
        LOGGER.info("Finished generating active map");
        LOGGER.info("Generating old maps list");
        generatePreviousMaps();
        LOGGER.info("Finished generating old maps list");
    }

    private void generateActiveMap() {
        File mapFile = null;
        File slamFile = null;
        if (activeMapDirectory == null) {
            LOGGER.warning("Active map directory not set");
            activeMap = null;
            return;
        }
        File[] files = activeMapDirectory.listFiles();
        if (files == null) {
            LOGGER.warning("Unable to list files in active map directory");
            activeMap = null;
            return;
        }
        LOGGER.info("Going through files to find active map and slam");
        for (File f : files) {
            if (f.isDirectory()) continue;
            LOGGER.fine("Active map directory file: " + f.getName());
            if (f.getName().startsWith("navmap") && f.getName().endsWith(".ppm")) {
                LOGGER.info("Found navmap");
                mapFile = f;
            }
            if (f.getName().equals("SLAM_fprintf.log")) {
                LOGGER.info("Found SLAM");
                slamFile = f;
            }
        }
        if (mapFile == null || slamFile == null) {
            LOGGER.info("No valid active map found");
            activeMap = null;
            return;
        }
        if (activeMapLastModified == mapFile.lastModified()) {
            LOGGER.info("Map file doesn't need updating");
            return;
        }

        try {
            synchronized(this) {
                LOGGER.info("Creating active de.sg_o.app.miioMapServer.VacuumMap");
                activeMap = VacuumMap.directToMapPackage(new BufferedReader(new FileReader(mapFile)));
                activeMapSlam = slamFile;
                LOGGER.info("Created active de.sg_o.app.miioMapServer.VacuumMap");
                activeMapLastModified = mapFile.lastModified();
            }
        } catch (IOException e) {
            LOGGER.warning("Unable to open active map files");
            activeMap = null;
        }
    }

    private void generatePreviousMaps() {
        if (previousMapsDirectory == null) {
            LOGGER.warning("Previous maps directory not set");
            previousMaps = new LinkedHashMap<>();
            return;
        }
        File[] files = previousMapsDirectory.listFiles();
        if (files == null) {
            LOGGER.warning("Unable to list files in previous maps directory");
            previousMaps = new LinkedHashMap<>();
            return;
        }
        for (File f : files) {
            if (!f.isDirectory()) continue;
            LOGGER.fine("Previous map directory: " + f.getName());
            extractMap(f);
        }
        LOGGER.info("Checking for the latest previous map");
        String latestMapName = null;
        for (String s : previousMaps.keySet()) {
            LOGGER.fine("Checking previous map: " + s);
            String[] split = s.split("\\.");
            if (split.length < 1) {
                LOGGER.info("The directory name could not be parsed: " + s);
                continue;
            }
            try {
                int i = Integer.valueOf(split[0]);
                if (i > lastMapNumber){
                    LOGGER.fine("Found newer map" + i);
                    lastMapNumber = i;
                    latestMapName = s;
                }
            }catch (Exception e){
                LOGGER.info("The directory name could not be parsed: " + s + ": " + e.toString());
            }
        }
        if (latestMapName != null){
            LOGGER.info("Generating latest old vacuumMap");
            lastMap = getOldMap(latestMapName);
            lastPath = getOldPath(latestMapName);
            LOGGER.info("Generated latest old vacuumMap");
        }
    }

    private void extractMap(File folder){
        File mapFile = null;
        File slamFile = null;
        if (folder == null) {
            LOGGER.warning("Map directory for extraction not set");
            return;
        }
        if (previousMaps.containsKey(folder.getName())) return;
        File[] files = folder.listFiles();
        if (files == null) {
            LOGGER.warning("Unable to list files in directory for extraction");
            return;
        }
        LOGGER.info("Going through files to find previous map and slam");
        for (File f : files) {
            LOGGER.fine("Previous map directory file: " + f.getName());
            if (f.isDirectory()) continue;
            if (f.getName().startsWith("navmap") && f.getName().endsWith(".gz")) {
                LOGGER.info("Found navmap");
                mapFile = f;
            }
            if (f.getName().startsWith("SLAM_fprintf.log") && f.getName().endsWith(".gz")) {
                LOGGER.info("Found SLAM");
                slamFile = f;
            }
        }
        if (mapFile == null || slamFile == null) {
            LOGGER.info("No valid previous map found");
            return;
        }
        synchronized(this) {
            LOGGER.info("Preparing storing of map name and files");
            File[] mapFiles = new File[2];
            mapFiles[0] = mapFile;
            mapFiles[1] = slamFile;
            LOGGER.info("Storing of map name and files");
            previousMaps.put(folder.getName(), mapFiles);
            LOGGER.info("Stored of map name and files");
        }
    }

    /**
     * Get a old map.
     * @param name The maps name.
     * @return The old map or null if no map was found.
     */
    public MapPackageProto.MapPackage getOldMap(String name){
        if (name == null) {
            LOGGER.warning("No old map file provided to parse");
            return null;
        }
        File[] map = previousMaps.get(name);
        if (map == null) {
            LOGGER.warning("Old map " + name + " not found");
            return null;
        }
        if (map.length != 2) {
            LOGGER.warning("Old map entry not of correct length");
            return null;
        }
        LOGGER.info("Decompressing map file");
        BufferedReader mapReader = unzipFile(map[0]);
        LOGGER.info("Done decompressing");
        if (mapReader == null) {
            LOGGER.warning("Decompression failed");
            return null;
        }
        LOGGER.info("Generating old map");
        try {
            return VacuumMap.directToMapPackage(mapReader);
        } catch (IOException e) {
            LOGGER.warning("Unable to open old map file");
            return null;
        }
    }

    /**
     * Get a old maps path.
     * @param name The maps name.
     * @return The old maps path or null if no map was found.
     */
    public MapSlamProto.MapSlam getOldPath(String name){
        if (name == null) {
            LOGGER.warning("No old map file provided to parse");
            return null;
        }
        File[] map = previousMaps.get(name);
        if (map == null) {
            LOGGER.warning("Old map " + name + " not found");
            return null;
        }
        if (map.length != 2) {
            LOGGER.warning("Old map entry not of correct length");
            return null;
        }
        LOGGER.info("Decompressing SLAM file");
        BufferedReader slamReader = unzipFile(map[1]);
        LOGGER.info("Done decompressing");
        if (slamReader == null) {
            LOGGER.warning("Decompression failed");
            return null;
        }
        LOGGER.info("Generating old map");
        try {
            return VacuumMap.directToPath(slamReader);
        } catch (IOException e) {
            LOGGER.warning("Unable to open old path file");
            return null;
        }
    }

    private BufferedReader unzipFile(File compressed) {
        if (compressed == null) {
            LOGGER.warning("File for extraction not set");
            return null;
        }
        GZIPInputStream gin;
        try {
            LOGGER.info("Generating GZIPInputStream");
            gin = new GZIPInputStream(new FileInputStream(compressed));
        } catch (IOException e) {
            LOGGER.warning("GZIPInputStream could not be created");
            return null;
        }
        LOGGER.fine("Creating small buffer for decompression");
        byte[] buf = new byte[1024];
        try {
            int len;
            LOGGER.fine("Creating big buffer for decompression");
            byte[] all = new byte[4194304];
            int i = 0;
            LOGGER.info("Starting decompression");
            while ((len = gin.read(buf)) > 0) {
                LOGGER.fine("Decompression round " + (i + 1));
                LOGGER.fine("Decompressed " + len + "bytes. Copping to to big buffer");
                System.arraycopy(buf, 0, all, i, len);
                i += len;
            }
            LOGGER.info("Creating decompressed InputStream");
            InputStream is = new ByteArrayInputStream(all, 0, i);
            LOGGER.info("Creating uncompressed BufferedReader");
            return new BufferedReader(new InputStreamReader(is));
        } catch (IOException e) {
            LOGGER.warning("Decompression failed: " + e.toString());
            return null;
        }
    }

    /**
     * @return The active map or null if it isn't available.
     */
    public MapPackageProto.MapPackage getActiveMap() {
        return activeMap;
    }

    /**
     * @return The latest of the old maps or null if it isn't available.
     */
    public MapPackageProto.MapPackage getLastMap() {
        return lastMap;
    }

    /**
     * @return The latest of the old maps path or null if it isn't available.
     */
    public MapSlamProto.MapSlam getLastPath() {
        return lastPath;
    }

    /**
     * @return All names of the old maps.
     */
    public Set<String> getPreviousMaps() {
        return previousMaps.keySet();
    }

    /**
     * @return True if a active map is available.
     */
    public boolean hasActiveMap() {
        return !(activeMap == null);
    }

    /**
     * @return The number of old maps.
     */
    public int numberOfPreviousMaps() {
        return previousMaps.size();
    }

    /**
     * Update the active map.
     */
    public void updateActiveMap() {
        generateActiveMap();
    }

    /**
     * Get the active maps path from a certain start position.
     * @param start The position to start to read from;
     * @return The path from that start point or null if the path could not be read.
     */
    public MapSlamProto.MapSlam getActivePathFrom(int start) {
        if (activeMapSlam == null) {
            LOGGER.info("No slam file set");
            return null;
        }
        if (!activeMapSlam.exists()) {
            LOGGER.info("Slam file does not exist");
            return null;
        }
        if (activeMap == null) {
            LOGGER.info("Active map not set");
            return null;
        }
        try {
            synchronized(this) {
                LOGGER.info("Appending slam");
                return VacuumMap.directToPath(new BufferedReader(new FileReader(activeMapSlam)), start);
            }
        } catch (IOException e) {
            LOGGER.warning("Appending slam failed");
            return null;
        }
    }

    /**
     * Update the old maps.
     */
    public void updatePreviousMaps() {
        generatePreviousMaps();
    }
}
