package org.luckyraven.gangland.sign.bulk;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Snapshot of what a bulk action will do, shown to the player before confirmation.
 */
@Getter
@RequiredArgsConstructor
public class BulkActionPreview {

	/**
	 * Number of items that will be transferred.
	 */
	private final int quantity;

	/**
	 * Total price of the transaction (buy = cost to player, sell = payout to player).
	 */
	private final double totalPrice;

	/**
	 * Human-readable name of the item being traded (e.g. "police_vest", "AK47").
	 */
	private final String contentName;

}
