package me.luckyraven.bukkit.inventory;

/**
 * Pagination configuration for MultiInventory pages.
 */
final class PageConfig {
	final int maxRows;
	final int maxColumns;
	final int perPage;
	final int pages;
	final int remainingAmount;
	final int finalPage;
	final int initialPage;

	PageConfig(int maxRows, int maxColumns, int perPage, int pages, int remainingAmount, int finalPage,
			   int initialPage) {
		this.maxRows         = maxRows;
		this.maxColumns      = maxColumns;
		this.perPage         = perPage;
		this.pages           = pages;
		this.remainingAmount = remainingAmount;
		this.finalPage       = finalPage;
		this.initialPage     = initialPage;
	}
}