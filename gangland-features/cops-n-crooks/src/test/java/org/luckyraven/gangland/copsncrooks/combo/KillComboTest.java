package org.luckyraven.gangland.copsncrooks.combo;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.events.combo.KillComboEvent;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves {@link KillCombo} announces its three moments (increment, wanted trigger, reset) as a Bukkit
 * {@link KillComboEvent} of the matching {@link KillComboEvent.Kind}, while the in-process consumers keep running
 * (CONTRACTS C8, docket WB-29 / WB-28).
 */
@DisplayName("KillCombo - Bukkit events for increment, trigger and reset")
class KillComboTest {

	private final JavaPlugin plugin = mock(JavaPlugin.class);
	private final Player     player = mock(Player.class);
	private final Entity     victim = mock(Entity.class);
	private final Wanted     wanted = mock(Wanted.class);

	private KillCombo combo(int threshold) {
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(wanted.getMaxLevel()).thenReturn(5);
		when(wanted.getLevel()).thenReturn(0);
		return new KillCombo(plugin, List.of(threshold));
	}

	private List<KillComboEvent> firedEvents(BukkitStatics bukkit) {
		ArgumentCaptor<KillComboEvent> captor = ArgumentCaptor.forClass(KillComboEvent.class);
		verify(bukkit.pluginManager(), atLeastOnce()).callEvent(captor.capture());
		return captor.getAllValues();
	}

	@Test
	@DisplayName("recordKill fires an INCREMENT event through Bukkit")
	void recordKill_firesIncrementThroughBukkit() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			combo(99).recordKill(player, wanted, victim, 30);

			List<KillComboEvent> events = firedEvents(bukkit);
			assertEquals(1, events.size());
			assertEquals(KillComboEvent.Kind.INCREMENT, events.get(0).getKind());
		}
	}

	@Test
	@DisplayName("reaching the threshold fires WANTED_TRIGGER and the registered consumer still runs")
	void thresholdReached_firesWantedTrigger_andStillRunsTheConsumer() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			AtomicInteger consumed = new AtomicInteger();
			KillCombo     combo    = combo(1);
			combo.setOnWantedLevelTrigger(event -> consumed.incrementAndGet());

			combo.recordKill(player, wanted, victim, 30);

			assertEquals(1, consumed.get());
			assertEquals(List.of(KillComboEvent.Kind.INCREMENT, KillComboEvent.Kind.WANTED_TRIGGER),
			             firedEvents(bukkit).stream().map(KillComboEvent::getKind).toList());
		}
	}

	@Test
	@DisplayName("resetCombo fires RESET from a scheduler task, because the tracker timer is async")
	void resetCombo_firesResetOnTheMainThread() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			KillCombo combo = combo(99);
			combo.recordKill(player, wanted, victim, 30);

			combo.resetCombo(player.getUniqueId());

			verify(bukkit.scheduler(), atLeastOnce()).runTask(eq(plugin), any(Runnable.class));
			List<KillComboEvent> events = firedEvents(bukkit);
			assertEquals(KillComboEvent.Kind.RESET, events.get(events.size() - 1).getKind());
		}
	}
}
