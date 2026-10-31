package dbrighthd.wildfiregendermodplugin;

import dbrighthd.wildfiregendermodplugin.listeners.ConnectionListener;
import dbrighthd.wildfiregendermodplugin.listeners.ModPayloadListener;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.networking.InboundPacketGuard;
import dbrighthd.wildfiregendermodplugin.networking.NetworkManager;
import dbrighthd.wildfiregendermodplugin.networking.SyncStateCoordinator;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The entry-point for this plugin.
 *
 * @author dbrighthd
 */
public final class GenderModPlugin extends JavaPlugin {
    private final CustomPluginLogger customLogger = new CustomPluginLogger(this);
    private final UserManager userManager = new UserManager();
    private final NetworkManager networkManager = new NetworkManager(this);
    private final InboundPacketGuard inboundPacketGuard = new InboundPacketGuard();
    private final SyncStateCoordinator syncStateCoordinator = new SyncStateCoordinator(this);

    @Override
    public void onEnable() {
        customLogger.info("By @dbrighthd, with contributions from @stigstille and @winnpixie");

        saveDefaultConfig();

        if (!networkManager.init()) {
            customLogger.severe("INVALID PROTOCOL, DISABLING SELF.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerEventListeners();
        registerModListeners();
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        inboundPacketGuard.clear();
        syncStateCoordinator.clear();
        userManager.clear();
    }

    public CustomPluginLogger getCustomLogger() {
        return customLogger;
    }

    public UserManager getUserManager() {
        return userManager;
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public InboundPacketGuard getInboundPacketGuard() {
        return inboundPacketGuard;
    }

    public SyncStateCoordinator getSyncStateCoordinator() {
        return syncStateCoordinator;
    }

    private void registerEventListeners() {
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
    }

    private void registerModListeners() {
        ModPayloadListener payloadListener = new ModPayloadListener(this);

        // Fabric
        getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.SEND_GENDER_INFO, payloadListener);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.SYNC);

        // Forge
        getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.FORGE, payloadListener);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.FORGE);

        if (networkManager.supportsHello()) {
            getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.SERVERBOUND_HELLO, payloadListener);
            getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.CLIENTBOUND_HELLO);
        }
    }
}
