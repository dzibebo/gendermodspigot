package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacket;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV2;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV3;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV4;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV5;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Serializes mod data and routes it through each recipient's registered transport.
 *
 * @author winnpixie
 */
public class NetworkManager {
    public static final int SYNC_HELLO_VERSION = 2;
    private static final byte[] HELLO_PAYLOAD = new byte[]{2, 1, 2};

    private static final Map<Integer, ModSyncPacket> PACKET_FORMATS = Map.of(
            2, new ModSyncPacketV2(),
            3, new ModSyncPacketV3(),
            4, new ModSyncPacketV4(),
            5, new ModSyncPacketV5()
    );

    private static final int PAYLOAD_CACHE_LIMIT = 1024;

    private final GenderModPlugin plugin;
    private final Map<PayloadKey, byte[]> payloadCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<PayloadKey, byte[]> eldest) {
                    return size() > PAYLOAD_CACHE_LIMIT;
                }
            });
    private ModSyncPacket packetFormat;

    public NetworkManager(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        int protocolVersion = plugin.getConfig().getInt("mod.protocol", -1);
        packetFormat = protocolVersion == -1 ? PACKET_FORMATS.get(5) : PACKET_FORMATS.get(protocolVersion);
        if (packetFormat == null) return false;

        plugin.getCustomLogger().info("Using protocol %d for mod version(s) %s",
                packetFormat.getVersion(), packetFormat.getModRange());
        return true;
    }

    public boolean supportsHello() {
        return packetFormat != null && packetFormat.getVersion() >= 5;
    }

    public void syncTo(Player target) {
        if (!target.isOnline()) return;

        String channel = plugin.getSyncStateCoordinator().preferredChannel(target);
        if (channel == null) return;

        boolean isModern = ModConstants.CLIENTBOUND_SYNC_MODERN.equals(channel)
                || ModConstants.SYNC_MODERN.equals(channel)
                || ModConstants.FORGE_MODERN.equals(channel);

        boolean isForge = ModConstants.FORGE.equals(channel) || ModConstants.FORGE_MODERN.equals(channel);

        ModSyncPacket format = isModern ? PACKET_FORMATS.get(5) : PACKET_FORMATS.get(4);
        if (format == null) format = packetFormat;

        for (ModUser user : plugin.getUserManager().snapshot()) {
            if (target.getUniqueId().equals(user.userId())) continue;
            byte[] data = serializedUser(user, isForge, format);
            if (data.length > 0) {
                target.sendPluginMessage(plugin, channel, data);
            }
        }

        plugin.getSyncStateCoordinator().markSynced(target);
    }

    public void broadcast(Player source, ModUser user) {
        for (Player recipient : plugin.getServer().getOnlinePlayers()) {
            if (!recipient.getUniqueId().equals(source.getUniqueId())) {
                sendEncoded(recipient, user);
            }
        }
    }

    public ModUser deserializeUser(byte[] data, boolean forge) throws IOException {
        return deserializeUser(null, data, forge);
    }

    public ModUser deserializeUser(Player player, byte[] data, boolean forge) throws IOException {
        try {
            return tryDeserialize(player, data, forge, packetFormat);
        } catch (IOException primaryEx) {
            for (ModSyncPacket format : PACKET_FORMATS.values()) {
                if (format == packetFormat) continue;
                try {
                    return tryDeserialize(player, data, forge, format);
                } catch (IOException ignored) {}
            }
            throw primaryEx;
        }
    }

    private ModUser tryDeserialize(Player player, byte[] data, boolean forge, ModSyncPacket format) throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            if (forge && input.readUnsignedByte() != 1) {
                throw new IOException("Invalid Forge packet discriminator");
            }

            UUID fallbackId = player != null ? player.getUniqueId() : null;
            ModUser user = format.read(input, fallbackId);
            if (input.available() != 0) {
                throw new IOException("Unexpected trailing data in sync payload");
            }
            ModUserValidator.validate(user);
            return user;
        } catch (RuntimeException ex) {
            throw new IOException("Could not deserialize user with format " + format.getVersion(), ex);
        }
    }

    public HelloResult handleHello(Player target, byte[] data) throws IOException {
        return handleHello(target, ModConstants.SERVERBOUND_HELLO, data);
    }

    public HelloResult handleHello(Player target, String channel, byte[] data) throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            int clientVersion = input.readVarInt();
            if (input.available() != 0) {
                throw new IOException("Unexpected trailing data in sync hello");
            }

            boolean compatible = clientVersion == 1 || clientVersion == SYNC_HELLO_VERSION;
            if (compatible && target != null) {
                String responseChannel = ModConstants.SERVERBOUND_HELLO_MODERN.equals(channel)
                        ? ModConstants.CLIENTBOUND_HELLO_MODERN
                        : ModConstants.CLIENTBOUND_HELLO;
                target.sendPluginMessage(plugin, responseChannel, createHelloPayload());
            }
            return new HelloResult(clientVersion, compatible);
        } catch (RuntimeException ex) {
            throw new IOException("Could not handle sync hello", ex);
        }
    }

    public static byte[] createHelloPayload() {
        return HELLO_PAYLOAD.clone();
    }

    private byte[] serializeUser(ModUser user, boolean forge, ModSyncPacket format) {
        try (ByteArrayOutputStream payload = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(payload)) {
            if (forge) output.writeByte(1);

            format.write(user, output);
            return payload.toByteArray();
        } catch (IOException | RuntimeException ex) {
            plugin.getCustomLogger().warning(ex, "Could not serialize user (forge=%s)", forge);
            return new byte[0];
        }
    }

    private byte[] serializedUser(ModUser user, boolean forge, ModSyncPacket format) {
        PayloadKey key = new PayloadKey(user, format.getVersion(), forge);
        byte[] cached = payloadCache.get(key);
        if (cached != null) return cached;

        byte[] data = serializeUser(user, forge, format);
        if (data.length > 0) {
            payloadCache.put(key, data);
        }
        return data;
    }

    private void sendEncoded(Player target, ModUser user) {
        if (!target.isOnline() || target.getUniqueId().equals(user.userId())) return;

        String channel = plugin.getSyncStateCoordinator().preferredChannel(target);
        if (channel == null) return;

        boolean isModern = ModConstants.CLIENTBOUND_SYNC_MODERN.equals(channel)
                || ModConstants.SYNC_MODERN.equals(channel)
                || ModConstants.FORGE_MODERN.equals(channel);

        boolean isForge = ModConstants.FORGE.equals(channel) || ModConstants.FORGE_MODERN.equals(channel);

        ModSyncPacket packetCodec = isModern ? PACKET_FORMATS.get(5) : PACKET_FORMATS.get(4);
        final ModSyncPacket resolvedFormat = packetCodec != null ? packetCodec : packetFormat;

        byte[] data = serializedUser(user, isForge, resolvedFormat);
        if (data.length > 0) {
            target.sendPluginMessage(plugin, channel, data);
        }
    }

    public record HelloResult(int clientVersion, boolean compatible) {
    }

    private record PayloadKey(ModUser user, int version, boolean forge) {
    }
}
