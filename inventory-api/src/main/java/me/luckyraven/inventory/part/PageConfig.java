package me.luckyraven.inventory.part;

/**
 * Pagination configuration for MultiInventory pages.
 */
public record PageConfig(int maxRows, int maxColumns, int perPage, int pages, int remainingAmount, int finalPage,
						 int initialPage) { }