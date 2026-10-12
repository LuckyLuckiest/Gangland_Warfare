package org.luckyraven.gangland.turf.contract;

/**
 * Persisted high-water mark for turf ids. Ids are handed out from a monotonically growing counter and this records
 * the highest one ever allocated, so deleting the newest turf and restarting never lets its id be reused (TF-05).
 */
public interface TurfSequenceContract {

	/** Highest turf id ever allocated, 0 when none was ever stored. */
	int loadHighWaterMark();

	/** Remembers that ids up to {@code highestAllocatedId} are used. */
	void saveHighWaterMark(int highestAllocatedId);
}
