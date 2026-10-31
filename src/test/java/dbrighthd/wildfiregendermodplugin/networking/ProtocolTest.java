package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacket;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV2;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV3;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV4;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV5;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.BreastOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GenderIdentities;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GeneralOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.ModConfiguration;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.PhysicsOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.UVDirection;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.UVLayout;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.UVLayouts;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.UVQuad;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtocolTest {
    @Test
    void parsesReleasedBeta4Fixture() throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(beta4Fixture())) {
            ModUser user = new ModSyncPacketV5().read(input);
            assertNotNull(user);
            assertEquals(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"), user.userId());
            assertEquals(GenderIdentities.OTHER, user.configuration().generalOptions().genderIdentity());
            assertEquals(0.75f, user.configuration().breastOptions().bustSize());
            assertTrue(user.configuration().generalOptions().hurtSounds());
            assertEquals(1.25f, user.configuration().generalOptions().voicePitch());
            assertTrue(user.configuration().physicsOptions().breastPhysics());
            assertFalse(user.configuration().generalOptions().showInArmor());
            assertFalse(user.configuration().physicsOptions().armorPhysics());
            assertEquals(0.8f, user.configuration().physicsOptions().buoyancy());
            assertEquals(0.9f, user.configuration().physicsOptions().floppiness());
            assertEquals(-0.1f, user.configuration().breastOptions().xOffset());
            assertEquals(0.2f, user.configuration().breastOptions().yOffset());
            assertEquals(0.3f, user.configuration().breastOptions().zOffset());
            assertTrue(user.configuration().breastOptions().uniBoob());
            assertEquals(0.4f, user.configuration().breastOptions().cleavage());
            assertNotNull(user.configuration().uvLayouts());

            Map<UVDirection, UVQuad> quads = user.configuration().uvLayouts().skin().left().getQuads();
            assertEquals(UVDirection.values().length, quads.size());
            int coordinate = 0;
            for (UVDirection direction : UVDirection.values()) {
                assertEquals(new UVQuad(coordinate, coordinate + 1, coordinate + 2, coordinate + 3),
                        quads.get(direction));
                coordinate += 4;
            }
            assertTrue(user.configuration().uvLayouts().skin().right().getQuads().isEmpty());
            assertTrue(user.configuration().uvLayouts().overlay().left().getQuads().isEmpty());
            assertTrue(user.configuration().uvLayouts().overlay().right().getQuads().isEmpty());
        }
    }

    @Test
    void roundTripsV5WithEveryUvDirection() throws IOException {
        ModUser original = testUser(UUID.randomUUID());
        ModUser decoded = roundTrip(new ModSyncPacketV5(), original);

        assertCoreConfiguration(original, decoded, true);
        Map<UVDirection, UVQuad> decodedQuads = decoded.configuration().uvLayouts().skin().left().getQuads();
        assertEquals(UVDirection.values().length, decodedQuads.size());
        for (UVDirection direction : UVDirection.values()) {
            assertEquals(original.configuration().uvLayouts().skin().left().getQuads().get(direction),
                    decodedQuads.get(direction));
        }
    }

    @Test
    void roundTripsImplementedLegacyProtocols() throws IOException {
        ModUser original = testUser(UUID.randomUUID());

        for (ModSyncPacket packet : List.of(new ModSyncPacketV2(), new ModSyncPacketV3(), new ModSyncPacketV4())) {
            ModUser decoded = roundTrip(packet, original);
            assertCoreConfiguration(original, decoded, packet.getVersion() >= 4);
            assertEquals(packet.getVersion() >= 4 ? original.configuration().generalOptions().voicePitch() : 0.0f,
                    decoded.configuration().generalOptions().voicePitch());
        }
    }

    @Test
    void parsesLegacyWireFixtures() throws IOException {
        UUID userId = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        ModUser expected = testUser(userId);

        ModUser v2 = read(new ModSyncPacketV2(), legacyV2Fixture());
        assertCoreConfiguration(expected, v2, false);
        assertEquals(0.0f, v2.configuration().generalOptions().voicePitch());

        ModUser v3 = read(new ModSyncPacketV3(), legacyV3Fixture());
        assertCoreConfiguration(expected, v3, false);
        assertEquals(0.0f, v3.configuration().generalOptions().voicePitch());

        ModUser v4 = read(new ModSyncPacketV4(), legacyV4Fixture());
        assertCoreConfiguration(expected, v4, true);
    }

    @Test
    void rejectsInvalidGenderOrdinal() {
        byte[] invalid = beta4Fixture();
        invalid[16] = 3;

        assertThrows(IOException.class, () -> readV5(invalid));
    }

    @Test
    void rejectsInvalidUvCount() {
        byte[] invalid = beta4Fixture();
        invalid[53] = 6;

        assertThrows(IOException.class, () -> readV5(invalid));
    }

    @Test
    void rejectsInvalidUvDirection() {
        byte[] invalid = beta4Fixture();
        invalid[54] = 5;

        assertThrows(IOException.class, () -> readV5(invalid));
    }

    @Test
    void rejectsDuplicateUvDirection() {
        byte[] invalid = beta4Fixture();
        invalid[59] = 0;

        assertThrows(IOException.class, () -> readV5(invalid));
    }

    @Test
    void rejectsTruncatedPayload() {
        byte[] fixture = beta4Fixture();
        byte[] truncated = Arrays.copyOf(fixture, fixture.length - 1);

        assertThrows(IOException.class, () -> readV5(truncated));
    }

    @Test
    void decodedUvMapsAreImmutable() throws IOException {
        ModUser decoded = readV5(beta4Fixture());
        Map<UVDirection, UVQuad> quads = decoded.configuration().uvLayouts().skin().left().getQuads();

        assertThrows(UnsupportedOperationException.class,
                () -> quads.put(UVDirection.NORTH, new UVQuad(1, 2, 3, 4)));
    }

    @Test
    void encodesV5HelloVersionAsVarInt() throws IOException {
        byte[] encoded;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(bytes)) {
            output.writeVarInt(NetworkManager.SYNC_HELLO_VERSION);
            encoded = bytes.toByteArray();
        }

        assertArrayEquals(new byte[]{1}, encoded);
        try (CraftInputStream input = CraftInputStream.ofBytes(encoded)) {
            assertEquals(NetworkManager.SYNC_HELLO_VERSION, input.readVarInt());
        }
    }

    public static ModUser testUser(UUID userId) {
        Map<UVDirection, UVQuad> quads = new EnumMap<>(UVDirection.class);
        int index = 0;
        for (UVDirection direction : UVDirection.values()) {
            quads.put(direction, new UVQuad(-index, index + 1, index + 2, index + 3));
            index += 4;
        }

        UVLayout layout = new UVLayout(quads);
        UVLayouts.Layer layer = new UVLayouts.Layer(layout, layout);
        UVLayouts uvLayouts = new UVLayouts(layer, layer);
        ModConfiguration config = new ModConfiguration(
                new GeneralOptions(GenderIdentities.OTHER, true, 1.2f, true),
                new PhysicsOptions(true, false, 0.8f, 0.9f),
                new BreastOptions(0.7f, -0.1f, 0.2f, 0.3f, true, 0.4f),
                uvLayouts);
        return new ModUser(userId, config);
    }

    static byte[] beta4Fixture() {
        return new byte[]{
                0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                (byte) 0x88, (byte) 0x99, (byte) 0xAA, (byte) 0xBB,
                (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF,
                0x02,
                0x3F, 0x40, 0x00, 0x00,
                0x01,
                0x3F, (byte) 0xA0, 0x00, 0x00,
                0x01,
                0x00,
                0x3F, 0x4C, (byte) 0xCC, (byte) 0xCD,
                0x3F, 0x66, 0x66, 0x66,
                (byte) 0xBD, (byte) 0xCC, (byte) 0xCC, (byte) 0xCD,
                0x3E, 0x4C, (byte) 0xCC, (byte) 0xCD,
                0x3E, (byte) 0x99, (byte) 0x99, (byte) 0x9A,
                0x01,
                0x3E, (byte) 0xCC, (byte) 0xCC, (byte) 0xCD,
                0x05,
                0x00, 0x00, 0x01, 0x02, 0x03,
                0x01, 0x04, 0x05, 0x06, 0x07,
                0x02, 0x08, 0x09, 0x0A, 0x0B,
                0x03, 0x0C, 0x0D, 0x0E, 0x0F,
                0x04, 0x10, 0x11, 0x12, 0x13,
                0x00, 0x00, 0x00
        };
    }

    private static byte[] legacyV2Fixture() {
        return HexFormat.of().parseHex(
                "00112233445566778899AABBCCDDEEFF" +
                        "02" +
                        "3F333333" +
                        "01" +
                        "01" +
                        "00" +
                        "01" +
                        "3F4CCCCD" +
                        "3F666666" +
                        "BDCCCCCD" +
                        "3E4CCCCD" +
                        "3E99999A" +
                        "01" +
                        "3ECCCCCD");
    }

    private static byte[] legacyV3Fixture() {
        return HexFormat.of().parseHex(
                "00112233445566778899AABBCCDDEEFF" +
                        "02" +
                        "3F333333" +
                        "01" +
                        "01" +
                        "01" +
                        "3F4CCCCD" +
                        "3F666666" +
                        "BDCCCCCD" +
                        "3E4CCCCD" +
                        "3E99999A" +
                        "01" +
                        "3ECCCCCD");
    }

    private static byte[] legacyV4Fixture() {
        return HexFormat.of().parseHex(
                "00112233445566778899AABBCCDDEEFF" +
                        "02" +
                        "3F333333" +
                        "01" +
                        "3F99999A" +
                        "01" +
                        "01" +
                        "3F4CCCCD" +
                        "3F666666" +
                        "BDCCCCCD" +
                        "3E4CCCCD" +
                        "3E99999A" +
                        "01" +
                        "3ECCCCCD");
    }

    private static ModUser roundTrip(ModSyncPacket packet, ModUser user) throws IOException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(bytes)) {
            packet.write(user, output);
            serialized = bytes.toByteArray();
        }

        return read(packet, serialized);
    }

    private static ModUser read(ModSyncPacket packet, byte[] data) throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            return packet.read(input);
        }
    }

    private static ModUser readV5(byte[] data) throws IOException {
        return read(new ModSyncPacketV5(), data);
    }

    private static void assertCoreConfiguration(ModUser expected, ModUser actual, boolean voicePitchPresent) {
        assertEquals(expected.userId(), actual.userId());
        assertEquals(expected.configuration().generalOptions().genderIdentity(),
                actual.configuration().generalOptions().genderIdentity());
        assertEquals(expected.configuration().generalOptions().hurtSounds(),
                actual.configuration().generalOptions().hurtSounds());
        assertEquals(expected.configuration().generalOptions().showInArmor(),
                actual.configuration().generalOptions().showInArmor());
        if (voicePitchPresent) {
            assertEquals(expected.configuration().generalOptions().voicePitch(),
                    actual.configuration().generalOptions().voicePitch());
        }
        assertEquals(expected.configuration().physicsOptions().breastPhysics(),
                actual.configuration().physicsOptions().breastPhysics());
        assertEquals(expected.configuration().physicsOptions().buoyancy(),
                actual.configuration().physicsOptions().buoyancy());
        assertEquals(expected.configuration().physicsOptions().floppiness(),
                actual.configuration().physicsOptions().floppiness());
        assertEquals(expected.configuration().breastOptions(), actual.configuration().breastOptions());
    }
}
