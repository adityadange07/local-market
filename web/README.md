# Web placeholder

Reserved for the React, TypeScript, Vite, and Material UI web client. The web application will contain public marketplace/storefront routes and authenticated seller/admin experiences, with role-specific navigation and shared design components. No dependency manifest or application source has been initialized; setup is tracked after the repository and backend foundations.

## Intended source boundaries

```text
src/app/       route composition and application providers
src/features/  auth, marketplace, seller, admin feature screens
src/shared/    reusable UI, forms, status, loading/empty/error states
src/api/       typed API client and DTO contracts
src/theme/     shared design tokens and Material UI theme
src/routes/    public and authenticated route definitions
```

Keep API DTOs separate from UI view models where needed. Enforce permissions on the API; hiding a UI control is not authorization. See [API design](../docs/architecture/API_DESIGN.md) and [product requirements](../docs/product/PRODUCT_REQUIREMENTS.md).
