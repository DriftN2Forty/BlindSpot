package dev.driftn2forty.blindspot.proximity;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockExitDistanceTest {

    private static final double EPS = 1e-6;

    @Test
    void rayThroughBlockAlongPositiveX() {
        // ray starting at (4.5, 5.5, 10.5) heading +X, block at (5,5,10)
        Vector origin = new Vector(4.5, 5.5, 10.5);
        Vector dir = new Vector(1, 0, 0);
        double t = ProximityService.blockExitDistance(origin, dir, 5, 5, 10);
        // exit face at x=6, distance = 6 - 4.5 = 1.5
        assertEquals(1.5, t, EPS);
    }

    @Test
    void rayThroughBlockAlongNegativeX() {
        Vector origin = new Vector(6.5, 5.5, 10.5);
        Vector dir = new Vector(-1, 0, 0);
        double t = ProximityService.blockExitDistance(origin, dir, 5, 5, 10);
        // exit face at x=5, distance = 6.5 - 5 = 1.5
        assertEquals(1.5, t, EPS);
    }

    @Test
    void rayThroughBlockAlongPositiveZ() {
        Vector origin = new Vector(5.5, 5.5, 9.5);
        Vector dir = new Vector(0, 0, 1);
        double t = ProximityService.blockExitDistance(origin, dir, 5, 5, 10);
        // exit at z=11, distance = 11 - 9.5 = 1.5
        assertEquals(1.5, t, EPS);
    }

    @Test
    void rayThroughBlockAlongNegativeY() {
        Vector origin = new Vector(5.5, 7.0, 10.5);
        Vector dir = new Vector(0, -1, 0);
        double t = ProximityService.blockExitDistance(origin, dir, 5, 5, 10);
        // exit at y=5, distance = 7.0 - 5 = 2.0
        assertEquals(2.0, t, EPS);
    }

    @Test
    void diagonalRayExitsOnClosestFace() {
        // ray from (4.0, 5.5, 10.5) going (1, 0, 1)/sqrt(2) hits block (5,5,10)
        // exit faces: x=6 at t=(6-4)/0.707=2.828, z=11 at t=(11-10.5)/0.707=0.707
        // exits on z-face first
        Vector dir = new Vector(1, 0, 1).normalize();
        Vector origin = new Vector(4.0, 5.5, 10.5);
        double t = ProximityService.blockExitDistance(origin, dir, 5, 5, 10);
        double expected = (11 - 10.5) / dir.getZ();
        assertEquals(expected, t, EPS);
    }

    @Test
    void originOnEntryFaceExitsOnOpposite() {
        // origin exactly on the west face of block (5,5,10), going +X
        Vector origin = new Vector(5.0, 5.5, 10.5);
        Vector dir = new Vector(1, 0, 0);
        double t = ProximityService.blockExitDistance(origin, dir, 5, 5, 10);
        // exit at x=6, distance = 1.0
        assertEquals(1.0, t, EPS);
    }

    @Test
    void originInsideBlockExitsCorrectly() {
        // origin at center of block (5,5,10) heading +X
        Vector origin = new Vector(5.5, 5.5, 10.5);
        Vector dir = new Vector(1, 0, 0);
        double t = ProximityService.blockExitDistance(origin, dir, 5, 5, 10);
        // exit at x=6, distance = 0.5
        assertEquals(0.5, t, EPS);
    }

    @Test
    void returnsZeroWhenOriginBeyondBlock() {
        // origin already past the exit face
        Vector origin = new Vector(7.0, 5.5, 10.5);
        Vector dir = new Vector(1, 0, 0);
        double t = ProximityService.blockExitDistance(origin, dir, 5, 5, 10);
        // (6 - 7)/1 = -1, clamped to 0
        assertEquals(0.0, t, EPS);
    }

    @Test
    void rayAlignedWithAxisParallelToFace() {
        // ray going purely +Z, block at (5,5,10), origin x and y inside block
        Vector origin = new Vector(5.5, 5.5, 9.0);
        Vector dir = new Vector(0, 0, 1);
        double t = ProximityService.blockExitDistance(origin, dir, 5, 5, 10);
        // exit at z=11, distance = 2.0
        assertEquals(2.0, t, EPS);
    }

    @Test
    void steepDiagonalExitsOnYFace() {
        // ray going mostly upward through block at (0,0,0)
        Vector origin = new Vector(0.5, -0.5, 0.5);
        Vector dir = new Vector(0.1, 1, 0.1).normalize();
        double t = ProximityService.blockExitDistance(origin, dir, 0, 0, 0);
        // y-axis exit at y=1: ty = (1 - (-0.5)) / dir.getY()
        double ty = 1.5 / dir.getY();
        double tx = (1 - 0.5) / dir.getX();
        double tz = (1 - 0.5) / dir.getZ();
        double expected = Math.min(ty, Math.min(tx, tz));
        assertEquals(expected, t, EPS);
    }
}
