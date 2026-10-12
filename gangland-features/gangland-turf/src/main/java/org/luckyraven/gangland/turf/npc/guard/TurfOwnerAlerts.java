package org.luckyraven.gangland.turf.npc.guard;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.util.ActionBarManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * W7-E (D28=A): tells the owning gang's online members, by chat line and action bar, when a cop hits one of them on its
 * turf, one of its guards falls, or its Quartermaster is attacked or killed. At most one line per turf per kind per
 * {@code Owner_Alerts.Cooldown_Seconds}; death lines are also told once per NPC. Never reaches allies or rivals. Main thread only.
 */
@CustomLog
public final class TurfOwnerAlerts {

	enum Kind {
		COP_HIT,
		GUARD_DOWN,
		QUARTERMASTER_HIT,
		QUARTERMASTER_DOWN
	}

	private final TurfManager        turfs;
	private final GangLookupContract gangs;
	private final TurfMessages       text;
	private final IntSupplier        cooldownSeconds;

	// ponytail: bounded by turfs x 4 kinds and never pruned; add eviction only if turf counts reach the thousands
	private final Map<String, Long> lastSent  = new HashMap<>();
	private final Set<Object>       deathsTold = Collections.newSetFromMap(new WeakHashMap<>());

	private LongSupplier              clock  = System::currentTimeMillis;
	private BiConsumer<UUID, String>  sender = TurfOwnerAlerts::chatAndActionBar;

	public TurfOwnerAlerts(TurfManager turfs, GangLookupContract gangs, TurfMessages text, IntSupplier cooldownSeconds) {
		this.turfs           = turfs;
		this.gangs           = gangs;
		this.text            = text;
		this.cooldownSeconds = cooldownSeconds;
	}

	/** Test seam. */
	void setClock(LongSupplier clock) {
		this.clock = clock;
	}

	/** Test seam. */
	void setSender(BiConsumer<UUID, String> sender) {
		this.sender = sender;
	}

	public void copHit(Turf turf, Player victim) {
		if (turf == null || victim == null) return;
		send(turf, Kind.COP_HIT, text.text("Alerts.Cop_Hit", "&c%turf%: a cop is attacking &f%player%",
		                                   "turf", turf.getDisplayName(), "player", victim.getName()));
	}

	public void guardDown(int turfId, Object npc) {
		Turf turf = turfs.get(turfId);
		if (turf == null || !deathsTold.add(npc)) return;
		send(turf, Kind.GUARD_DOWN, text.text("Alerts.Guard_Down", "&c%turf%: one of your guards fell",
		                                      "turf", turf.getDisplayName()));
	}

	public void quartermasterHit(int turfId, String attacker) {
		Turf turf = turfs.get(turfId);
		if (turf == null) return;
		send(turf, Kind.QUARTERMASTER_HIT,
		     text.text("Alerts.Quartermaster_Hit", "&c%turf%: &f%attacker% &cis attacking your Quartermaster",
		               "turf", turf.getDisplayName(), "attacker", attacker));
	}

	public void quartermasterDown(int turfId, Object npc) {
		Turf turf = turfs.get(turfId);
		if (turf == null || !deathsTold.add(npc)) return;
		send(turf, Kind.QUARTERMASTER_DOWN, text.text("Alerts.Quartermaster_Down",
		                                              "&c%turf%: your Quartermaster was killed",
		                                              "turf", turf.getDisplayName()));
	}

	private void send(Turf turf, Kind kind, String line) {
		Integer ownerId = turf.getOwnerGangId();
		Gang    owner   = ownerId == null ? null : gangs.findById(ownerId);
		if (owner == null || line == null || line.isEmpty()) return;

		long   now = clock.getAsLong();
		String key = turf.getId() + "|" + kind;
		Long   last = lastSent.get(key);
		if (last != null && now - last < cooldownSeconds.getAsInt() * 1000L) return;
		lastSent.put(key, now);

		for (Member member : owner.getMembers()) {
			sender.accept(member.getUuid(), line);
		}
	}

	private static void chatAndActionBar(UUID id, String line) {
		Player player = Bukkit.getPlayer(id);
		if (player == null) return;
		player.sendMessage(line);
		ActionBarManager.send(player, line);
	}
}
