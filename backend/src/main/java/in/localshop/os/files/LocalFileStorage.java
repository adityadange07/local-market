package in.localshop.os.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local adapter for development; deploy an object-storage adapter in production. */
@Component
public class LocalFileStorage implements FileStorage {
    private static final java.util.regex.Pattern KEY = java.util.regex.Pattern
            .compile("^[0-9a-f-]{36}/[0-9a-f-]{36}$");
    private final Path root;

    public LocalFileStorage(@Value("${localshop.files.storage-root:/var/lib/localshop/files}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public void put(String objectKey, byte[] content) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException exception) {
            throw new IllegalStateException("File storage write failed.", exception);
        }
    }

    @Override
    public byte[] read(String objectKey) {
        try {
            return Files.readAllBytes(resolve(objectKey));
        } catch (IOException exception) {
            throw new IllegalStateException("File storage read failed.", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException exception) {
            throw new IllegalStateException("File storage delete failed.", exception);
        }
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || !KEY.matcher(objectKey).matches()) {
            throw new IllegalArgumentException("Invalid generated file key.");
        }
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid file path.");
        return target;
    }
}
