package org.luckyraven.gangland.turf.support;

import org.luckyraven.gangland.turf.contract.TurfSequenceContract;

/**
 * In-memory stand-in for the persisted {@code turf_sequence} row. Keeps the highest value ever saved, the same way
 * {@code TurfSequenceRepository} does, so a "restart" in a test can share it with the next {@code TurfManager}.
 */
public final class InMemoryTurfSequence implements TurfSequenceContract {

	private int highest;

	@Override
	public int loadHighWaterMark() {
		return highest;
	}

	@Override
	public void saveHighWaterMark(int highestAllocatedId) {
		highest = Math.max(highest, highestAllocatedId);
	}
}
