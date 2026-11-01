package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PaperConfigurationBridge {
    private final GenderModPlugin plugin;
    private final Set<UUID> pendingV5Users = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingV5Names = ConcurrentHashMap.newKeySet();
    private boolean enabled;

    private Method getConnectionMethod;
    private Method getProfileMethod;
    private Method getIdMethod;
    private Method getNameMethod;
    private Method addChannelMethod;
    private Method channelsMethod;
    private Method sendPluginMessageMethod;

    private Field listenerField;
    private Method parseIdentifierMethod;
    private Constructor<?> discardedPayloadConstructor;
    private Constructor<?> customPayloadPacketConstructor;
    private Method sendPacketMethod;
    private boolean nmsInitialized;

    public PaperConfigurationBridge(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public boolean register() {
        try {
            Class<?> eventClass;
            try {
                eventClass = Class.forName("io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent");
            } catch (ClassNotFoundException e) {
                eventClass = Class.forName("io.papermc.paper.event.connection.AsyncPlayerConnectionConfigureEvent");
            }
            getConnectionMethod = eventClass.getMethod("getConnection");

            Bukkit.getPluginManager().registerEvent(
                    (Class<? extends Event>) eventClass,
                    new Listener() {},
                    EventPriority.NORMAL,
                    (listener, event) -> onConfigure(event),
                    plugin
            );
            enabled = true;
            plugin.getCustomLogger().info("Paper configuration phase bridge enabled");
            return true;
        } catch (ClassNotFoundException ex) {
            plugin.getCustomLogger().debug("AsyncPlayerConnectionConfigureEvent not found, running without Paper config bridge");
            return false;
        } catch (Throwable ex) {
            plugin.getCustomLogger().warning(ex, "Failed to register Paper configuration phase bridge");
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean consumePendingV5(UUID uuid) {
        return consumePendingV5(uuid, null);
    }

    public boolean consumePendingV5(UUID uuid, String name) {
        boolean removedUuid = uuid != null && pendingV5Users.remove(uuid);
        boolean removedName = name != null && pendingV5Names.remove(name.toLowerCase(Locale.ROOT));
        return removedUuid || removedName;
    }

    public void remove(UUID uuid) {
        if (uuid != null) pendingV5Users.remove(uuid);
    }

    public void remove(UUID uuid, String name) {
        if (uuid != null) pendingV5Users.remove(uuid);
        if (name != null) pendingV5Names.remove(name.toLowerCase(Locale.ROOT));
    }

    public void clear() {
        pendingV5Users.clear();
        pendingV5Names.clear();
    }

    private void onConfigure(Event event) {
        try {
            Object conn = getConnectionMethod.invoke(event);
            if (conn == null) return;

            initConnectionMethods(conn.getClass());

            UUID playerId = null;
            String playerName = null;
            if (getProfileMethod != null) {
                Object profile = getProfileMethod.invoke(conn);
                if (profile != null) {
                    if (getIdMethod == null) {
                        try { getIdMethod = profile.getClass().getMethod("getId"); } catch (Throwable ignored) {}
                    }
                    if (getNameMethod == null) {
                        try { getNameMethod = profile.getClass().getMethod("getName"); } catch (Throwable ignored) {}
                    }
                    if (getIdMethod != null) {
                        playerId = (UUID) getIdMethod.invoke(profile);
                    }
                    if (getNameMethod != null) {
                        playerName = (String) getNameMethod.invoke(profile);
                    }
                }
            }

            byte[] helloPayload = NetworkManager.createHelloPayload();

            sendConfigurationHello(conn, ModConstants.CLIENTBOUND_HELLO_MODERN, helloPayload);
            sendConfigurationHello(conn, ModConstants.CLIENTBOUND_HELLO, helloPayload);

            if (playerId != null) {
                pendingV5Users.add(playerId);
            }
            if (playerName != null) {
                pendingV5Names.add(playerName.toLowerCase(Locale.ROOT));
            }
            plugin.getCustomLogger().debug("Sent configuration hello to %s (%s)", playerId, playerName);
        } catch (Throwable ex) {
            plugin.getCustomLogger().warning(ex, "Error handling player configuration event");
        }
    }

    private void initConnectionMethods(Class<?> connClass) {
        if (sendPluginMessageMethod != null) return;
        try {
            getProfileMethod = connClass.getMethod("getProfile");
        } catch (Throwable ignored) {}
        try {
            addChannelMethod = connClass.getMethod("addChannel", String.class);
        } catch (Throwable ignored) {}
        try {
            channelsMethod = connClass.getMethod("channels");
        } catch (Throwable ignored) {}
        try {
            sendPluginMessageMethod = connClass.getMethod("sendPluginMessage", Plugin.class, String.class, byte[].class);
        } catch (Throwable ignored) {}
    }

    private void sendConfigurationHello(Object conn, String channel, byte[] payload) {
        if (addChannelMethod != null) {
            try {
                addChannelMethod.invoke(conn, channel);
            } catch (Throwable ignored) {}
        }
        if (channelsMethod != null) {
            try {
                @SuppressWarnings("unchecked")
                Set<String> channelSet = (Set<String>) channelsMethod.invoke(conn);
                if (channelSet != null) {
                    channelSet.add(channel);
                    channelSet.add(ModConstants.CLIENTBOUND_SYNC_MODERN);
                    channelSet.add(ModConstants.SERVERBOUND_SYNC_MODERN);
                    channelSet.add(ModConstants.SYNC_MODERN);
                    channelSet.add(ModConstants.CLIENTBOUND_SYNC_LEGACY);
                    channelSet.add(ModConstants.SERVERBOUND_SYNC_LEGACY);
                    channelSet.add(ModConstants.SEND_GENDER_INFO);
                    channelSet.add(ModConstants.SYNC);
                }
            } catch (Throwable ignored) {}
        }

        boolean sent = false;
        if (sendPluginMessageMethod != null) {
            try {
                sendPluginMessageMethod.invoke(conn, plugin, channel, payload);
                sent = true;
            } catch (Throwable t) {
                plugin.getCustomLogger().debug("sendPluginMessage failed for %s: %s", channel, t.getMessage());
            }
        }

        if (!sent) {
            sendNmsPacket(conn, channel, payload);
        }
    }

    private void initNms(Object conn) {
        if (nmsInitialized) return;
        nmsInitialized = true;
        try {
            Class<?> clazz = conn.getClass();
            while (clazz != null && listenerField == null) {
                try {
                    listenerField = clazz.getDeclaredField("packetListener");
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (listenerField != null) {
                listenerField.setAccessible(true);
            }

            Class<?> identifierClass;
            try {
                identifierClass = Class.forName("net.minecraft.resources.Identifier");
            } catch (ClassNotFoundException e) {
                identifierClass = Class.forName("net.minecraft.resources.ResourceLocation");
            }
            try {
                parseIdentifierMethod = identifierClass.getMethod("parse", String.class);
            } catch (NoSuchMethodException e) {
                try {
                    parseIdentifierMethod = identifierClass.getMethod("tryParse", String.class);
                } catch (NoSuchMethodException e2) {
                    parseIdentifierMethod = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class);
                }
            }

            Class<?> discardedPayloadClass = Class.forName("net.minecraft.network.protocol.common.custom.DiscardedPayload");
            discardedPayloadConstructor = discardedPayloadClass.getConstructor(identifierClass, byte[].class);

            Class<?> customPayloadClass = Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket");
            customPayloadPacketConstructor = packetClass.getConstructor(customPayloadClass);

            Class<?> packetInterface = Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> listenerClass = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");
            sendPacketMethod = listenerClass.getMethod("send", packetInterface);
        } catch (Throwable t) {
            plugin.getCustomLogger().debug("Could not initialize NMS fallback: %s", t.getMessage());
        }
    }

    private void sendNmsPacket(Object conn, String channel, byte[] payload) {
        initNms(conn);
        if (listenerField == null || parseIdentifierMethod == null || discardedPayloadConstructor == null
                || customPayloadPacketConstructor == null || sendPacketMethod == null) {
            return;
        }
        try {
            Object packetListener = listenerField.get(conn);
            if (packetListener == null) return;

            Object id = parseIdentifierMethod.invoke(null, channel);
            Object discardedPayload = discardedPayloadConstructor.newInstance(id, payload);
            Object packet = customPayloadPacketConstructor.newInstance(discardedPayload);

            sendPacketMethod.invoke(packetListener, packet);
            plugin.getCustomLogger().debug("Sent NMS configuration packet for %s directly", channel);
        } catch (Throwable t) {
            plugin.getCustomLogger().debug("NMS packet fallback skipped for %s: %s", channel, t.getMessage());
        }
    }
}
