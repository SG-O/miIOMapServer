package de.sg_o.app.miioMapServerTest;

import de.sg_o.app.miioMapServer.Maps;
import de.sg_o.app.miioMapServer.VacuumMap;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;

import static org.junit.Assert.*;

public class MapsTest {
    private Maps s0;
    private Maps s1;
    private Maps s2;
    private VacuumMap m0;

    @Before
    public void setUp() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File currentMap = new File(Objects.requireNonNull(classLoader.getResource("run/shm/")).getFile());
        File oldMap = new File(Objects.requireNonNull(classLoader.getResource("mnt/data/rockrobo/rrlog")).getFile());
        File emptyDir = new File(Objects.requireNonNull(classLoader.getResource("run/")).getFile());
        s0 = new Maps(currentMap, oldMap, Level.ALL);
        s1 = new Maps(emptyDir, oldMap, Level.ALL);
        s2 = new Maps(currentMap, emptyDir, Level.ALL);

        File activeFileMap = new File(Objects.requireNonNull(classLoader.getResource("run/shm/navmap0.ppm")).getFile());
        File activeFileSlam = new File(Objects.requireNonNull(classLoader.getResource("run/shm/SLAM_fprintf.log")).getFile());

        BufferedReader map = new BufferedReader(new FileReader(activeFileMap));
        BufferedReader slam = new BufferedReader(new FileReader(activeFileSlam));

        m0 = new VacuumMap(map, slam, 1, null);
        map.close();
        slam.close();
    }

    @Test
    public void activeTest() {
        assertTrue(s0.hasActiveMap());
        assertEquals(m0,s0.getActiveMap());
        s0.updateActiveMap();
        assertEquals(m0,s0.getActiveMap());
        assertTrue(s0.updateActiveMapSlam());
        assertEquals(m0,s0.getActiveMap());
        assertFalse(s1.hasActiveMap());
        s1.updateActiveMap();
        assertFalse(s1.updateActiveMapSlam());
        assertFalse(s1.hasActiveMap());
        assertTrue(s2.hasActiveMap());
    }

    @Test
    public void previousTest() {
        assertEquals("de.sg_o.app.miioMapServer.VacuumMap{map=width:1024; height:1024, pathEntries=4598, boundingBox=[460, 409, 90, 118], overSample=1}", s0.getLastMap().toString());
    }

    @Test
    public void oldTest() {
        assertEquals(2, s0.numberOfPreviousMaps());
        assertTrue(s0.getPreviousMaps().contains("000143.20180604001001609_1387101062713_2018032100REL"));
        assertTrue(s0.getPreviousMaps().contains("000144.20180604034309095_1387101062713_2018032100REL"));
        assertEquals("de.sg_o.app.miioMapServer.VacuumMap{map=width:1024; height:1024, pathEntries=4831, boundingBox=[442, 450, 133, 117], overSample=1}", s0.getOldMap("000143.20180604001001609_1387101062713_2018032100REL").toString());
        assertEquals("de.sg_o.app.miioMapServer.VacuumMap{map=width:1024; height:1024, pathEntries=4598, boundingBox=[460, 409, 90, 118], overSample=1}", s0.getOldMap("000144.20180604034309095_1387101062713_2018032100REL").toString());
        s0.updatePreviousMaps();
        assertEquals(2, s0.numberOfPreviousMaps());
        assertEquals(2, s1.numberOfPreviousMaps());
        assertEquals(0, s2.numberOfPreviousMaps());
        s2.updatePreviousMaps();
        assertEquals(0, s2.numberOfPreviousMaps());
    }

    @Test
    public void failTest() {
        ClassLoader classLoader = getClass().getClassLoader();
        File currentMap = new File(Objects.requireNonNull(classLoader.getResource("run/shm/")).getFile());
        File oldMap = new File(Objects.requireNonNull(classLoader.getResource("mnt/data/rockrobo/rrlog")).getFile());
        File badDir = new File("/run/shm/notExisting");
        File activeFileMap = new File(Objects.requireNonNull(classLoader.getResource("run/shm/navmap0.ppm")).getFile());
        try {
            new Maps(null, oldMap, Level.ALL);
            fail();
        } catch (IOException e) {
            assertEquals("java.io.IOException", e.toString());
        }
        try {
            new Maps(currentMap, null, Level.ALL);
            fail();
        } catch (IOException e) {
            assertEquals("java.io.IOException", e.toString());
        }
        try {
            new Maps(badDir, oldMap, Level.ALL);
            fail();
        } catch (IOException e) {
            assertEquals("java.io.IOException", e.toString());
        }
        try {
            new Maps(currentMap, badDir, Level.ALL);
            fail();
        } catch (IOException e) {
            assertEquals("java.io.IOException", e.toString());
        }
        try {
            new Maps(activeFileMap, oldMap, Level.ALL);
            fail();
        } catch (IOException e) {
            assertEquals("java.io.IOException", e.toString());
        }
        try {
            new Maps(activeFileMap, badDir, Level.ALL);
            fail();
        } catch (IOException e) {
            assertEquals("java.io.IOException", e.toString());
        }
    }
}