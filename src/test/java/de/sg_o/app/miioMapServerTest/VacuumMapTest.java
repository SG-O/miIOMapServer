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

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.*;

public class VacuumMapTest {
    private VacuumMap m0;
    private VacuumMap m1;
    private VacuumMap m2;

    private File fileMap;
    private File fileSlam;

    @Before
    public void setUp() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        fileMap = new File(Objects.requireNonNull(classLoader.getResource("run/shm/navmap0.ppm")).getFile());
        fileSlam = new File(Objects.requireNonNull(classLoader.getResource("run/shm/SLAM_fprintf.log")).getFile());
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
        assertEquals(-8553091, m0.getMap()[0]);
        assertEquals(-8553091, m1.getMap()[0]);
        assertEquals(0, m2.getMap()[0]);
    }

    @Test
    public void getPathTest() {
        assertEquals("[2048.0, 2048.0]", Arrays.toString(m0.getPath().get(0)));
        assertEquals("[2049.12, 2062.24]", Arrays.toString(m0.getPath().get(500)));
        assertEquals("[512.0, 512.0]", Arrays.toString(m1.getPath().get(0)));
        assertEquals("[512.28, 515.56]", Arrays.toString(m1.getPath().get(500)));
        assertEquals(0, m2.getPath().size());
    }

    @Test
    public void getBoundingBoxTest() {
        assertEquals(644, m0.getBoundingBox()[3]);
        assertEquals(492, m0.getBoundingBox()[2]);
        assertEquals(161, m1.getBoundingBox()[3]);
        assertEquals(123, m1.getBoundingBox()[2]);
        assertEquals(2048, m2.getBoundingBox()[3]);
        assertEquals(2048, m2.getBoundingBox()[2]);
    }

    @Test
    public void getOverSampleTest() {
        assertEquals(4, m0.getOverSample());
        assertEquals(1, m1.getOverSample());
        assertEquals(2, m2.getOverSample());

        assertEquals("[2048.0, 2048.0]", Arrays.toString(m0.getPath().get(0)));
        assertEquals(644, m0.getBoundingBox()[3]);
        assertEquals(492, m0.getBoundingBox()[2]);
        assertEquals(-8553091, m0.getMapWithPath()[0]);
        m0.setOverSample(2);
        assertEquals("[1024.0, 1024.0]", Arrays.toString(m0.getPath().get(0)));
        assertEquals(322, m0.getBoundingBox()[3]);
        assertEquals(246, m0.getBoundingBox()[2]);
        assertEquals(-8553091, m0.getMapWithPath()[0]);
        m0.setOverSample(-1);
        assertEquals("[512.0, 512.0]", Arrays.toString(m0.getPath().get(0)));
        assertEquals(161, m0.getBoundingBox()[3]);
        assertEquals(123, m0.getBoundingBox()[2]);
        assertEquals(-8553091, m0.getMapWithPath()[0]);

    }

    @Test
    public void mapPointScaleTest() {
        int[] p0 = new int[]{200, 400};
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
        int[] r0 = new int[]{200, 400, 20, 40};
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
        assertEquals(316848, m0.getMapWithPathInBounds().length);
        assertEquals(316848, m0.getMapWithPathInBounds(VacuumMap.RED, VacuumMap.GREEN).length);
        assertEquals(19803, m1.getMapWithPathInBounds().length);
        assertEquals(4194304, m2.getMapWithPathInBounds().length);
    }

    @Test
    public void getMapWithPathTest() {
        assertEquals(-8553091, m0.getMapWithPath()[0]);
        assertEquals(16777216, m0.getMapWithPath().length);
        assertEquals(-8553091, m0.getMapWithPath()[0]);
        assertEquals(-8553091, m0.getMapWithPath(VacuumMap.RED, VacuumMap.GREEN)[0]);
        assertEquals(-8553091, m1.getMapWithPath()[0]);
        assertEquals(1048576, m1.getMapWithPath().length);
        assertEquals(0, m2.getMapWithPath()[0]);
        assertEquals(4194304, m2.getMapWithPath().length);
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
        assertTrue(m1.equals(m4));
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
        assertEquals("de.sg_o.app.miioMapServer.VacuumMap{map=width:4096; height:4096, pathEntries=2419, boundingBox=[1768, 1676, 492, 644], overSample=4}", m0.toString());
        assertEquals("de.sg_o.app.miioMapServer.VacuumMap{map=width:1024; height:1024, pathEntries=2419, boundingBox=[442, 419, 123, 161], overSample=1}", m1.toString());
        assertEquals("de.sg_o.app.miioMapServer.VacuumMap{map=width:2048; height:2048, pathEntries=0, boundingBox=[0, 0, 2048, 2048], overSample=2}", m2.toString());
    }

    @Test
    public void protobufTest() throws IOException {
        MapPackageProto.MapPackage mp0 = m0.getMapPackage();
        MapSlamProto.MapSlam ms0 = m0.getMapPath();
        VacuumMap m3 = new VacuumMap(mp0, ms0, m0.getOverSample());
        assertEquals(m0, m3);
        assertEquals(2419, m3.getPathSize());
        assertEquals(m0.getPathSize(), m3.getPathSize());
        assertEquals(MapErrorProto.MapError.ErrorCode.SLAM_OUT_OF_RANGE, m0.getMapPath(m0.getPathSize()).getError().getCode());
        m3.appendMapSlam(m0.getMapPath(m0.getPathSize() - 1));
        assertEquals(2420, m3.getPathSize());
        VacuumMap m4 = new VacuumMap(null, null, 2);
        assertEquals(0, m4.getPathSize());
        assertEquals(0, m4.getMap()[0]);

        BufferedReader map = new BufferedReader(new FileReader(fileMap));
        BufferedReader slam = new BufferedReader(new FileReader(fileSlam));
        MapPackageProto.MapPackage mp1 = VacuumMap.directToMapPackage(map);
        MapSlamProto.MapSlam ms1 = VacuumMap.directToPath(slam);
        VacuumMap m5 = new VacuumMap(mp1, ms1, m0.getOverSample());
        assertArrayEquals(m0.getMap(), m5.getMap());
        List<float[]> slm0 = m0.getPath();
        List<float[]> slm1 = m5.getPath();
        assertEquals(slm0.size(), slm1.size());
        for (int i = 0; i < slm0.size(); i++) {
            assertArrayEquals(slm0.get(i), slm1.get(i), 0.001f);
        }
        map.close();
        slam.close();


        slam = new BufferedReader(new FileReader(fileSlam));
        MapSlamProto.MapSlam ms2 = VacuumMap.directToPath(slam, 1000);
        assertEquals(1419, ms2.getPointsCount());
        slam.close();

        slam = new BufferedReader(new FileReader(fileSlam));
        MapSlamProto.MapSlam ms3 = VacuumMap.directToPath(slam, 5000);
        assertEquals(MapErrorProto.MapError.ErrorCode.SLAM_OUT_OF_RANGE, ms3.getError().getCode());
        slam.close();
    }

    @Test
    public void appendTest() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        assertEquals(2419, m0.getPathSize());
        m0.appendSlam(new BufferedReader(new FileReader(new File(Objects.requireNonNull(classLoader.getResource("SLAM_fprintf_bad.log")).getFile()))));
        assertEquals(2420, m0.getPathSize());
        m0.appendSlam(null);
        assertEquals(2420, m0.getPathSize());
        new BufferedReader(new StringReader(""));
        VacuumMap m4 = new VacuumMap(new BufferedReader(new StringReader("")), null, -1, null);
        VacuumMap m5 = new VacuumMap(new BufferedReader(new StringReader("\n")), null, -1, null);
        assertEquals(0, m4.getMap()[0]);
        assertEquals(0, m5.getMap()[0]);
    }
}