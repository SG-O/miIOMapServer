package de.sg_o.app.miioMapServerTest;

import de.sg_o.app.miioMapServer.Maps;
import de.sg_o.app.miioMapServer.VacuumMap;
import de.sg_o.proto.MapPackageProto;
import de.sg_o.proto.MapSlamProto;
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
    private MapPackageProto.MapPackage m0;
    private MapSlamProto.MapSlam sl0;

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

        m0 = VacuumMap.directToMapPackage(map);
        sl0 = VacuumMap.directToPath(slam);
        map.close();
        slam.close();
    }

    @Test
    public void activeTest() {
        assertTrue(s0.hasActiveMap());
        assertEquals(m0,s0.getActiveMap());
        assertEquals(sl0, s0.getActivePathFrom(0));
        s0.updateActiveMap();
        assertEquals(m0,s0.getActiveMap());
        assertEquals(sl0, s0.getActivePathFrom(0));
        assertFalse(s1.hasActiveMap());
        s1.updateActiveMap();
        assertFalse(s1.hasActiveMap());
        assertTrue(s2.hasActiveMap());
    }

    @Test
    public void previousTest() {
        assertEquals(118, s0.getLastMap().getActiveH());
        assertEquals(90, s0.getLastMap().getActiveW());
        assertEquals(1997, s0.getLastPath().getPointsCount());
    }

    @Test
    public void oldTest() {
        assertEquals(3, s0.numberOfPreviousMaps());
        assertTrue(s0.getPreviousMaps().contains("000142.20180712010502823_1387101062713_2018032100REL"));
        assertTrue(s0.getPreviousMaps().contains("000143.20180604001001609_1387101062713_2018032100REL"));
        assertTrue(s0.getPreviousMaps().contains("000144.20180604034309095_1387101062713_2018032100REL"));
        assertEquals(190, s0.getOldMap("000142.20180712010502823_1387101062713_2018032100REL").getActiveH());
        assertEquals(98, s0.getOldMap("000142.20180712010502823_1387101062713_2018032100REL").getActiveW());
        assertEquals(117, s0.getOldMap("000143.20180604001001609_1387101062713_2018032100REL").getActiveH());
        assertEquals(133, s0.getOldMap("000143.20180604001001609_1387101062713_2018032100REL").getActiveW());
        assertEquals(118, s0.getOldMap("000144.20180604034309095_1387101062713_2018032100REL").getActiveH());
        assertEquals(90, s0.getOldMap("000144.20180604034309095_1387101062713_2018032100REL").getActiveW());
        assertEquals(1743, s0.getOldPath("000143.20180604001001609_1387101062713_2018032100REL").getPointsCount());
        assertEquals(1997, s0.getOldPath("000144.20180604034309095_1387101062713_2018032100REL").getPointsCount());
        s0.updatePreviousMaps();
        assertEquals(3, s0.numberOfPreviousMaps());
        assertEquals(3, s1.numberOfPreviousMaps());
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