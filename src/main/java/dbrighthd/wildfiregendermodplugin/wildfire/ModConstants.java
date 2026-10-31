package dbrighthd.wildfiregendermodplugin.wildfire;

/**
 * Utility class for interoperability with Wildfire's Female Gender Mod.
 *
 * @author winnpixie
 */
public final class ModConstants {
    /**
     * Legacy payload namespace for Wildfire's Female Gender Mod (< 5.0.0).
     */
    public static final String MOD_ID_LEGACY = "wildfire_gender";

    /**
     * Modern payload namespace for Female Gender Mod (>= 5.0.0).
     */
    public static final String MOD_ID_MODERN = "female_gender_mod";

    /**
     * Backward-compatibility alias.
     */
    public static final String MOD_ID = MOD_ID_LEGACY;

    /**
     * Payload channel used by legacy mod users to send their bodily configuration to the server.
     */
    public static final String SEND_GENDER_INFO = MOD_ID_LEGACY + ":send_gender_info";
    public static final String SERVERBOUND_SYNC_LEGACY = MOD_ID_LEGACY + ":serverbound/sync";

    /**
     * Payload channel used by modern mod users to send their bodily configuration to the server.
     */
    public static final String SERVERBOUND_SYNC_MODERN = MOD_ID_MODERN + ":serverbound/sync";

    /**
     * Functions similarly to {@link ModConstants#SEND_GENDER_INFO}, but used by Forge users.
     */
    public static final String FORGE = MOD_ID_LEGACY + ":main_channel";
    public static final String FORGE_MODERN = MOD_ID_MODERN + ":main_channel";

    /**
     * Payload channel used by the server to send mod users others' bodily configurations (legacy).
     */
    public static final String SYNC = MOD_ID_LEGACY + ":sync";
    public static final String CLIENTBOUND_SYNC_LEGACY = MOD_ID_LEGACY + ":clientbound/sync";

    /**
     * Payload channel used by the server to send mod users others' bodily configurations (modern).
     */
    public static final String CLIENTBOUND_SYNC_MODERN = MOD_ID_MODERN + ":clientbound/sync";
    public static final String SYNC_MODERN = MOD_ID_MODERN + ":sync";

    /**
     * V5 protocol hello sent from a Fabric/NeoForge client.
     */
    public static final String SERVERBOUND_HELLO = MOD_ID_LEGACY + ":serverbound/hello";
    public static final String SERVERBOUND_HELLO_MODERN = MOD_ID_MODERN + ":serverbound/hello";

    /**
     * V5 protocol hello response sent to a Fabric/NeoForge client.
     */
    public static final String CLIENTBOUND_HELLO = MOD_ID_LEGACY + ":clientbound/hello";
    public static final String CLIENTBOUND_HELLO_MODERN = MOD_ID_MODERN + ":clientbound/hello";

    private ModConstants() {
    }
}
