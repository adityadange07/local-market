package in.localshop.os.cart;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/cart")
public class CustomerCartController {
    private final CustomerCartService carts;

    public CustomerCartController(CustomerCartService carts) { this.carts = carts; }

    @GetMapping
    public CartView view() { return carts.view(); }

    @PostMapping("/items")
    public CartView add(@Valid @RequestBody AddCartItemRequest request) { return carts.add(request); }

    @PutMapping("/items/{itemId}")
    public CartView update(@PathVariable UUID itemId, @Valid @RequestBody UpdateCartItemRequest request) {
        return carts.updateItem(itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public CartView remove(@PathVariable UUID itemId) { return carts.removeItem(itemId); }

    @DeleteMapping
    public CartView clear() { return carts.clear(); }
}
