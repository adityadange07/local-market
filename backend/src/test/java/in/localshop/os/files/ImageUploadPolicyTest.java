package in.localshop.os.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ImageUploadPolicyTest {
    @Test
    void detectsOnlySupportedContentFromTheFileSignature() {
        assertThat(ImageUploadPolicy.verifyAndDetect(new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 })).isEqualTo("image/jpeg");
        assertThat(ImageUploadPolicy.verifyAndDetect(new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A })).isEqualTo("image/png");
        assertThat(ImageUploadPolicy.verifyAndDetect(new byte[] {
                'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P' })).isEqualTo("image/webp");
    }

    @Test
    void rejectsEmptyOversizedAndSpoofedImageContent() {
        assertThatThrownBy(() -> ImageUploadPolicy.verifyAndDetect(new byte[0]))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ImageUploadPolicy.verifyAndDetect(new byte[ImageUploadPolicy.MAX_BYTES + 1]))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ImageUploadPolicy.verifyAndDetect("not really an image".getBytes()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void calculatesStableSha256Digest() {
        assertThat(ImageUploadPolicy.sha256("abc".getBytes()))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
