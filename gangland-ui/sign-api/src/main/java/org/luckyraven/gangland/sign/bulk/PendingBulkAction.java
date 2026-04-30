package org.luckyraven.gangland.sign.bulk;

import lombok.Getter;
import lombok.Setter;
import org.luckyraven.gangland.sign.model.ParsedSign;

/**
 * Tracks one player's in-flight bulk confirmation. Created when a player shift-clicks a sign with a
 * {@link BulkSignHandler}; removed on confirmation, cancellation, or expiry.
 */
@Getter
public class PendingBulkAction {

	private final ParsedSign        parsedSign;
	private final BulkSignHandler   handler;
	private final BulkActionPreview preview;
	/**
	 * Epoch-millis deadline after which this pending action is considered stale.
	 */
	private final long              expiresAt;
	/**
	 * Bukkit scheduler task ID for the expiry countdown; -1 if not scheduled yet.
	 */
	@Setter
	private       int               schedulerTaskId = -1;

	public PendingBulkAction(ParsedSign parsedSign, BulkSignHandler handler, BulkActionPreview preview,
	                         int confirmWindowSeconds) {
		this.parsedSign = parsedSign;
		this.handler    = handler;
		this.preview    = preview;
		this.expiresAt  = System.currentTimeMillis() + (confirmWindowSeconds * 1000L);
	}

	public boolean isExpired() {
		return System.currentTimeMillis() > expiresAt;
	}

	/**
	 * Returns {@code true} when {@code other} refers to the same sign block and same sign type, so a second shift-click
	 * on the same sign confirms rather than creating a new pending action.
	 */
	public boolean matchesSign(ParsedSign other) {
		if (!parsedSign.getSignType().equals(other.getSignType())) return false;

		return parsedSign.getLocation().equals(other.getLocation());
	}

}
