package dbrighthd.wildfiregendermodplugin;

import dbrighthd.wildfiregendermodplugin.listeners.ConnectionListener;
import dbrighthd.wildfiregendermodplugin.listeners.ModPayloadListener;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.networking.InboundPacketGuard;
import dbrighthd.wildfiregendermodplugin.networking.NetworkManager;
import dbrighthd.wildfiregendermodplugin.networking.PaperConfigurationBridge;
import dbrighthd.wildfiregendermodplugin.networking.ResyncTask;
import dbrighthd.wildfiregendermodplugin.networking.SyncStateCoordinator;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * The entry-point for this plugin.
 *
 * @author dbrighthd
 */
public final class GenderModPlugin extends JavaPlugin {
    private static final long RESYNC_CHECK_TICKS = 20L;

    private final CustomPluginLogger customLogger = new CustomPluginLogger(this);
    private UserManager userManager = new UserManager();
    private final NetworkManager networkManager = new NetworkManager(this);
    private final InboundPacketGuard inboundPacketGuard = new InboundPacketGuard();
    private final SyncStateCoordinator syncStateCoordinator = new SyncStateCoordinator(this);
    private PaperConfigurationBridge paperConfigurationBridge;

    @Override
    public void onEnable() {
        customLogger.info("By @dbrighthd, with contributions from @stigstille and @winnpixie");

        saveDefaultConfig();

        userManager = new UserManager(new File(getDataFolder(), "userdata"));

        if (!networkManager.init()) {
            customLogger.severe("INVALID PROTOCOL, DISABLING SELF.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        paperConfigurationBridge = new PaperConfigurationBridge(this);
        paperConfigurationBridge.register();

        registerEventListeners();
        registerModListeners();
        initializeOnlinePlayers();
        getServer().getScheduler().runTaskTimer(this, new ResyncTask(this), RESYNC_CHECK_TICKS, RESYNC_CHECK_TICKS);
    }

    private void initializeOnlinePlayers() {
        for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
            java.util.Set<String> channels = online.getListeningPluginChannels();
            boolean isV5 = paperConfigurationBridge != null && paperConfigurationBridge.isEnabled();
            if (isV5 || channels.contains(ModConstants.CLIENTBOUND_SYNC_MODERN) || channels.contains(ModConstants.SYNC_MODERN)) {
                syncStateCoordinator.onChannelRegistered(online, ModConstants.CLIENTBOUND_SYNC_MODERN);
                syncStateCoordinator.onHelloAccepted(online);
            }
            ModUser existing = userManager.getByPlayer(online);
            if (existing != null) {
                if (!existing.userId().equals(online.getUniqueId())) {
                    existing = new ModUser(online.getUniqueId(), existing.configuration());
                }
                userManager.put(existing, online.getName());
                networkManager.broadcast(online, existing);
            }
            networkManager.syncTo(online);
        }
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (paperConfigurationBridge != null) {
            paperConfigurationBridge.clear();
        }
        inboundPacketGuard.clear();
        syncStateCoordinator.clear();
        userManager.close();
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

    public PaperConfigurationBridge getPaperConfigurationBridge() {
        return paperConfigurationBridge;
    }

    private void registerEventListeners() {
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
    }

    private void registerModListeners() {
        ModPayloadListener payloadListener = new ModPayloadListener(this);

        getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.SERVERBOUND_SYNC_MODERN, payloadListener);
        getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.SYNC_MODERN, payloadListener);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.CLIENTBOUND_SYNC_MODERN);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.SYNC_MODERN);
        getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.FORGE_MODERN, payloadListener);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.FORGE_MODERN);

        getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.SEND_GENDER_INFO, payloadListener);
        getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.SERVERBOUND_SYNC_LEGACY, payloadListener);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.SYNC);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.CLIENTBOUND_SYNC_LEGACY);

        getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.FORGE, payloadListener);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.FORGE);

        if (networkManager.supportsHello()) {
            getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.SERVERBOUND_HELLO, payloadListener);
            getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.CLIENTBOUND_HELLO);
            getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.SERVERBOUND_HELLO_MODERN, payloadListener);
            getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.CLIENTBOUND_HELLO_MODERN);
        }
    }
}
