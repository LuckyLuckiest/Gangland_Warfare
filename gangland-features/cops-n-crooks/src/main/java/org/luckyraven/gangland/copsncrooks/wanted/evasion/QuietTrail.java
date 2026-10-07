package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.EvasionSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.QuietSpeedSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.CrimeRecord;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Cold trail: the longer a chased player commits no crime, the faster the search timer runs, and after
 * {@code Backup_Skip_Seconds} of quiet with no fresh sighting the next backup wave is held back. Quiet time runs from
 * the last heat-ledger crime on this chase, or from the chase start when it has none; offline time is never quiet.
 * Fed once per cop AI tick through {@code CopManager.addAiTickHook}; main thread only.
 *
 * @since 0.16.0
 */
public final class QuietTrail {

	/** The ledger crime a player's quiet is anchored to, and the offline total when this class first saw it. */
	private record Anchor(long crimeAt, long offlineTotalThen) {
	}

	private final HeatLedger        ledger;
	private final ChaseArcs         arcs;
	private final ChaseConfigLoader config;
	private final CopRadio          radio;
	private final LongSupplier      clock;
	private final Map<UUID, Anchor> anchors   = new HashMap<>();
	/** Players whose backup hold was already announced; re-armed when the hold ends. */
	private final Set<UUID>         announced = new HashSet<>();

	public QuietTrail(HeatLedger ledger, ChaseArcs arcs, ChaseConfigLoader config, CopRadio radio, LongSupplier clock) {
		this.ledger = ledger;
		this.arcs   = arcs;
		this.config = config;
		this.radio  = radio;
		this.clock  = clock;
	}

	/** Milliseconds of quiet at {@code now}; 0 when the player has no chase. */
	public long quietMs(UUID id, long now) {
		ChaseArc arc = arcs.arc(id);
		if (arc == null) {
			forget(id);
			return 0;
		}

		AutoSettings auto = Objects.requireNonNullElse(evasion().auto(), AutoSettings.DEFAULT);
		CrimeRecord  last = ledger.lastCrime(id);
		// the ledger keeps sub-threshold crimes with no decay: only one on this chase anchors it
		if (last != null && arcs.hadCrime(id, List.of(last), auto)) {
			long  total  = arcs.offlineTotalMs(id);
			Anchor known = anchors.get(id);
			if (known == null || known.crimeAt() != last.at()) {
				known = new Anchor(last.at(), total);
				anchors.put(id, known);
			}
			return Math.max(0, now - last.at() - (total - known.offlineTotalThen()));
		}

		anchors.remove(id);
		return Math.max(0, now - arc.startedAt());
	}

	/** The timer speed the quiet earns; 1.0 when disabled or not quiet. */
	public double speed(UUID id, long now) {
		return quietSpeed().speedFor(quietMs(id, now));
	}

	/** Holds the group's backup while he is quiet and unseen, and says so once. */
	public void tick(Player player, @Nullable CopGroup group) {
		UUID id = player.getUniqueId();
		if (group == null) {
			forget(id);
			return;
		}

		QuietSpeedSettings quiet = quietSpeed();
		boolean held = quiet.enabled() && arcs.has(id) &&
		               quietMs(id, clock.getAsLong()) >= quiet.backupSkipSeconds() * 1000L &&
		               !group.getSquad().hasFreshSighting();
		group.setBackupHeld(held);
		if (!held) {
			announced.remove(id);
		} else if (announced.add(id)) {
			radio.sayFromLeader(group, "Returning_To_Patrol");
		}
	}

	private void forget(UUID id) {
		anchors.remove(id);
		announced.remove(id);
	}

	private EvasionSettings evasion() {
		return Objects.requireNonNullElse(config.get().evasion(), EvasionSettings.DEFAULT);
	}

	private QuietSpeedSettings quietSpeed() {
		return Objects.requireNonNullElse(evasion().quietSpeed(), QuietSpeedSettings.DEFAULT);
	}
}
