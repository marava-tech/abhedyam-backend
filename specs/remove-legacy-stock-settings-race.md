# Remove legacy stock/product endpoints + fix settings race
- Delete /api/v1/stock/*, /api/v1/products, /owners/{id}/products (replaced by /items API; app 1.12 doesn't call them). Also drops the public with-stock route.
- OwnerSettingsService.getOwnerSettings: INSERT IGNORE then read, so parallel first GETs never 409.
