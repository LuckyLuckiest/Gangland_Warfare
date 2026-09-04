package org.luckyraven.gangland.sign.bulk;

import org.bukkit.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.model.ParsedSign;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@link PendingBulkAction#isExpired()}/{@code #matchesSign(ParsedSign)} (Test Surface,
 * lootchests-signs-waypoints.md: "PendingBulkAction#isExpired/matchesSign").
 *
 * <p>{@code isExpired} is deadline-based off the wall clock with no injectable clock seam. Rather
 * than sleep past a real deadline, the "already expired" case is driven with a negative
 * {@code confirmWindowSeconds} so {@code expiresAt} lands in the past at construction time -
 * deterministic without a wait.
 */
@DisplayName("PendingBulkAction - expiry and sign-matching")
class PendingBulkActionTest {

	private static final BulkActionPreview PREVIEW = new BulkActionPreview(64, 320.0, "stone");

	@Test
	@DisplayName("a freshly created action with a positive window is not expired")
	void isExpired_falseWithinWindow() {
		PendingBulkAction action = action(sign(new SignType("glw-buy", "buy"), 0, 0, 0), 100);

		assertFalse(action.isExpired());
	}

	@Test
	@DisplayName("a negative confirm window puts expiresAt in the past, so the action is immediately expired")
	void isExpired_trueOnceDeadlinePasses() {
		PendingBulkAction action = action(sign(new SignType("glw-buy", "buy"), 0, 0, 0), -1);

		assertTrue(action.isExpired());
	}

	@Test
	@DisplayName("matchesSign is true for the same sign type at the same location")
	void matchesSign_trueForSameTypeAndLocation() {
		SignType type = new SignType("glw-buy", "buy");
		PendingBulkAction action = action(sign(type, 10, 64, 10), 100);

		assertTrue(action.matchesSign(sign(type, 10, 64, 10)));
	}

	@Test
	@DisplayName("matchesSign is false when the sign type differs, even at the same location")
	void matchesSign_falseForDifferentType() {
		PendingBulkAction action = action(sign(new SignType("glw-buy", "buy"), 10, 64, 10), 100);

		assertFalse(action.matchesSign(sign(new SignType("glw-sell", "sell"), 10, 64, 10)));
	}

	@Test
	@DisplayName("matchesSign is false when the location differs, even for the same sign type")
	void matchesSign_falseForDifferentLocation() {
		SignType type = new SignType("glw-buy", "buy");
		PendingBulkAction action = action(sign(type, 10, 64, 10), 100);

		assertFalse(action.matchesSign(sign(type, 11, 64, 10)));
	}

	@Test
	@DisplayName("schedulerTaskId defaults to -1 (not scheduled) and is settable")
	void schedulerTaskId_defaultsToNegativeOne_andIsSettable() {
		PendingBulkAction action = action(sign(new SignType("glw-buy", "buy"), 0, 0, 0), 100);

		assertEquals(-1, action.getSchedulerTaskId());

		action.setSchedulerTaskId(7);

		assertEquals(7, action.getSchedulerTaskId());
	}

	private static PendingBulkAction action(ParsedSign sign, int confirmWindowSeconds) {
		return new PendingBulkAction(sign, null, PREVIEW, confirmWindowSeconds);
	}

	private static ParsedSign sign(SignType type, double x, double y, double z) {
		Location location = new Location(null, x, y, z);

		return new ParsedSign() {

			@Override
			public SignType getSignType() {
				return type;
			}

			@Override
			public String getContent() {
				return "";
			}

			@Override
			public double getPrice() {
				return 0;
			}

			@Override
			public int getAmount() {
				return 0;
			}

			@Override
			public Location getLocation() {
				return location;
			}

			@Override
			public String[] getRawLines() {
				return new String[0];
			}

			@Override
			public <T> T getMetadata(String key, Class<T> type) {
				return null;
			}

			@Override
			public boolean hasMetadata(String key) {
				return false;
			}

		};
	}

}
