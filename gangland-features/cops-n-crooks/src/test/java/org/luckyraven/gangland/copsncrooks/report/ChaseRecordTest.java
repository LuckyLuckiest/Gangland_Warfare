package org.luckyraven.gangland.copsncrooks.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 0.12 F6: {@link ChaseRecord} tracks the top star and cop kills reached during a chase, and resolves the outcome the
 * pursuit report shows once the chase ends.
 */
@DisplayName("ChaseRecord")
class ChaseRecordTest {

	@Test
	@DisplayName("raiseStars keeps only the highest level seen")
	void raiseStars_keepsHighest() {
		ChaseRecord record = new ChaseRecord(0L);

		record.raiseStars(2);
		record.raiseStars(1);
		record.raiseStars(4);
		record.raiseStars(3);

		assertEquals(4, record.getMaxStars());
	}

	@Test
	@DisplayName("copKilled increments the count")
	void copKilled_increments() {
		ChaseRecord record = new ChaseRecord(0L);

		record.copKilled();
		record.copKilled();

		assertEquals(2, record.getCopsKilled());
	}

	@Test
	@DisplayName("durationSeconds is the elapsed time, floored, never negative")
	void durationSeconds_flooredElapsed() {
		ChaseRecord record = new ChaseRecord(10_000L);

		assertEquals(0L, record.durationSeconds(10_000L));
		assertEquals(0L, record.durationSeconds(10_999L));
		assertEquals(1L, record.durationSeconds(11_000L));
		assertEquals(0L, record.durationSeconds(0L), "a clock earlier than the start never goes negative");
	}

	@Test
	@DisplayName("resolve without a death or a cuff is ESCAPED")
	void resolve_defaultIsEscaped() {
		ChaseRecord record = new ChaseRecord(0L);

		assertEquals(ChaseOutcome.ESCAPED, record.resolve(false, 100_000L));
	}

	@Test
	@DisplayName("resolve is BUSTED while the player is currently restrained")
	void resolve_currentlyRestrainedIsBusted() {
		ChaseRecord record = new ChaseRecord(0L);

		assertEquals(ChaseOutcome.BUSTED, record.resolve(true, 100_000L));
	}

	@Test
	@DisplayName("resolve is BUSTED inside the 30s grace window after a cuff, ESCAPED once it elapses")
	void resolve_cuffGraceWindow() {
		ChaseRecord record = new ChaseRecord(0L);
		record.markCuffed(10_000L);

		assertEquals(ChaseOutcome.BUSTED, record.resolve(false, 10_000L), "the moment of the cuff");
		assertEquals(ChaseOutcome.BUSTED, record.resolve(false, 40_000L), "exactly at the 30s edge");
		assertEquals(ChaseOutcome.ESCAPED, record.resolve(false, 40_001L), "just past the 30s window");
	}

	@Test
	@DisplayName("resolve is WASTED once markWasted is called, even if the player is later restrained")
	void resolve_wastedIsSticky() {
		ChaseRecord record = new ChaseRecord(0L);
		record.markWasted();

		assertEquals(ChaseOutcome.WASTED, record.resolve(true, 100_000L));
		assertEquals(ChaseOutcome.WASTED, record.resolve(false, 100_000L));
	}

}
