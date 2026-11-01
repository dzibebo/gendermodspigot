package dbrighthd.wildfiregendermodplugin.networking.wildfire;

import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.*;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class ModSyncPacketV5 implements ModSyncPacket {
    @Override
    public int getVersion() {
        return 5;
    }

    @Override
    public String getModRange() {
        return "5.0.0+";
    }

    @Override
    public ModUser read(CraftInputStream input) throws IOException {
        UUID userId = input.readUUID();
        return readConfig(input, userId);
    }

    @Override
    public ModUser read(CraftInputStream input, UUID fallbackUserId) throws IOException {
        return readServerbound(input, fallbackUserId);
    }

    public ModUser readServerbound(CraftInputStream input, UUID userId) throws IOException {
        UUID effectiveId = userId != null ? userId : new UUID(0L, 0L);
        return readConfig(input, effectiveId);
    }

    private ModUser readConfig(CraftInputStream input, UUID userId) throws IOException {
        boolean hasConfig = input.readBoolean();
        if (!hasConfig) {
            return new ModUser(userId, defaultConfiguration(GenderIdentities.MALE));
        }

        GeneralOptions.Builder generalBuilder = new GeneralOptions.Builder();
        PhysicsOptions.Builder physicsBuilder = new PhysicsOptions.Builder();
        BreastOptions.Builder breastBuilder = new BreastOptions.Builder();

        int genderId = input.readVarInt();
        if (genderId < 0 || genderId >= GenderIdentities.values().length) {
            throw new IOException("Invalid gender identity ordinal: " + genderId);
        }
        generalBuilder.setGenderIdentity(GenderIdentities.values()[genderId]);

        breastBuilder.setXOffset(input.readFloat());
        breastBuilder.setYOffset(input.readFloat());
        breastBuilder.setZOffset(input.readFloat());
        breastBuilder.setBustSize(input.readFloat());
        breastBuilder.setCleavage(input.readFloat());

        boolean physicsEnabled = input.readBoolean();
        physicsBuilder.setBreastPhysics(physicsEnabled);
        if (physicsEnabled) {
            breastBuilder.setUniBoob(input.readBoolean());
            physicsBuilder.setBuoyancy(input.readFloat());
            physicsBuilder.setFloppiness(input.readFloat());
        } else {
            breastBuilder.setUniBoob(true);
            physicsBuilder.setBuoyancy(0.333f);
            physicsBuilder.setFloppiness(0.75f);
        }

        UVLayouts uvLayouts = readUVLayouts(input);

        generalBuilder.setHurtSounds(input.readBoolean());
        generalBuilder.setVoicePitch(input.readFloat());

        boolean showInArmor = input.readBoolean();
        generalBuilder.setShowInArmor(showInArmor);
        physicsBuilder.setArmorPhysics(showInArmor);

        return new ModUser(userId, new ModConfiguration(
                generalBuilder.create(),
                physicsBuilder.create(),
                breastBuilder.create(),
                uvLayouts));
    }

    @Override
    public void write(ModUser user, CraftOutputStream output) throws IOException {
        output.writeUUID(user.userId());

        ModConfiguration configuration = user.configuration();
        GeneralOptions general = configuration.generalOptions();
        PhysicsOptions physics = configuration.physicsOptions();
        BreastOptions breast = configuration.breastOptions();
        UVLayouts uvLayouts = configuration.uvLayouts();

        if (general.genderIdentity() == GenderIdentities.MALE) {
            output.writeBoolean(false);
            return;
        }

        output.writeBoolean(true);

        output.writeVarInt(general.genderIdentity().ordinal());

        output.writeFloat(breast.xOffset());
        output.writeFloat(breast.yOffset());
        output.writeFloat(breast.zOffset());
        output.writeFloat(breast.bustSize());
        output.writeFloat(breast.cleavage());

        output.writeBoolean(physics.breastPhysics());
        if (physics.breastPhysics()) {
            output.writeBoolean(breast.uniBoob());
            output.writeFloat(physics.buoyancy());
            output.writeFloat(physics.floppiness());
        }

        writeUVLayouts(uvLayouts, output);

        output.writeBoolean(general.hurtSounds());
        output.writeFloat(general.voicePitch());

        output.writeBoolean(general.showInArmor());
    }

    public static ModConfiguration defaultConfiguration(GenderIdentities identity) {
        return new ModConfiguration(
                new GeneralOptions(identity, true, 1.0f, true),
                new PhysicsOptions(true, true, 0.333f, 0.75f),
                new BreastOptions(identity == GenderIdentities.MALE ? 0.0f : 0.6f, 0.0f, 0.0f, 0.0f, true, 0.0f),
                new UVLayouts(
                        new UVLayouts.Layer(defaultSkinLeft(), defaultSkinRight()),
                        new UVLayouts.Layer(defaultOverlayLeft(), defaultOverlayRight())
                )
        );
    }

    public static UVLayout defaultSkinLeft() {
        Map<UVDirection, UVQuad> map = new EnumMap<>(UVDirection.class);
        map.put(UVDirection.EAST, new UVQuad(24, 21, 27, 26));
        map.put(UVDirection.WEST, new UVQuad(16, 21, 20, 26));
        map.put(UVDirection.DOWN, new UVQuad(20, 17, 24, 21));
        map.put(UVDirection.UP, new UVQuad(20, 25, 24, 27));
        map.put(UVDirection.NORTH, new UVQuad(20, 21, 24, 26));
        return new UVLayout(map);
    }

    public static UVLayout defaultSkinRight() {
        Map<UVDirection, UVQuad> map = new EnumMap<>(UVDirection.class);
        map.put(UVDirection.EAST, new UVQuad(28, 21, 32, 26));
        map.put(UVDirection.WEST, new UVQuad(21, 21, 24, 26));
        map.put(UVDirection.DOWN, new UVQuad(24, 17, 28, 21));
        map.put(UVDirection.UP, new UVQuad(24, 25, 28, 27));
        map.put(UVDirection.NORTH, new UVQuad(24, 21, 28, 26));
        return new UVLayout(map);
    }

    public static UVLayout defaultOverlayLeft() {
        Map<UVDirection, UVQuad> map = new EnumMap<>(UVDirection.class);
        map.put(UVDirection.EAST, new UVQuad(0, 0, 0, 0));
        map.put(UVDirection.WEST, new UVQuad(17, 37, 20, 42));
        map.put(UVDirection.DOWN, new UVQuad(20, 34, 24, 37));
        map.put(UVDirection.UP, new UVQuad(20, 41, 24, 44));
        map.put(UVDirection.NORTH, new UVQuad(20, 37, 24, 42));
        return new UVLayout(map);
    }

    public static UVLayout defaultOverlayRight() {
        Map<UVDirection, UVQuad> map = new EnumMap<>(UVDirection.class);
        map.put(UVDirection.EAST, new UVQuad(28, 37, 31, 42));
        map.put(UVDirection.WEST, new UVQuad(0, 0, 0, 0));
        map.put(UVDirection.DOWN, new UVQuad(24, 34, 28, 37));
        map.put(UVDirection.UP, new UVQuad(24, 41, 28, 44));
        map.put(UVDirection.NORTH, new UVQuad(24, 37, 28, 42));
        return new UVLayout(map);
    }

    private UVLayouts readUVLayouts(CraftInputStream input) throws IOException {
        UVLayouts.Layer skin = readLayer(input);
        UVLayouts.Layer overlay = readLayer(input);
        return new UVLayouts(skin, overlay);
    }

    private UVLayouts.Layer readLayer(CraftInputStream input) throws IOException {
        UVLayout left = readUVLayout(input);
        UVLayout right = readUVLayout(input);
        return new UVLayouts.Layer(left, right);
    }

    private UVLayout readUVLayout(CraftInputStream input) throws IOException {
        int count = input.readVarInt();
        if (count < 0 || count > UVDirection.values().length) {
            throw new IOException("Invalid UV quad count: " + count);
        }

        Map<UVDirection, UVQuad> quads = new EnumMap<>(UVDirection.class);
        for (int i = 0; i < count; i++) {
            int directionId = input.readVarInt();
            if (directionId < 0 || directionId >= UVDirection.values().length) {
                throw new IOException("Invalid UV direction: " + directionId);
            }
            UVDirection direction = UVDirection.byId(directionId);
            UVQuad quad = new UVQuad(
                    input.readVarInt(),
                    input.readVarInt(),
                    input.readVarInt(),
                    input.readVarInt());
            if (quads.put(direction, quad) != null) {
                throw new IOException("Duplicate UV direction: " + direction);
            }
        }
        return new UVLayout(quads);
    }

    private void writeUVLayouts(UVLayouts layouts, CraftOutputStream output) throws IOException {
        UVLayouts effective = layouts != null ? layouts : new UVLayouts(
                new UVLayouts.Layer(defaultSkinLeft(), defaultSkinRight()),
                new UVLayouts.Layer(defaultOverlayLeft(), defaultOverlayRight())
        );
        writeLayer(effective.skin(), defaultSkinLeft(), defaultSkinRight(), output);
        writeLayer(effective.overlay(), defaultOverlayLeft(), defaultOverlayRight(), output);
    }

    private void writeLayer(UVLayouts.Layer layer, UVLayout fallbackLeft, UVLayout fallbackRight, CraftOutputStream output) throws IOException {
        UVLayout left = layer != null && layer.left() != null && !layer.left().getQuads().isEmpty() ? layer.left() : fallbackLeft;
        UVLayout right = layer != null && layer.right() != null && !layer.right().getQuads().isEmpty() ? layer.right() : fallbackRight;
        writeUVLayout(left, output);
        writeUVLayout(right, output);
    }

    private void writeUVLayout(UVLayout layout, CraftOutputStream output) throws IOException {
        if (layout == null || layout.getQuads() == null) {
            output.writeVarInt(0);
            return;
        }
        Map<UVDirection, UVQuad> quads = layout.getQuads();
        output.writeVarInt(quads.size());
        for (Map.Entry<UVDirection, UVQuad> entry : quads.entrySet()) {
            output.writeVarInt(entry.getKey().ordinal());
            UVQuad quad = entry.getValue();
            output.writeVarInt(quad.x1());
            output.writeVarInt(quad.y1());
            output.writeVarInt(quad.x2());
            output.writeVarInt(quad.y2());
        }
    }
}
