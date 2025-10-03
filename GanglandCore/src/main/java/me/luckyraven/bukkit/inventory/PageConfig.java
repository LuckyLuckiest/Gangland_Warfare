package me.luckyraven.bukkit.inventory;

/**
 * Pagination configuration for MultiInventory pages.
 */
record PageConfig(int maxRows, int maxColumns, int perPage, int pages, int remainingAmount, int finalPage,
				  int initialPage) { }