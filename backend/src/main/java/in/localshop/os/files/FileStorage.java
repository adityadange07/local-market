package in.localshop.os.files;

public interface FileStorage {
    void put(String objectKey, byte[] content);
    byte[] read(String objectKey);
    void delete(String objectKey);
}
