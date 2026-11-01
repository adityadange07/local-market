# Mobile placeholder

Reserved for the Expo / React Native / TypeScript customer application, Android-first. No dependency manifest or application source has been initialized.

## Intended source boundaries

```text
app/           navigation and route composition
src/features/  auth, discovery, shop, cart, orders, profile
src/shared/    reusable components, validation, loading/empty/error states
src/api/       typed API client
```

Store refresh credentials using platform secure storage, request location permission only when needed, and support links to public shop storefronts. Do not put business authorization in the client. See [API design](../docs/architecture/API_DESIGN.md) and [MVP scope](../docs/product/MVP_SCOPE.md).
