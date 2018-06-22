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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
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
    private final static Logger LOGGER = Logger.getLogger(ServerThread.class.getName());

    private transient BufferedImage map;
    private transient List<Point2D.Float> path = new LinkedList<>();
    private Rectangle boundingBox;
    private int overSample;
    private int numberOfSlamLines = 0;
    private boolean slamLocked = true;

    /**
     * Create a vacuum map object.
     * @param image The reader the map image should be read from.
     * @param slam The reader the slam log should be read from.
     * @param overSample The oversampling that should be applied to the map.
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
        this.map = new BufferedImage(1024, 1024, BufferedImage.TYPE_3BYTE_BGR);
        LOGGER.fine("Creating maximum bounding box");
        this.boundingBox = new Rectangle(1024, 1024);
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
        this.map = new BufferedImage(1024, 1024, BufferedImage.TYPE_3BYTE_BGR);
        this.boundingBox = new Rectangle(1024, 1024);
        decodeMapPackage(image);
        decodeMapSlam(slam);
    }

    private void readMap(BufferedReader image) throws IOException {
        LOGGER.fine("Initializing bounding box creation");
        int x = 0;
        int y = 0;
        int top = map.getHeight();
        int bottom = 0;
        int left = map.getWidth();
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
                boundingBox = new Rectangle(left, top, (right - left) + 1, (bottom - top) + 1);
                return;
            }
            for (int i = 0; i < rgb.length; i++) {
                rgb[i] = rgb[i] & 0xFF;
            }
            LOGGER.fine("Setting pixel");
            map.setRGB(x, y, new Color(rgb[0], rgb[1], rgb[2]).getRGB());
            if (rgb[0] != 125 || rgb[1] != 125 || rgb[2] != 125){
                LOGGER.fine("Updating bounding box");
                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                if (y > bottom) bottom = y;
            }
            x++;
            if (x >= map.getWidth()){
                x = 0;
                y++;
            }
            if (y >= map.getHeight()){
                LOGGER.info("Restarting at the start of the image");
                y = 0;
            }
        }
    }

    private void readSlam(BufferedReader slam) throws IOException {
        String line;
        while ((line = slam.readLine()) != null){
            LOGGER.fine("Parsing line: " + line);
            numberOfSlamLines++;
            if (line.contains("reset")){
                LOGGER.fine("Reset");
                path = new LinkedList<>();
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
                    LOGGER.fine("Parsing coordinates");
                    x = Float.valueOf(split[2]) * (20.0f);
                    y = Float.valueOf(split[3]) * (-20.0f);
                    path.add(new Point2D.Float(x, y));
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
    public synchronized BufferedImage getMap() {
        BufferedImage outMap = new BufferedImage(1024 * overSample, 1024 * overSample, BufferedImage.TYPE_3BYTE_BGR);
        AffineTransform at = new AffineTransform();
        at.scale(overSample, overSample);
        AffineTransformOp op = new AffineTransformOp(at, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        op.filter(map, outMap);
        return outMap;
    }

    /**
     * @return The path the vacuum took.
     */
    public synchronized List<Point2D.Float> getPath() {
        List<Point2D.Float> outPath = new LinkedList<>();
        for (Point2D.Float p : path){
            outPath.add(new Point2D.Float((p.x + (map.getWidth() / 2.0f)) * overSample, (p.y + (map.getHeight() / 2.0f)) * overSample));
        }
        return outPath;
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
    public synchronized Rectangle getBoundingBox() {
        Rectangle tmp = new Rectangle();
        tmp.x = boundingBox.x * overSample;
        tmp.y = boundingBox.y * overSample;
        tmp.width = boundingBox.width * overSample;
        tmp.height = boundingBox.height * overSample;
        return tmp;
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
    public synchronized int[] mapPointScale(Point p) {
        if (p == null) p = new Point(0,0);
        int[] scaled = new int[2];
        scaled[0] = p.x / overSample;
        scaled[1] = p.y / overSample;
        return scaled;
    }

    /**
     * Get coordinates from a rectangle in this map. They can be used to define the area of the area cleanup.
     * @param rec The rectangle to convert.
     * @return An array of coordinates (x0, y0, x1, y1).
     */
    public synchronized int[] mapRectangleScale(Rectangle rec) {
        if (rec == null) rec = new Rectangle(0,0,0,0);
        int[] scaled = new int[4];
        scaled[0] = rec.x / overSample;
        scaled[1] = rec.y / overSample;
        scaled[2] = (rec.x / overSample) + (rec.width / overSample);
        scaled[3] = (rec.y / overSample) + (rec.height / overSample);
        return scaled;
    }

    /**
     * @return The map with the path drawn into it within the bounding box. Using the color green for the start point and blue for the path.
     */
    public BufferedImage getMapWithPathInBounds(){
        return getMapWithPathInBounds(null, null);
    }

    /**
     * @return The map with the path drawn into it within the bounding box.
     * @param startColor The color the start point should be drawn with. If null is provided this will fall back to green.
     * @param pathColor The color the path should be drawn with. If null is provided this will fall back to blue.
     */
    public BufferedImage getMapWithPathInBounds(Color startColor, Color pathColor){
        BufferedImage raw = getMapWithPath(startColor, pathColor);
        Rectangle tmp = getBoundingBox();
        return raw.getSubimage(tmp.x, tmp.y, tmp.width, tmp.height);
    }

    /**
     * @return The map with the path drawn into it. Using the color green for the start point and blue for the path.
     */
    public BufferedImage getMapWithPath(){
        return getMapWithPath(null, null);
    }

    /**
     * @return The map with the path drawn into it.
     * @param startColor The color the start point should be drawn with. If null is provided this will fall back to green.
     * @param pathColor The color the path should be drawn with. If null is provided this will fall back to blue.
     */
    public synchronized BufferedImage getMapWithPath(Color startColor, Color pathColor) {
        if (startColor == null) startColor = Color.GREEN;
        if (pathColor == null) pathColor = Color.BLUE;
        BufferedImage pathMap = new BufferedImage(1024 * overSample, 1024 * overSample, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D img = pathMap.createGraphics();
        AffineTransform at = new AffineTransform();
        at.scale(overSample, overSample);
        AffineTransformOp op = new AffineTransformOp(at, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        img.drawImage(map, op,0, 0);
        img.setBackground(startColor);
        img.setColor(startColor);
        int[] home = {(pathMap.getWidth() / 2) - (3 * overSample), (pathMap.getHeight() / 2) - (3 * overSample)};
        img.fillOval(home[0], home[1], 6 * overSample,6 * overSample);
        img.setColor(pathColor);
        BasicStroke bs = new BasicStroke(1);
        img.setStroke(bs);
        Point2D.Float prev = null;
        for (Point2D.Float p : getPath()){
            if (prev == null) {
                prev = p;
                continue;
            }
            img.drawLine((int)prev.x, (int)prev.y, (int)p.x, (int)p.y);
            prev = p;
        }
        return pathMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VacuumMap vacuumMap = (VacuumMap) o;
        if (map.getWidth() != vacuumMap.map.getWidth() || map.getHeight() != vacuumMap.map.getHeight()) return false;
        int w = map.getWidth();
        int h = map.getHeight();
        for (int j = 0; j < h; j++){
            for (int i = 0; i < w; i++){
                if (map.getRGB(i, j) != vacuumMap.map.getRGB(i, j)) return false;
            }
        }
        boolean ret;
        synchronized(this) {
            ret =  overSample == vacuumMap.overSample &&
                    Objects.equals(path, vacuumMap.path) &&
                    Objects.equals(boundingBox, vacuumMap.boundingBox);
        }
        return ret;
    }

    @Override
    public int hashCode() {
        int ret;
        synchronized(this) {
            ret = Objects.hash(map.getHeight(), map.getWidth(), path, boundingBox, overSample);
        }
        return ret;
    }

    @Override
    public String toString() {
        String ret;
        synchronized(this) {
            ret = "de.sg_o.app.miioMapServer.VacuumMap{" +
                    "map=width:" + map.getWidth() * overSample + "; height:" + map.getHeight() * overSample +
                    ", pathEntries=" + path.size() +
                    ", boundingBox=" + getBoundingBox() +
                    ", overSample=" + overSample +
                    '}';
        }
        return ret;
    }

    /**
     * Get the complete map image as a proto message.
     * @return The complete map image as a proto message.
     */
    public synchronized MapPackageProto.MapPackage getMapPackage(){
        LOGGER.info("Getting the map within bounds");
        BufferedImage mapInBounds = map.getSubimage(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
        HashMap<Integer, MapPackageColorProto.MapPackageColor.Builder> colorMap = new HashMap<>();
        LOGGER.info("Creating all colors");
        for (int j = 0; j < mapInBounds.getHeight(); j++) {
            for (int i = 0; i < mapInBounds.getWidth(); i++) {
                LOGGER.fine("Getting color for pixel: " + i + "," + j);
                int color = mapInBounds.getRGB(i, j);
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
        pack.setActiveX(boundingBox.x);
        pack.setActiveY(boundingBox.y);
        pack.setActiveW(boundingBox.width);
        pack.setActiveH(boundingBox.height);
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
        boundingBox.x = image.getActiveX();
        boundingBox.y = image.getActiveY();
        boundingBox.width = image.getActiveW();
        boundingBox.height = image.getActiveH();
        synchronized(this) {
            Graphics2D gr = map.createGraphics();
            gr.setColor(new Color(125, 125, 125));
            gr.fillRect(0, 0, map.getWidth(), map.getHeight());
            for (MapPackageColorProto.MapPackageColor c : image.getDataList()) {
                int color = c.getColor();
                for (int pos : c.getCoordinatesList()) {
                    map.setRGB(boundingBox.x + (pos & 0xFF), boundingBox.y + ((pos >> 16) & 0xFF), color);
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
                Point2D.Float p = path.get(i);
                point.setX(p.x);
                point.setY(p.y);
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
                path.add(new Point2D.Float(p.getX(), p.getY()));
            }
        }
    }

    private byte[] mapToBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        synchronized(this) {
            ImageIO.write(this.map, "png", baos);
        }
        baos.flush();
        byte[] imgInByte = baos.toByteArray();
        baos.close();
        return imgInByte;
    }

    private void bytesToMap(byte[] source) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(source);
        synchronized(this) {
            this.map = ImageIO.read(bais);
            if (this.map == null) {
                this.map = new BufferedImage(1024, 1024, BufferedImage.TYPE_3BYTE_BGR);
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
                this.path = (List<Point2D.Float>) o;
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
}
