package org.luckyraven.gangland.gang.combat;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.listener.gang.combat.DamageLedgerListener;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DamageLedger and DamageLedgerListener")
class DamageLedgerTest {

	private final AtomicLong         clock = new AtomicLong(100_000L);
	private final Map<UUID, Integer> gangs = new HashMap<>();
	private final UUID               a     = UUID.randomUUID();
	private final UUID               b     = UUID.randomUUID();
	private final UUID               c     = UUID.randomUUID();
	private       DamageLedger         ledger;
	private       CombatTag            tag;
	private       DamageLedgerListener listener;

	@BeforeEach
	void setUp() {
		gangs.put(a, 1);
		gangs.put(b, 2);
		gangs.put(c, 3);
		ledger   = new DamageLedger(clock::get);
		tag      = new CombatTag(clock::get, () -> 15, uuid -> gangs.getOrDefault(uuid, -1));
		listener = new DamageLedgerListener(ledger, tag);
	}

	private Player player(UUID id) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		return player;
	}

	private EntityDamageByEntityEvent hit(Entity damager, Player victim, double finalDamage) {
		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(damager);
		when(event.getEntity()).thenReturn(victim);
		when(event.getFinalDamage()).thenReturn(finalDamage);
		return event;
	}

	@Test
	@DisplayName("cancelledHit_neverTags: the handler is HIGH with ignoreCancelled, after the LOWEST friendly-fire cancel")
	void cancelledHit_neverTags() throws Exception {
		EventHandler handler = DamageLedgerListener.class.getMethod("onDamage", EntityDamageByEntityEvent.class)
		                                                 .getAnnotation(EventHandler.class);

		assertEquals(EventPriority.HIGH, handler.priority());
		assertTrue(handler.ignoreCancelled());
	}

	@Test
	@DisplayName("zeroDamage_neverTags: a 0-damage snowball records nothing and tags nobody")
	void zeroDamage_neverTags() {
		Snowball snowball = mock(Snowball.class);
		Player shooter = player(a);
		when(snowball.getShooter()).thenReturn(shooter);

		listener.onDamage(hit(snowball, player(b), 0D));

		assertTrue(ledger.lastDamager(b).isEmpty());
		assertFalse(tag.isTagged(a));
		assertFalse(tag.isTagged(b));
	}

	@Test
	@DisplayName("respawnOn_oneShotKillRecordsShooter: a lethal arrow still credits the shooter and tags both")
	void respawnOn_oneShotKillRecordsShooter() {
		Arrow arrow = mock(Arrow.class);
		Player shooter = player(a);
		Player victim  = player(b);
		when(arrow.getShooter()).thenReturn(shooter);

		listener.onDamage(hit(arrow, victim, 40D));

		assertEquals(a, ledger.lastDamager(b).orElseThrow());
		assertTrue(tag.isTagged(a));
		assertTrue(tag.isTagged(b));
	}

	@Test
	@DisplayName("a player hurting himself is not a ledger entry")
	void selfHit_notRecorded() {
		Arrow  arrow = mock(Arrow.class);
		Player self  = player(a);
		when(arrow.getShooter()).thenReturn(self);

		listener.onDamage(hit(arrow, self, 6D));

		assertTrue(ledger.lastDamager(a).isEmpty());
	}

	@Test
	@DisplayName("assistWindow: earlier damagers inside the window assist, the last one is the killer, old ones drop out")
	void assistWindow() {
		ledger.record(a, c, 4D, clock.get());
		clock.addAndGet(5_000L);
		ledger.record(b, c, 4D, clock.get());
		clock.addAndGet(3_000L);

		assertEquals(b, ledger.lastDamager(c).orElseThrow());
		assertEquals(List.of(a), ledger.assistants(c, Duration.ofSeconds(10)));

		clock.addAndGet(5_000L);
		assertEquals(List.of(), ledger.assistants(c, Duration.ofSeconds(10)), "a is 13 s old");
		assertEquals(b, ledger.lastDamager(c).orElseThrow());
	}

	@Test
	@DisplayName("a stale last damager expires from the ledger")
	void staleEntriesExpire() {
		ledger.record(a, c, 4D, clock.get());
		clock.addAndGet(10 * 60_000L);

		assertTrue(ledger.lastDamager(c).isEmpty());
	}
}
