package in.localshop.os.files;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/seller/tenants/{tenantId}/products/{productId}/images")
public class SellerProductImageController {
    private final ProductImageService images;

    public SellerProductImageController(ProductImageService images) { this.images = images; }

    @GetMapping
    public List<ProductImageView> list(@PathVariable UUID tenantId, @PathVariable UUID productId) {
        return images.sellerImages(tenantId, productId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductImageView upload(@PathVariable UUID tenantId, @PathVariable UUID productId,
            @RequestParam("file") MultipartFile file) {
        try {
            return images.upload(tenantId, productId, file.getBytes());
        } catch (java.io.IOException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Could not read uploaded image.");
        }
    }

    @DeleteMapping("/{imageId}")
    public void delete(@PathVariable UUID tenantId, @PathVariable UUID productId, @PathVariable UUID imageId) {
        images.delete(tenantId, productId, imageId);
    }
}
