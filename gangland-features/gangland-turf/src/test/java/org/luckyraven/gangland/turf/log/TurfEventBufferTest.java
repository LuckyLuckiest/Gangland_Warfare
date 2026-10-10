package org.luckyraven.gangland.turf.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests of the in-memory "while you were away" buffer (TF-37): a ten-line ring per gang, a 300-payout income log
 * per gang, and the last quit time per player.
 */
@DisplayName("TurfEventBuffer - ring of ten lines, income log, last quit (TF-37)")
class TurfEventBufferTest {

	@Test
	@DisplayName("ring_keepsTheLastTen: 12 records keep records 3..12, oldest first")
	void ring_keepsTheLastTen() {
		TurfEventBuffer buffer = new TurfEventBuffer();
		for (int at = 1; at <= 12; at++) {
			buffer.record(1, at, "line" + at);
		}

		List<TurfEventBuffer.Entry> latest = buffer.latest(1, 10);

		assertEquals(10, latest.size());
		assertEquals(3L, latest.get(0).at());
		assertEquals(12L, latest.get(9).at());
		assertEquals("line3", latest.get(0).line());
	}

	@Test
	@DisplayName("since_filtersAndCaps: records at 1..6 give 4, 5, 6 after t=3, oldest first")
	void since_filtersAndCaps() {
		TurfEventBuffer buffer = new TurfEventBuffer();
		for (int at = 1; at <= 6; at++) {
			buffer.record(1, at, "line" + at);
		}

		List<TurfEventBuffer.Entry> since = buffer.since(1, 3L, 4);

		assertEquals(List.of(4L, 5L, 6L), since.stream().map(TurfEventBuffer.Entry::at).toList());
	}

	@Test
	@DisplayName("incomeSince_sums: 100 at t=1 and 200 at t=5 give 200 after t=2 and 300 after t=0")
	void incomeSince_sums() {
		TurfEventBuffer buffer = new TurfEventBuffer();
		buffer.recordIncome(1, 1L, new BigDecimal("100"));
		buffer.recordIncome(1, 5L, new BigDecimal("200"));

		assertEquals(0, new BigDecimal("200").compareTo(buffer.incomeSince(1, 2L)));
		assertEquals(0, new BigDecimal("300").compareTo(buffer.incomeSince(1, 0L)));
	}

	@Test
	@DisplayName("lastQuit_unknownIsZero: an unknown player has no quit stamp; markQuit then lastQuit returns it")
	void lastQuit_unknownIsZero() {
		TurfEventBuffer buffer = new TurfEventBuffer();
		UUID player = UUID.randomUUID();

		assertEquals(0L, buffer.lastQuit(player));

		buffer.markQuit(player, 42L);

		assertEquals(42L, buffer.lastQuit(player));
	}

	@Test
	@DisplayName("forgetGang_clearsBoth: forgetting a gang drops its lines and its income log")
	void forgetGang_clearsBoth() {
		TurfEventBuffer buffer = new TurfEventBuffer();
		buffer.record(1, 1L, "line");
		buffer.recordIncome(1, 1L, new BigDecimal("50"));

		buffer.forgetGang(1);

		assertTrue(buffer.latest(1, 10).isEmpty());
		assertEquals(0, BigDecimal.ZERO.compareTo(buffer.incomeSince(1, 0L)));
	}
}
