package dev.shirokai.essentials.persistence;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Never replace an unreadable file with defaults. Keep the previous successful revision. */
public final class JsonFiles {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private JsonFiles() {}
    public static <T> T read(Path path, Class<T> type, T defaults) throws IOException {
        if (!Files.exists(path)) { write(path, defaults); return defaults; }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            T result = GSON.fromJson(reader, type);
            if (result == null) throw new IOException("JSON vazio: " + path);
            return result;
        } catch (JsonParseException | IllegalStateException e) { throw new IOException("JSON inválido: " + path, e); }
    }
    public static synchronized void write(Path path, Object value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), path.getFileName().toString(), ".tmp");
        try {
            byte[] data = GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                var bytes = ByteBuffer.wrap(data);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            // Atomic move is required: a filesystem without this support fails without touching the original.
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
