package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperConfigurationBridgeTest {
    @Test
    void tracksAndConsumesPendingV5Users() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        CustomPluginLogger logger = mock(CustomPluginLogger.class);
        when(plugin.getCustomLogger()).thenReturn(logger);

        PaperConfigurationBridge bridge = new PaperConfigurationBridge(plugin);
        UUID uuid = UUID.randomUUID();

        assertFalse(bridge.consumePendingV5(uuid));

        bridge.remove(uuid);
        assertFalse(bridge.consumePendingV5(uuid));

        bridge.clear();
        assertFalse(bridge.consumePendingV5(uuid));
        assertFalse(bridge.consumePendingV5(null));
    }
}

