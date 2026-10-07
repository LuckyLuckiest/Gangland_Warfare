package org.luckyraven.gangland.data.wanted;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.support.SettingsFixture;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sighting cache and shared cooldown behind the crooked contact (shipped: 1000 per star, 600 s cooldown). */
@DisplayName("ContactDesk - sightings, cooldown and price")
class ContactDeskTest {

	@TempDir
	Path dir;

	private final AtomicLong now = new AtomicLong(1_000L);
	private final UUID       id  = UUID.randomUUID();
	private ContactDesk      desk;

	@BeforeEach
	void setUp() {
		SettingsFixture.initializeMinimal(dir);
		desk = new ContactDesk(now::get);
	}

	@Test
	@DisplayName("seen is true after SEEN, false with no entry and after SEARCHING or EVADED")
	void seen_followsTheLastState() {
		assertFalse(desk.seen(id), "no track counts as unseen");

		desk.observe(id, EvasionState.SEEN);
		assertTrue(desk.seen(id));

		desk.observe(id, EvasionState.SEARCHING);
		assertFalse(desk.seen(id));

		desk.observe(id, EvasionState.SEEN);
		desk.observe(id, EvasionState.EVADED);
		assertFalse(desk.seen(id));
	}

	@Test
	@DisplayName("OFF and forget clear the SEEN entry")
	void seen_clearedByOffAndForget() {
		desk.observe(id, EvasionState.SEEN);
		desk.observe(id, EvasionState.OFF);
		assertFalse(desk.seen(id));

		desk.observe(id, EvasionState.SEEN);
		desk.forget(id);
		assertFalse(desk.seen(id));
	}

	@Test
	@DisplayName("cooldown math: full window after start, counts down, zero when over")
	void cooldownMath() {
		assertEquals(0, desk.cooldownLeftMs(id));

		desk.startCooldown(id);
		assertEquals(600_000L, desk.cooldownLeftMs(id));

		now.addAndGet(100_000L);
		assertEquals(500_000L, desk.cooldownLeftMs(id));

		now.addAndGet(500_000L);
		assertEquals(0, desk.cooldownLeftMs(id));
	}

	@Test
	@DisplayName("priceFor is the per-star price times the stars")
	void priceFor() {
		assertEquals(0, new BigDecimal("1000").compareTo(desk.priceFor(1)));
		assertEquals(0, new BigDecimal("2000").compareTo(desk.priceFor(2)));
	}

	@Test
	@DisplayName("a quit and rejoin keeps the cooldown")
	void quitAndRejoin_cooldownSurvives() {
		desk.observe(id, EvasionState.SEEN);
		desk.startCooldown(id);

		desk.forget(id);

		assertEquals(600_000L, desk.cooldownLeftMs(id));
		assertFalse(desk.seen(id));
	}

	@Test
	@DisplayName("the wanted level ending (OFF) and a new chase keep the cooldown")
	void wantedEndThenNewChase_cooldownSurvives() {
		desk.startCooldown(id);

		desk.observe(id, EvasionState.OFF);
		desk.observe(id, EvasionState.SEEN);

		assertEquals(600_000L, desk.cooldownLeftMs(id));
	}

	@Test
	@DisplayName("expired cooldown entries are pruned when another one starts")
	void startCooldown_prunesExpired() {
		UUID other = UUID.randomUUID();
		desk.startCooldown(other);
		now.addAndGet(601_000L);

		desk.startCooldown(id);

		assertEquals(0, desk.cooldownLeftMs(other));
		assertEquals(600_000L, desk.cooldownLeftMs(id));
	}

	@Test
	@DisplayName("formatLeft rounds up to whole seconds and shows minutes")
	void formatLeft() {
		assertEquals("5s", ContactDesk.formatLeft(4_200L));
		assertEquals("1m 35s", ContactDesk.formatLeft(95_000L));
		assertEquals("10m 0s", ContactDesk.formatLeft(600_000L));
	}

}
