/*
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

package de.sg_o.app.miioMapServerTest;

import de.sg_o.app.miioMapServer.VacuumMap;
import de.sg_o.proto.MapErrorProto;
import de.sg_o.proto.MapPackageProto;
import de.sg_o.proto.MapSlamProto;
import org.junit.Before;
import org.junit.Test;

import java.awt.*;
import java.io.*;
import java.util.Objects;

import static org.junit.Assert.*;

public class VacuumMapTest {
    private VacuumMap m0;
    private VacuumMap m1;
    private VacuumMap m2;

    @Before
    public void setUp() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File fileMap = new File(Objects.requireNonNull(classLoader.getResource("run/shm/navmap0.ppm")).getFile());
        File fileSlam = new File(Objects.requireNonNull(classLoader.getResource("run/shm/SLAM_fprintf.log")).getFile());
        File fileBadSlam = new File(Objects.requireNonNull(classLoader.getResource("SLAM_fprintf_bad.log")).getFile());

        BufferedReader map = new BufferedReader(new FileReader(fileMap));
        BufferedReader slam = new BufferedReader(new FileReader(fileSlam));

        m0 = new VacuumMap(map, slam, 4, null);
        map.close();
        slam.close();
        map = new BufferedReader(new FileReader(fileMap));
        slam = new BufferedReader(new FileReader(fileBadSlam));
        m1 = new VacuumMap(map, slam, -1, null);
        map.close();
        slam.close();
        m2 = new VacuumMap(null, null, 2, null);
    }

    @Test
    public void getMapTest() {
        assertEquals(-8553091, m0.getMap().getRGB(0, 0));
        assertEquals(4096, m0.getMap().getHeight());
        assertEquals(4096, m0.getMap().getWidth());
        assertEquals(-8553091, m1.getMap().getRGB(0, 0));
        assertEquals(1024, m1.getMap().getHeight());
        assertEquals(1024, m1.getMap().getWidth());
        assertEquals(-16777216, m2.getMap().getRGB(0, 0));
        assertEquals(2048, m2.getMap().getHeight());
        assertEquals(2048, m2.getMap().getWidth());
    }

    @Test
    public void getPathTest() {
        assertEquals("Point2D.Float[2048.0, 2048.0]", m0.getPath().get(0).toString());
        assertEquals("Point2D.Float[2169.68, 2074.24]", m0.getPath().get(500).toString());
        assertEquals("Point2D.Float[512.0, 512.0]", m1.getPath().get(0).toString());
        assertEquals("Point2D.Float[543.36, 517.56]", m1.getPath().get(500).toString());
        assertEquals(0, m2.getPath().size());
    }

    @Test
    public void getBoundingBoxTest() {
        assertEquals(644, m0.getBoundingBox().height);
        assertEquals(492, m0.getBoundingBox().width);
        assertEquals(161, m1.getBoundingBox().height);
        assertEquals(123, m1.getBoundingBox().width);
        assertEquals(2048, m2.getBoundingBox().height);
        assertEquals(2048, m2.getBoundingBox().width);
    }

    @Test
    public void getOverSampleTest() {
        assertEquals(4, m0.getOverSample());
        assertEquals(1, m1.getOverSample());
        assertEquals(2, m2.getOverSample());

        assertEquals("Point2D.Float[2048.0, 2048.0]", m0.getPath().get(0).toString());
        assertEquals(644, m0.getBoundingBox().height);
        assertEquals(492, m0.getBoundingBox().width);
        assertEquals(644, m0.getMapWithPathInBounds().getHeight());
        assertEquals(492, m0.getMapWithPathInBounds().getWidth());
        assertEquals(-16776961, m0.getMapWithPath().getRGB(2048, 2048));
        m0.setOverSample(2);
        assertEquals("Point2D.Float[1024.0, 1024.0]", m0.getPath().get(0).toString());
        assertEquals(322, m0.getBoundingBox().height);
        assertEquals(246, m0.getBoundingBox().width);
        assertEquals(322, m0.getMapWithPathInBounds().getHeight());
        assertEquals(246, m0.getMapWithPathInBounds().getWidth());
        assertEquals(-16776961, m0.getMapWithPath().getRGB(1024, 1024));
        m0.setOverSample(-1);
        assertEquals("Point2D.Float[512.0, 512.0]", m0.getPath().get(0).toString());
        assertEquals(161, m0.getBoundingBox().height);
        assertEquals(123, m0.getBoundingBox().width);
        assertEquals(161, m0.getMapWithPathInBounds().getHeight());
        assertEquals(123, m0.getMapWithPathInBounds().getWidth());
        assertEquals(-16776961, m0.getMapWithPath().getRGB(512, 512));

    }

    @Test
    public void mapPointScaleTest() {
        Point p0 = new Point(200, 400);
        int[] b0 = m0.mapPointScale(p0);
        int[] b1 = m1.mapPointScale(p0);
        int[] b2 = m2.mapPointScale(p0);
        int[] b3 = m0.mapPointScale(null);

        assertEquals(50, b0[0]);
        assertEquals(100, b0[1]);

        assertEquals(200, b1[0]);
        assertEquals(400, b1[1]);

        assertEquals(100, b2[0]);
        assertEquals(200, b2[1]);

        assertEquals(0, b3[0]);
        assertEquals(0, b3[1]);
    }

    @Test
    public void mapRectangleScaleTest() {
        Rectangle r0 = new Rectangle(200, 400, 20, 40);
        int[] b0 = m0.mapRectangleScale(r0);
        int[] b1 = m1.mapRectangleScale(r0);
        int[] b2 = m2.mapRectangleScale(r0);
        int[] b3 = m0.mapRectangleScale(null);

        assertEquals(50, b0[0]);
        assertEquals(100, b0[1]);
        assertEquals(55, b0[2]);
        assertEquals(110, b0[3]);

        assertEquals(200, b1[0]);
        assertEquals(400, b1[1]);
        assertEquals(220, b1[2]);
        assertEquals(440, b1[3]);

        assertEquals(100, b2[0]);
        assertEquals(200, b2[1]);
        assertEquals(110, b2[2]);
        assertEquals(220, b2[3]);

        assertEquals(0, b3[0]);
        assertEquals(0, b3[1]);
        assertEquals(0, b3[2]);
        assertEquals(0, b3[3]);
    }

    @Test
    public void getMapWithPathInBoundsTest() {
        assertEquals(644, m0.getMapWithPathInBounds().getHeight());
        assertEquals(492, m0.getMapWithPathInBounds().getWidth());
        assertEquals(644, m0.getMapWithPathInBounds(Color.MAGENTA, Color.ORANGE).getHeight());
        assertEquals(492, m0.getMapWithPathInBounds(Color.MAGENTA, Color.ORANGE).getWidth());
        assertEquals(161, m1.getMapWithPathInBounds().getHeight());
        assertEquals(123, m1.getMapWithPathInBounds().getWidth());
        assertEquals(2048, m2.getMapWithPathInBounds().getHeight());
        assertEquals(2048, m2.getMapWithPathInBounds().getWidth());
    }

    @Test
    public void getMapWithPathTest() {
        assertEquals(-8553091, m0.getMapWithPath().getRGB(0, 0));
        assertEquals(4096, m0.getMapWithPath().getHeight());
        assertEquals(4096, m0.getMapWithPath().getWidth());
        assertEquals(-16776961, m0.getMapWithPath().getRGB(2048, 2048));
        assertEquals(-14336, m0.getMapWithPath(Color.MAGENTA, Color.ORANGE).getRGB(2048, 2048));
        assertEquals(-8553091, m1.getMapWithPath().getRGB(0, 0));
        assertEquals(1024, m1.getMapWithPath().getHeight());
        assertEquals(1024, m1.getMapWithPath().getWidth());
        assertEquals(-16777216, m2.getMapWithPath().getRGB(0, 0));
        assertEquals(2048, m2.getMapWithPath().getHeight());
        assertEquals(2048, m2.getMapWithPath().getWidth());
    }

    @Test
    public void serialisationTest() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(out);
        oos.writeObject(m0);
        oos.flush();
        out.flush();
        byte[] serialized = out.toByteArray();
        oos.close();
        out.close();
        ByteArrayInputStream in = new ByteArrayInputStream(serialized);
        ObjectInputStream ois = new ObjectInputStream(in);
        VacuumMap serial = (VacuumMap) ois.readObject();
        ois.close();
        in.close();
        assertEquals(m0, serial);
    }

    @SuppressWarnings({"SimplifiableJUnitAssertion", "ObjectEqualsNull", "ConstantConditions"})
    @Test
    public void equalsTest() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File fileMap = new File(Objects.requireNonNull(classLoader.getResource("run/shm/navmap0.ppm")).getFile());
        File fileSlam = new File(Objects.requireNonNull(classLoader.getResource("run/shm/SLAM_fprintf.log")).getFile());

        BufferedReader map = new BufferedReader(new FileReader(fileMap));
        BufferedReader slam = new BufferedReader(new FileReader(fileSlam));
        VacuumMap m3 = new VacuumMap(map, slam, 4, null);
        map.close();
        slam.close();

        map = new BufferedReader(new FileReader(fileMap));
        slam = new BufferedReader(new FileReader(fileSlam));
        VacuumMap m4 = new VacuumMap(map, slam, -1, null);
        map.close();
        slam.close();

        assertTrue(m0.equals(m3));
        assertFalse(m1.equals(m4));
        assertFalse(m0.equals(m1));
        assertFalse(m0.equals(m2));

        assertFalse(m0.equals(null));
        assertFalse(m0.equals(new Object()));
    }

    @Test
    public void hashCodeTest() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File fileMap = new File(Objects.requireNonNull(classLoader.getResource("run/shm/navmap0.ppm")).getFile());
        File fileSlam = new File(Objects.requireNonNull(classLoader.getResource("run/shm/SLAM_fprintf.log")).getFile());

        BufferedReader map = new BufferedReader(new FileReader(fileMap));
        BufferedReader slam = new BufferedReader(new FileReader(fileSlam));

        VacuumMap m3 = new VacuumMap(map, slam, 4, null);
        map.close();
        slam.close();

        assertEquals(m0.hashCode(), m3.hashCode());
        assertNotEquals(m0.hashCode(), m1.hashCode());
        assertNotEquals(m0.hashCode(), m2.hashCode());
    }

    @Test
    public void toStringTest() {
        assertEquals("de.sg_o.app.miioMapServer.VacuumMap{map=width:4096; height:4096, pathEntries=6040, boundingBox=java.awt.Rectangle[x=1768,y=1676,width=492,height=644], overSample=4}", m0.toString());
        assertEquals("de.sg_o.app.miioMapServer.VacuumMap{map=width:1024; height:1024, pathEntries=6039, boundingBox=java.awt.Rectangle[x=442,y=419,width=123,height=161], overSample=1}", m1.toString());
        assertEquals("de.sg_o.app.miioMapServer.VacuumMap{map=width:2048; height:2048, pathEntries=0, boundingBox=java.awt.Rectangle[x=0,y=0,width=2048,height=2048], overSample=2}", m2.toString());
    }

    @Test
    public void protobufTest() {
        MapPackageProto.MapPackage mp = m0.getMapPackage();
        MapSlamProto.MapSlam ms = m0.getMapPath();
        VacuumMap m3 = new VacuumMap(mp, ms, m0.getOverSample());
        assertEquals(m0, m3);
        assertEquals(6040, m3.getPathSize());
        assertEquals(m0.getPathSize(), m3.getPathSize());
        assertEquals(MapErrorProto.MapError.ErrorCode.SLAM_OUT_OF_RANGE, m0.getMapPath(m0.getPathSize()).getError().getCode());
        m3.appendMapSlam(m0.getMapPath(m0.getPathSize() - 1));
        assertEquals(6041, m3.getPathSize());
        VacuumMap m4 = new VacuumMap(null, null, 2);
        assertEquals(0, m4.getPathSize());
        assertEquals(-16777216, m4.getMap().getRGB(0,0));
    }

    @Test
    public void appendTest() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        assertEquals(6040, m0.getPathSize());
        m0.appendSlam(new BufferedReader(new FileReader(new File(Objects.requireNonNull(classLoader.getResource("SLAM_fprintf_bad.log")).getFile()))));
        assertEquals(6041, m0.getPathSize());
        m0.appendSlam(null);
        assertEquals(6041, m0.getPathSize());
        new BufferedReader(new StringReader(""));
        VacuumMap m4 = new VacuumMap(new BufferedReader(new StringReader("")), null, -1, null);
        VacuumMap m5 = new VacuumMap(new BufferedReader(new StringReader("\n")), null, -1, null);
        assertEquals(-16777216, m4.getMap().getRGB(0,0));
        assertEquals(-16777216, m5.getMap().getRGB(0,0));
    }
}