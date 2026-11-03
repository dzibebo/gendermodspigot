package dbrighthd.wildfiregendermodplugin.wildfire;

import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV5;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UserDataStorage {
    private static final int MAGIC = 0x46474D50;
    private static final int VERSION = 1;

    private final File directory;
    private final ModSyncPacketV5 packetCodec = new ModSyncPacketV5();

    public UserDataStorage(File directory) {
        this.directory = directory;
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public synchronized void save(ModUser user, String username) {
        if (user == null || user.userId() == null) return;
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File targetFile = new File(directory, user.userId() + ".dat");
        File tempFile = new File(directory, user.userId() + ".tmp");

        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             CraftOutputStream out = new CraftOutputStream(byteOut)) {

            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(username != null ? username : "");
            packetCodec.write(user, out);

            try (FileOutputStream fileOut = new FileOutputStream(tempFile)) {
                fileOut.write(byteOut.toByteArray());
            }

            Files.move(tempFile.toPath(), targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public void loadAll(Map<UUID, ModUser> destination, Map<String, UUID> nameIndex) {
        if (!directory.exists()) return;

        File[] files = directory.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) return;

        for (File file : files) {
            String fileName = file.getName();
            String uuidString = fileName.substring(0, fileName.length() - 4);
            UUID userId;
            try {
                userId = UUID.fromString(uuidString);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            try (FileInputStream in = new FileInputStream(file)) {
                byte[] bytes = in.readAllBytes();
                try (CraftInputStream input = new CraftInputStream(new ByteArrayInputStream(bytes))) {
                    int magic = input.readInt();
                    if (magic != MAGIC) continue;
                    int version = input.readInt();
                    if (version != VERSION) continue;
                    String username = input.readUTF();

                    ModUser user = packetCodec.read(input);
                    if (user != null) {
                        destination.put(userId, user);
                        if (!username.isEmpty()) {
                            nameIndex.put(username.toLowerCase(Locale.ROOT), userId);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
    }
}
