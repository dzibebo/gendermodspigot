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
import java.util.Map;
import java.util.UUID;

/**
 * Serializes mod data and routes it through each recipient's registered transport.
 *
 * @author winnpixie
 */
public class NetworkManager {
    public static final int SYNC_HELLO_VERSION = 1;

    private static final Map<Integer, ModSyncPacket> PACKET_FORMATS = Map.of(
            2, new ModSyncPacketV2(),
            3, new ModSyncPacketV3(),
            4, new ModSyncPacketV4(),
            5, new ModSyncPacketV5()
    );

    private final GenderModPlugin plugin;
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
        for (ModUser user : plugin.getUserManager().snapshot()) {
            sendEncoded(target, encodeUser(user));
        }
    }

    public void broadcast(Player source, ModUser user) {
        EncodedUser encoded = encodeUser(user);
        for (Player recipient : plugin.getServer().getOnlinePlayers()) {
            if (!recipient.getUniqueId().equals(source.getUniqueId())) {
                sendEncoded(recipient, encoded);
            }
        }
    }

    public ModUser deserializeUser(byte[] data, boolean forge) throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            if (forge && input.readUnsignedByte() != 1) {
                throw new IOException("Invalid Forge packet discriminator");
            }

            ModUser user = packetFormat.read(input);
            if (input.available() != 0) {
                throw new IOException("Unexpected trailing data in sync payload");
            }
            ModUserValidator.validate(user);
            return user;
        } catch (RuntimeException ex) {
            throw new IOException("Could not deserialize user (forge=" + forge + ")", ex);
        }
    }

    public HelloResult handleHello(Player target, byte[] data) throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            int clientVersion = input.readVarInt();
            if (input.available() != 0) {
                throw new IOException("Unexpected trailing data in sync hello");
            }

            try (ByteArrayOutputStream payload = new ByteArrayOutputStream();
                 CraftOutputStream output = new CraftOutputStream(payload)) {
                output.writeVarInt(SYNC_HELLO_VERSION);
                sendIfListening(target, ModConstants.CLIENTBOUND_HELLO, payload.toByteArray());
            }
            return new HelloResult(clientVersion, clientVersion == SYNC_HELLO_VERSION);
        } catch (RuntimeException ex) {
            throw new IOException("Could not handle sync hello", ex);
        }
    }

    private EncodedUser encodeUser(ModUser user) {
        return new EncodedUser(user.userId(), serializeUser(user, false), serializeUser(user, true));
    }

    private byte[] serializeUser(ModUser user, boolean forge) {
        try (ByteArrayOutputStream payload = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(payload)) {
            if (forge) output.writeByte(1);

            packetFormat.write(user, output);
            return payload.toByteArray();
        } catch (IOException | RuntimeException ex) {
            plugin.getCustomLogger().warning(ex, "Could not serialize user (forge=%s)", forge);
            return new byte[0];
        }
    }

    private void sendEncoded(Player target, EncodedUser user) {
        if (!target.isOnline() || target.getUniqueId().equals(user.userId())) return;

        String channel = plugin.getSyncStateCoordinator().preferredChannel(target);
        if (ModConstants.SYNC.equals(channel) && user.fabricData().length > 0) {
            target.sendPluginMessage(plugin, channel, user.fabricData());
        } else if (ModConstants.FORGE.equals(channel) && user.forgeData().length > 0) {
            target.sendPluginMessage(plugin, channel, user.forgeData());
        }
    }

    private void sendIfListening(Player target, String channel, byte[] data) {
        if (target.getListeningPluginChannels().contains(channel)) {
            target.sendPluginMessage(plugin, channel, data);
        }
    }

    private record EncodedUser(UUID userId, byte[] fabricData, byte[] forgeData) {
    }

    public record HelloResult(int clientVersion, boolean compatible) {
    }
}
