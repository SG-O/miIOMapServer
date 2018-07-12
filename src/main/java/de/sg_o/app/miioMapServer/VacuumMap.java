package de.sg_o.app.miioMapServer;/*
 * Copyright (c) 2018 Joerg Bayer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import de.sg_o.proto.MapErrorProto;
import de.sg_o.proto.MapPackageColorProto;
import de.sg_o.proto.MapPackageProto;
import de.sg_o.proto.MapSlamProto;

import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * This class handles the vacuums map. It parses the files stored on the vacuum, creates a bounding box for the area with valid data in the map, allows scaling the map, provides serialization and deserialization.
 */
@SuppressWarnings("WeakerAccess")
public class VacuumMap implements Serializable {
    private static final long serialVersionUID = 6328146796574327681L;
    private static final int MAP_WIDTH = 1024;
    private static final int MAP_HEIGHT = 1024;

    public static final int RED = -65536;
    public static final int GREEN = -16711936;
    public static final int BLUE = -16776961;

    private final static Logger LOGGER = Logger.getLogger(ServerThread.class.getName());

    private transient int[] map;
    private transient List<float[]> path = new LinkedList<>();
    private int[] boundingBox;
    private int overSample;
    private int numberOfSlamLines = 0;
    private boolean slamLocked = true;

    /**
     * Create a vacuum map object.
     * @param image The reader the map image should be read from.
     * @param slam The reader the slam log should be read from.
     * @param overSample The oversampling that should be applied to the map.
     * @param logLevel The log level.
     */
    public VacuumMap(BufferedReader image, BufferedReader slam, int overSample, Level logLevel) {
        if (logLevel != null) {
            LOGGER.setLevel(logLevel);
        } else {
            LOGGER.setLevel(Level.OFF);
        }
        if (overSample < 1) overSample = 1;
        this.overSample = overSample;
        LOGGER.fine("Creating empty image");
        this.map = new int[MAP_WIDTH * MAP_HEIGHT];
        LOGGER.fine("Creating maximum bounding box");
        this.boundingBox = new int[]{0, 0, 1024, 1024};
        try {
            LOGGER.info("Reading image");
            readMap(image);
            LOGGER.info("Reading slam");
            readSlam(slam);
        } catch (Exception e){
            LOGGER.warning("Reading failed: " + e);
        }
    }

    public VacuumMap(MapPackageProto.MapPackage image, MapSlamProto.MapSlam slam, int overSample) {
        if (overSample < 1) overSample = 1;
        this.overSample = overSample;
        this.map = new int[MAP_WIDTH * MAP_HEIGHT];
        this.boundingBox = new int[]{0, 0, 1024, 1024};
        decodeMapPackage(image);
        decodeMapSlam(slam);
    }

    private void readMap(BufferedReader image) throws IOException {
        LOGGER.fine("Initializing bounding box creation");
        int x = 0;
        int y = 0;
        int top = MAP_HEIGHT;
        int bottom = 0;
        int left = MAP_WIDTH;
        int right = 0;
        if (image.readLine() == null) {
            LOGGER.warning("File format invalid");
            return;
        }
        if (image.readLine() == null) {
            LOGGER.warning("File format invalid");
            return;
        }
        while (true) {
            LOGGER.fine("Reading pixel: " + x + "," + y);
            int[] rgb = {image.read(), image.read(), image.read()};
            if (rgb[0] < 0 || rgb[1] < 0 || rgb[2] < 0) {
                LOGGER.info("End of map file reached");
                boundingBox = new int[]{left, top, (right - left) + 1, (bottom - top) + 1};
                return;
            }
            for (int i = 0; i < rgb.length; i++) {
                rgb[i] = rgb[i] & 0xFF;
            }
            LOGGER.fine("Setting pixel");
            map[x + (y * MAP_WIDTH)] = toColorInt(rgb[0], rgb[1], rgb[2], 0xff);
            if (rgb[0] != 125 || rgb[1] != 125 || rgb[2] != 125){
                LOGGER.fine("Updating bounding box");
                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                if (y > bottom) bottom = y;
            }
            x++;
            if (x >= MAP_WIDTH){
                x = 0;
                y++;
            }
            if (y >= MAP_HEIGHT){
                LOGGER.info("Restarting at the start of the image");
                y = 0;
            }
        }
    }

    private void readSlam(BufferedReader slam) throws IOException {
        String line;
        float oldX = 100000;
        float oldY = 100000;
        while ((line = slam.readLine()) != null){
            LOGGER.fine("Parsing line: " + line);
            numberOfSlamLines++;
            if (line.contains("reset")){
                LOGGER.fine("Reset");
                path = new LinkedList<>();
                oldX = 100000;
                oldY = 100000;
            }
            if (line.contains("lock")) {
                LOGGER.fine("Lock");
                slamLocked = true;
            }
            if (line.contains("unlock")) {
                LOGGER.fine("Unlock");
                slamLocked = false;
            }
            if (slamLocked) continue;
            if (line.contains("estimate")){
                LOGGER.fine("Parsing estimate");
                String[] split = line.split("\\s+");
                if (split.length != 5) {
                    LOGGER.info("Estimate of wrong length");
                    continue;
                }
                float x;
                float y;
                try {
                    x = Float.valueOf(split[2]) * (20.0f);
                    y = Float.valueOf(split[3]) * (-20.0f);
                    if ((Math.abs(x - oldX) > 1.0f) || (Math.abs(y - oldY) > 1.0f)){
                        oldX = x;
                        oldY = y;
                    } else {
                        continue;
                    }
                    path.add(new float[]{x, y});
                } catch (Exception e){
                    LOGGER.warning("Parsing coordinates failed: " + e);
                }

            }
        }
    }

    /**
     * Append new slam lines to the map
     * @param slam The reader to parse
     */
    public void appendSlam(BufferedReader slam) {
        try {
            for (int i = 0; i < numberOfSlamLines; i++) {
                if (slam.readLine() == null) return;
            }
            readSlam(slam);
        } catch (Exception e) {
            LOGGER.warning("Parsing slam failed: " + e);
        }
    }

    /**
     * @return The complete map.
     */
    public synchronized int[] getMap() {
        int width = MAP_WIDTH * overSample;
        int height = MAP_HEIGHT * overSample;
        int[] outMap = new int[width * height];

        for (int y = 0; y < MAP_HEIGHT; y++) {
            for (int x = 0; x < MAP_WIDTH; x++) {
                int color = map[x + (y * MAP_WIDTH)];
                for (int b = 0; b < overSample; b++) {
                    for (int a = 0; a < overSample; a++) {
                        int c = (x * overSample) + a;
                        int d = (y * overSample) + b;
                        outMap[c + (d * MAP_WIDTH * overSample)] = color;
                    }
                }
            }
        }

        return outMap;
    }

    /**
     * @return The unscaled complete map.
     */
    public synchronized int[] getRawMap() {
        return map;
    }

    /**
     * @return The path the vacuum took.
     */
    public synchronized List<float[]> getPath() {
        List<float[]> outPath = new LinkedList<>();
        for (float[] p : path){
            outPath.add(new float[]{(p[0] + (MAP_WIDTH / 2.0f)) * overSample, (p[1] + (MAP_HEIGHT / 2.0f)) * overSample});
        }
        return outPath;
    }

    /**
     * @return The unscaled path the vacuum took.
     */
    public synchronized List<float[]> getRawPath() {
        return path;
    }

    /**
     * @return The number of points in the path.
     */
    public synchronized int getPathSize(){
        return path.size();
    }

    /**
     * @return The bounding box of the active map area.
     */
    public synchronized int[] getBoundingBox() {
        int[] tmp = new int[4];
        tmp[0] = boundingBox[0] * overSample;
        tmp[1] = boundingBox[1] * overSample;
        tmp[2] = boundingBox[2] * overSample;
        tmp[3] = boundingBox[3] * overSample;
        return tmp;
    }

    /**
     * @return The unscaled bounding box of the active map area.
     */
    public synchronized int[] getRawBoundingBox() {
        return boundingBox;
    }

    /**
     * @return The current overSample set.
     */
    public synchronized int getOverSample() {
        return overSample;
    }

    /**
     * @param overSample The new overSample to set.
     */
    public synchronized void setOverSample(int overSample) {
        if (overSample < 1) overSample = 1;
        this.overSample = overSample;
    }

    /**
     * Get coordinates from a point in this map. It can be used to define the point the vacuum should move to.
     * @param p The point to convert.
     * @return An array of coordinates (x, y).
     */
    public synchronized int[] mapPointScale(int[] p) {
        if (p == null) p = new int[]{0,0};
        int[] scaled = new int[2];
        scaled[0] = p[0] / overSample;
        scaled[1] = p[1] / overSample;
        return scaled;
    }

    /**
     * Get coordinates from a rectangle in this map. They can be used to define the area of the area cleanup.
     * @param rec The rectangle to convert.
     * @return An array of coordinates (x0, y0, x1, y1).
     */
    public synchronized int[] mapRectangleScale(int[] rec) {
        if (rec == null) rec = new int[]{0,0,0,0};
        int[] scaled = new int[4];
        scaled[0] = rec[0] / overSample;
        scaled[1] = rec[1] / overSample;
        scaled[2] = (rec[0] / overSample) + (rec[2] / overSample);
        scaled[3] = (rec[1] / overSample) + (rec[3] / overSample);
        return scaled;
    }

    /**
     * @return The map with the path drawn into it within the bounding box. Using the color green for the start point and blue for the path.
     */
    public int[] getMapWithPathInBounds(){
        return getMapWithPathInBounds(null, null);
    }

    /**
     * @return The map with the path drawn into it within the bounding box.
     * @param startColor The color the start point should be drawn with. If null is provided this will fall back to green.
     * @param pathColor The color the path should be drawn with. If null is provided this will fall back to blue.
     */
    public int[] getMapWithPathInBounds(Integer startColor, Integer pathColor){
        int[] raw = getMapWithPath(startColor, pathColor);
        int[] tmp = getBoundingBox();
        int[] out = new int[tmp[2] * tmp[3]];
        for (int y = 0; y < tmp[3]; y++){
            for (int x = 0; x < tmp[2]; x++){
                int a = x + tmp[0];
                int b = y + tmp[1];
                out[x + (y * tmp[2])] = raw[a + (b * MAP_WIDTH * overSample)];
            }
        }
        return out;
    }

    /**
     * @return The map with the path drawn into it. Using the color green for the start point and blue for the path.
     */
    public int[] getMapWithPath(){
        return getMapWithPath(null, null);
    }

    /**
     * @return The map with the path drawn into it.
     * @param startColor The color the start point should be drawn with. If null is provided this will fall back to green.
     * @param pathColor The color the path should be drawn with. If null is provided this will fall back to blue.
     */
    public synchronized int[] getMapWithPath(Integer startColor, Integer pathColor) {
        if (startColor == null) startColor = GREEN;
        if (pathColor == null) pathColor = BLUE;

        int sColor = startColor;
        int pColor = pathColor;

        int[] pathMap = getMap();
        drawRectangle((MAP_WIDTH * overSample / 2) - 10, (MAP_HEIGHT * overSample / 2) -10, 20, 20, pathMap, MAP_WIDTH * overSample, sColor);

        List<float[]> path = getPath();

        float[] oldP = null;
        for (float[] p : path) {
            if (oldP == null) {
                oldP = p;
                continue;
            }
            int x0 = Math.round(oldP[0]);
            int y0 = Math.round(oldP[1]);
            int x1 = Math.round(p[0]);
            int y1 = Math.round(p[1]);

            drawLine(x0, y0, x1, y1, pathMap, MAP_WIDTH * overSample, pColor);

            oldP = p;
        }

        return pathMap;
    }

    @SuppressWarnings("SameParameterValue")
    private void drawRectangle(int x0, int y0, int w, int h, int[] map, int mapWidth, int color) {
        for (int j = y0; j < (y0 + h); j++) {
            for (int i = x0; i < (x0 + w); i++) {
                map[i + (j * mapWidth)] = color;
            }
        }
    }

    private void drawLine(int x0, int y0, int x1, int y1, int[] map, int w, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);

        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;

        int err = dx-dy;
        int e2;

        while (true) {
            map[x0 + (y0 * w)] = color;
            if (x0 == x1 && y0 == y1) break;

            e2 = 2 * err;
            if (e2 > -dy) {
                err = err - dy;
                x0 = x0 + sx;
            }
            if (e2 < dx) {
                err = err + dx;
                y0 = y0 + sy;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VacuumMap vacuumMap = (VacuumMap) o;
        if (map.length != vacuumMap.map.length) return false;
        for (int i = 0; i < map.length; i++){
            if (map[i] != vacuumMap.map[i]) {
                return false;
            }
        }
        boolean ret;
        synchronized(this) {
            ret =  overSample == vacuumMap.overSample &&
                    Objects.equals(path.size(), vacuumMap.path.size()) &&
                    Arrays.equals(boundingBox, vacuumMap.boundingBox);
        }
        return ret;
    }

    @Override
    public int hashCode() {
        int ret;
        synchronized(this) {
            ret = Objects.hash(path.size(), boundingBox[0], boundingBox[1], boundingBox[2], boundingBox[3], overSample);
        }
        return ret;
    }

    @Override
    public String toString() {
        String ret;
        synchronized(this) {
            ret = "de.sg_o.app.miioMapServer.VacuumMap{" +
                    "map=width:" + MAP_WIDTH * overSample + "; height:" + MAP_HEIGHT * overSample +
                    ", pathEntries=" + path.size() +
                    ", boundingBox=" + Arrays.toString(getBoundingBox()) +
                    ", overSample=" + overSample +
                    '}';
        }
        return ret;
    }

    private int[] getMapInBounds(){
        int[] tmp = boundingBox;
        int[] out = new int[tmp[2] * tmp[3]];
        for (int y = 0; y < tmp[3]; y++){
            for (int x = 0; x < tmp[2]; x++){
                int a = x + tmp[0];
                int b = y + tmp[1];
                out[x + (y * tmp[2])] = map[a + (b * MAP_WIDTH)];
            }
        }
        return out;
    }

    /**
     * Get the complete map image as a proto message.
     * @return The complete map image as a proto message.
     */
    public synchronized MapPackageProto.MapPackage getMapPackage(){
        LOGGER.info("Getting the map within bounds");
        int[] mapInBounds = getMapInBounds();
        HashMap<Integer, MapPackageColorProto.MapPackageColor.Builder> colorMap = new HashMap<>();
        LOGGER.info("Creating all colors");
        for (int j = 0; j < boundingBox[3]; j++) {
            for (int i = 0; i < boundingBox[2]; i++) {
                LOGGER.fine("Getting color for pixel: " + i + "," + j);
                int color = mapInBounds[i + (j * boundingBox[2])];
                LOGGER.fine("Checking whether the color is already in the colorMap");
                MapPackageColorProto.MapPackageColor.Builder builder = colorMap.get(color);
                if (builder == null){
                    LOGGER.fine("Adding new color: " + color);
                    MapPackageColorProto.MapPackageColor.Builder nBuilder = MapPackageColorProto.MapPackageColor.newBuilder();
                    nBuilder.setColor(color);
                    int comp = (j << 16) + i;
                    nBuilder.addCoordinates(comp);
                    colorMap.put(color, nBuilder);
                } else {
                    LOGGER.fine("Adding to existing color");
                    int comp = (j << 16) + i;
                    builder.addCoordinates(comp);
                }
            }
        }
        MapPackageProto.MapPackage.Builder pack = MapPackageProto.MapPackage.newBuilder();
        pack.setError(MapErrorProto.MapError.newBuilder().setCode(MapErrorProto.MapError.ErrorCode.NONE).build());
        LOGGER.info("Adding bounding box to output");
        pack.setVersion(0);
        pack.setActiveX(boundingBox[0]);
        pack.setActiveY(boundingBox[1]);
        pack.setActiveW(boundingBox[2]);
        pack.setActiveH(boundingBox[3]);
        LOGGER.info("Adding all colors to output");
        for (MapPackageColorProto.MapPackageColor.Builder b : colorMap.values()){
            MapPackageColorProto.MapPackageColor col = b.build();
            LOGGER.fine("Adding color: " + col.toString());
            pack.addData(col);
        }
        LOGGER.info("Building output");
        return pack.build();
    }

    private void decodeMapPackage(MapPackageProto.MapPackage image) {
        if (image == null) return;
        boundingBox[0] = image.getActiveX();
        boundingBox[1] = image.getActiveY();
        boundingBox[2] = image.getActiveW();
        boundingBox[3] = image.getActiveH();
        synchronized(this) {
            for (int i = 0; i < map.length; i++){
                map[i] = toColorInt(125, 125, 125, 0xff);
            }

            switch (image.getVersion()) {
                case 1:
                    for (MapPackageColorProto.MapPackageColor c : image.getDataList()) {
                        int color = c.getColor();
                        for (int pos : c.getCoordinatesList()) {
                            map[((pos & 0xFFFF) + ((pos >> 16) & 0xFFFF) * MAP_WIDTH)] = color;
                        }
                    }
                    break;
                default:
                    for (MapPackageColorProto.MapPackageColor c : image.getDataList()) {
                        int color = c.getColor();
                        for (int pos : c.getCoordinatesList()) {
                            map[(boundingBox[0] + (pos & 0xFFFF)) + ((boundingBox[1] + ((pos >> 16) & 0xFFFF)) * MAP_WIDTH)] = color;
                        }
                    }
            }
        }
    }

    /**
     * Get the complete path as a proto message.
     * @return The path as a proto message.
     */
    public MapSlamProto.MapSlam getMapPath() {
        return getMapPath(0);
    }

    /**
     * Get the maps path as a proto message.
     * @param start The first index of the path pints to get.
     * @return The path as a proto message.
     */
    public MapSlamProto.MapSlam getMapPath(int start){
        LOGGER.info("Getting path from " + start);
        MapErrorProto.MapError.Builder err = MapErrorProto.MapError.newBuilder();
        MapSlamProto.MapSlam.Builder slam = MapSlamProto.MapSlam.newBuilder();
        synchronized(this) {
            if ((start >= path.size()) || (start < 0)) {
                LOGGER.warning("Path out of range");
                err.setCode(MapErrorProto.MapError.ErrorCode.SLAM_OUT_OF_RANGE);
                return slam.setError(err.build()).build();
            }
            for (int i = start; i < path.size(); i++) {
                LOGGER.fine("Adding point: " + i);
                MapSlamProto.MapSlam.Point.Builder point = MapSlamProto.MapSlam.Point.newBuilder();
                float[] p = path.get(i);
                point.setX(p[0]);
                point.setY(p[1]);
                slam.addPoints(point.build());
            }
        }
        err.setCode(MapErrorProto.MapError.ErrorCode.NONE);
        slam.setError(err.build());
        LOGGER.info("Building slam message");
        return slam.build();
    }

    private void decodeMapSlam(MapSlamProto.MapSlam slam) {
        synchronized(this) {
            path = new LinkedList<>();
        }
        appendMapSlam(slam);
    }

    /**
     * Add new path segments to the previous path.
     * @param slam The path segments to add.
     */
    public void appendMapSlam(MapSlamProto.MapSlam slam) {
        if (slam == null) return;
        synchronized(this) {
            for (MapSlamProto.MapSlam.Point p : slam.getPointsList()) {
                path.add(new float[]{p.getX(), p.getY()});
            }
        }
    }

    private byte[] mapToBytes() {
        byte[] out = new byte[map.length * 4];
        synchronized(this) {
            for (int i = 0; i < map.length; i++){
                out[(i * 4)] = (byte) (map[i] & 0xFF);
                out[(i * 4) + 1] = (byte) ((map[i] >> 8) & 0xFF);
                out[(i * 4) + 2] = (byte) ((map[i] >> 16) & 0xFF);
                out[(i * 4) + 3] = (byte) ((map[i] >> 24) & 0xFF);
            }
        }
        return out;
    }

    private void bytesToMap(byte[] source) throws IOException {
        map = new int[MAP_WIDTH * MAP_HEIGHT];
        if ((map.length * 4) != source.length) throw new IOException();
        synchronized(this) {
            for (int i = 0; i < map.length; i++) {
                int tmp = 0;
                tmp |= source[(i * 4) + 3] & 0xFF;
                tmp = tmp << 8;
                tmp |= source[(i * 4) + 2] & 0xFF;
                tmp = tmp << 8;
                tmp |= source[(i * 4) + 1] & 0xFF;
                tmp = tmp << 8;
                tmp |= source[(i * 4)] & 0xFF;
                this.map[i] = tmp;
            }
        }
    }

    private byte[] pathToBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        synchronized(this) {
            oos.writeObject(this.path);
        }
        oos.flush();
        baos.flush();
        byte[] outputTrimmed = baos.toByteArray();
        oos.close();
        baos.close();
        return outputTrimmed;
    }

    private void bytesToPath(byte[] source) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(source);
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object o = ois.readObject();
        try {
            synchronized(this) {
                //noinspection unchecked
                this.path = (List<float[]>) o;
            }
        } catch (ClassCastException e){
            throw new IOException("Can't convert to path");
        }
        ois.close();
        bais.close();
    }

    private byte[] compress(byte[] data) {
        byte[] output = new byte[data.length + 1000];
        Deflater compressor = new Deflater();
        compressor.setInput(data);
        compressor.finish();

        byte[] outputTrimmed = new byte[compressor.deflate(output)];
        System.arraycopy(output, 0, outputTrimmed, 0, outputTrimmed.length);
        return outputTrimmed;
    }

    private void inflate(byte[] compressed, byte[] restored) throws IOException {
        Inflater inflate = new Inflater();
        inflate.setInput(compressed);
        inflate.finished();
        try {
            int restoredBytes = inflate.inflate(restored);
            if (restoredBytes != restored.length) throw new IOException("Image size does not match");
        } catch (DataFormatException e) {
            throw new IOException("Inflation failed");
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        byte[] imgBytes = mapToBytes();
        byte[] compressedMap = compress(imgBytes);
        byte[] pathBytes = pathToBytes();
        byte[] compressedPath = compress(pathBytes);
        out.writeInt(compressedMap.length);
        out.writeInt(imgBytes.length);
        out.writeInt(compressedPath.length);
        out.writeInt(pathBytes.length);
        out.write(compressedMap);
        out.write(compressedPath);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        byte[] compressedMap = new byte[in.readInt()];
        byte[] mapBytes = new byte[in.readInt()];
        byte[] compressedPath = new byte[in.readInt()];
        byte[] pathBytes = new byte[in.readInt()];
        in.readFully(compressedMap);
        in.readFully(compressedPath);
        inflate(compressedMap, mapBytes);
        inflate(compressedPath, pathBytes);
        bytesToMap(mapBytes);
        bytesToPath(pathBytes);
    }

    /**
     * Create a map message directly from a map file.
     * @param image The map file to parse.
     * @return The message containing the map.
     * @throws IOException If the file could not be read.
     */
    public static MapPackageProto.MapPackage directToMapPackage(BufferedReader image) throws IOException {
        LOGGER.fine("Initializing bounding box creation");
        int x = 0;
        int y = 0;
        int top = MAP_HEIGHT;
        int bottom = 0;
        int left = MAP_WIDTH;
        int right = 0;

        if (image.readLine() == null) {
            LOGGER.warning("File format invalid");
            throw new IOException("File format invalid");
        }
        if (image.readLine() == null) {
            LOGGER.warning("File format invalid");
            throw new IOException("File format invalid");
        }

        HashMap<Integer, MapPackageColorProto.MapPackageColor.Builder> colorMap = new HashMap<>();

        while (true) {
            int[] rgb = {image.read(), image.read(), image.read()};
            if (rgb[0] < 0 || rgb[1] < 0 || rgb[2] < 0) {
                LOGGER.info("End of map file reached");
                //boundingBox = new int[]{left, top, (right - left) + 1, (bottom - top) + 1};
                break;
            }
            for (int i = 0; i < rgb.length; i++) {
                rgb[i] = rgb[i] & 0xFF;
            }
            //map[x + (y * MAP_WIDTH)] = toColorInt(rgb[0], rgb[1], rgb[2], 0xff);
            if (rgb[0] != 125 || rgb[1] != 125 || rgb[2] != 125){
                int color = toColorInt(rgb[0], rgb[1], rgb[2], 0xff);

                MapPackageColorProto.MapPackageColor.Builder builder = colorMap.get(color);
                if (builder == null){
                    LOGGER.fine("Adding new color: " + color);
                    MapPackageColorProto.MapPackageColor.Builder nBuilder = MapPackageColorProto.MapPackageColor.newBuilder();
                    nBuilder.setColor(color);
                    int comp = (y << 16) + x;
                    nBuilder.addCoordinates(comp);
                    colorMap.put(color, nBuilder);
                } else {
                    LOGGER.fine("Adding to existing color");
                    int comp = (y << 16) + x;
                    builder.addCoordinates(comp);
                }

                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                if (y > bottom) bottom = y;
            }
            x++;
            if (x >= MAP_WIDTH){
                x = 0;
                y++;
            }
            if (y >= MAP_HEIGHT){
                LOGGER.info("Restarting at the start of the image");
                y = 0;
            }
        }

        MapPackageProto.MapPackage.Builder pack = MapPackageProto.MapPackage.newBuilder();
        pack.setError(MapErrorProto.MapError.newBuilder().setCode(MapErrorProto.MapError.ErrorCode.NONE).build());
        LOGGER.info("Adding bounding box to output");
        pack.setVersion(1);
        pack.setActiveX(left);
        pack.setActiveY(top);
        pack.setActiveW((right - left) + 1);
        pack.setActiveH((bottom - top) + 1);
        LOGGER.info("Adding all colors to output");
        for (MapPackageColorProto.MapPackageColor.Builder b : colorMap.values()){
            MapPackageColorProto.MapPackageColor col = b.build();
            LOGGER.fine("Adding color: " + col.toString());
            pack.addData(col);
        }
        LOGGER.info("Building output");
        return pack.build();
    }

    /**
     * Create a path message directly from a slam file.
     * @param slam The slam file to parse.
     * @return The message with the path.
     * @throws IOException If the file could not be read.
     */
    public static MapSlamProto.MapSlam directToPath(BufferedReader slam) throws IOException {
        return directToPath(slam, 0);
    }

    /**
     * Create a path message directly from a slam file.
     * @param slam The slam file to parse.
     * @param start The path point to start reading from.
     * @return The message with the path.
     * @throws IOException If the file could not be read.
     */
    public static MapSlamProto.MapSlam directToPath(BufferedReader slam, int start) throws IOException {
        LOGGER.info("Getting path from " + start);
        MapErrorProto.MapError.Builder err = MapErrorProto.MapError.newBuilder();
        MapSlamProto.MapSlam.Builder mapSlam = MapSlamProto.MapSlam.newBuilder();

        String line;
        boolean slamLocked = true;
        int pos = 0;
        float oldX = 100000;
        float oldY = 100000;
        float x;
        float y;
        while ((line = slam.readLine()) != null){
            LOGGER.fine("Parsing line: " + line);
            if (line.contains("reset")){
                LOGGER.fine("Reset");
                pos = 0;
                mapSlam.clearPoints();
                oldX = 100000;
                oldY = 100000;
            }
            if (line.contains("lock")) {
                LOGGER.fine("Lock");
                slamLocked = true;
            }
            if (line.contains("unlock")) {
                LOGGER.fine("Unlock");
                slamLocked = false;
            }
            if (slamLocked) continue;
            if (line.contains("estimate")){
                String[] split = line.split("\\s+");
                if (split.length != 5) {
                    LOGGER.info("Estimate of wrong length");
                    continue;
                }
                try {
                    x = Float.valueOf(split[2]) * (20.0f);
                    y = Float.valueOf(split[3]) * (-20.0f);
                    if ((Math.abs(x - oldX) > 1.0f) || (Math.abs(y - oldY) > 1.0f)){
                        oldX = x;
                        oldY = y;
                        if (pos < start) {
                            pos++;
                            continue;
                        }
                    } else {
                        continue;
                    }
                    MapSlamProto.MapSlam.Point.Builder point = MapSlamProto.MapSlam.Point.newBuilder();
                    point.setX(x);
                    point.setY(y);
                    mapSlam.addPoints(point.build());
                } catch (Exception e){
                    LOGGER.warning("Parsing coordinates failed: " + e);
                }

            }
        }
        if (pos < start) {
            LOGGER.warning("Path out of range");
            err.setCode(MapErrorProto.MapError.ErrorCode.SLAM_OUT_OF_RANGE);
            mapSlam.clearPoints();
        } else {
            err.setCode(MapErrorProto.MapError.ErrorCode.NONE);
        }
        mapSlam.setError(err.build());
        LOGGER.info("Building slam message");
        return mapSlam.build();
    }

    @SuppressWarnings("SameParameterValue")
    private static int toColorInt(int r, int g, int b, int a) {
        return (a & 0xff) << 24 | (r & 0xff) << 16 | (g & 0xff) << 8 | (b & 0xff);
    }
}
