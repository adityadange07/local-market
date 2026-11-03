package in.localshop.os.catalog;

import java.util.UUID;

public record CategoryView(UUID id, UUID parentId, String name, String slug, String description, int sortOrder) { }
