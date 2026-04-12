package dev.driftn2forty.blindspot.guard;

import dev.driftn2forty.blindspot.config.PluginConfig;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TpsGuardTest {

    private PluginConfig config;
    private Server server;

    @BeforeEach
    void setUp() {
        config = mock(PluginConfig.class);
        server = mock(Server.class);
    }

    @Test
    void allowsWhenGuardDisabled() {
        config.tpsGuardEnabled = false;
        TpsGuard guard = new TpsGuard(config);
        assertTrue(guard.allowHeavyWork());
    }

    @Test
    void allowsWhenTpsAboveThreshold() {
        config.tpsGuardEnabled = true;
        config.tpsGuardMin = 18.5;
        TpsGuard guard = new TpsGuard(config);

        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getServer).thenReturn(server);
            when(server.getTPS()).thenReturn(new double[]{19.8, 19.5, 19.0});
            assertTrue(guard.allowHeavyWork());
        }
    }

    @Test
    void deniesWhenTpsBelowThreshold() {
        config.tpsGuardEnabled = true;
        config.tpsGuardMin = 18.5;
        TpsGuard guard = new TpsGuard(config);

        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getServer).thenReturn(server);
            when(server.getTPS()).thenReturn(new double[]{15.0, 16.0, 17.0});
            assertFalse(guard.allowHeavyWork());
        }
    }

    @Test
    void allowsWhenTpsExactlyAtThreshold() {
        config.tpsGuardEnabled = true;
        config.tpsGuardMin = 18.5;
        TpsGuard guard = new TpsGuard(config);

        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getServer).thenReturn(server);
            when(server.getTPS()).thenReturn(new double[]{18.5});
            assertTrue(guard.allowHeavyWork());
        }
    }

    @Test
    void implementsTpsThrottleInterface() {
        TpsGuard guard = new TpsGuard(config);
        assertInstanceOf(TpsThrottle.class, guard);
    }
}
