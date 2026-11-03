package in.localshop.os.marketplace;

import java.util.List;

public record MarketplaceSearchPage<T>(List<T> items, int offset, int limit, long total) { }
