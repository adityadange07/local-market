package in.localshop.os.customer;

import java.util.List;
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
@RequestMapping("/api/v1/customer/addresses")
public class CustomerAddressController {
    private final CustomerAddressService addresses;

    public CustomerAddressController(CustomerAddressService addresses) { this.addresses = addresses; }

    @GetMapping
    public List<CustomerAddressView> list() { return addresses.list(); }

    @PostMapping
    public CustomerAddressView create(@Valid @RequestBody CustomerAddressRequest request) {
        return addresses.create(request);
    }

    @PutMapping("/{addressId}")
    public CustomerAddressView update(@PathVariable UUID addressId,
            @Valid @RequestBody CustomerAddressRequest request) {
        return addresses.update(addressId, request);
    }

    @DeleteMapping("/{addressId}")
    public void delete(@PathVariable UUID addressId) { addresses.delete(addressId); }
}
