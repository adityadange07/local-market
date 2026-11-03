package in.localshop.os.marketplace;

import java.util.UUID;

import in.localshop.os.files.ProductImageDownload;
import in.localshop.os.files.ProductImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/marketplace/shops/{shopSlug}/products/{productSlug}/images")
public class PublicProductImageController {
    private final ProductImageService images;

    public PublicProductImageController(ProductImageService images) { this.images = images; }

    @GetMapping("/{imageId}")
    public ResponseEntity<byte[]> get(@PathVariable String shopSlug, @PathVariable String productSlug,
            @PathVariable UUID imageId) {
        ProductImageDownload image = images.publicImage(shopSlug, productSlug, imageId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noCache()).header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }
}
