package org.luckyraven.gangland.copsncrooks.npc.police;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehaviorFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.npc.spi.NpcSquadListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A group of cop NPCs all pursuing the same target player.
 * <p>
 * Provides lifecycle management for the cop list. Cuff-lock coordination is intentionally NOT scoped per-group: cops
 * may retarget to a different player after their original target loses wanted status, and a per-group registry would
 * allow two groups to simultaneously hold the lock on the same target. The single global {@link CuffLockRegistry} in
 * {@link CopBehaviorFactory} is keyed by target UUID and correctly serializes cuffing attempts across all groups.
 * <p>
 * The group owns one Keystone {@link NpcSquad}: the cops' shared awareness of the target (last-known position,
 * sighting time, routes). A cop joins it when {@link #add(CopNpc) assigned} and leaves it when
 * {@link #release(CopNpc, NpcMarkManager) released} (despawned, killed or invalid) or when it starts returning
 * ({@link CopNpc#leaveSquad()}). Cops of the group that turn on someone else (an attacker, a wanted civilian) share
 * one {@link #attackerSquad attacker squad} per target, so they coordinate on it too.
 */
@Getter
public class CopGroup {

	private final UUID                targetPlayerId;
	private final List<CopNpc>        cops;
	private final NpcSquad            squad;
	private final Map<UUID, NpcSquad> attackerSquads = new HashMap<>();
	private       NpcSquadListener    listener       = NpcSquadListener.NONE;

	/** The highest tier this group has spawned; a rise above it is announced. 0 before the first spawn. */
	@Setter
	private int     lastTier;
	/** The wanted level the group last spawned for ({@code %level%} on the radio). */
	@Setter
	private int     level;
	/** The stripped display name of the group's current tier ({@code %tier%} on the radio). */
	@Setter
	private String  tierName = "";
	/** The suspect resisted (escaped cuffing too often, or hit a cop): every cop, old and new, fights. */
	private boolean combatAlert;
	private boolean resistingPending;

	private long backupUntil;
	private long backupReadyAt;
	/** Backup cops still to send home after a backup ran out; kept until enough are free to go. */
	@Setter
	private int  pendingRelease;

	public CopGroup(UUID targetPlayerId) {
		this.targetPlayerId = targetPlayerId;
		this.cops           = Collections.synchronizedList(new ArrayList<>());
		this.squad          = new NpcSquad();
	}

	/** The observer of the group's squad and of every attacker squad it opens (the police radio). */
	public void setListener(NpcSquadListener listener) {
		this.listener = listener;
		squad.setListener(listener);
		for (NpcSquad attackers : attackerSquads.values())
			attackers.setListener(listener);
	}

	public void add(CopNpc cop) {
		cops.add(cop);
		squad.add(cop);
		cop.setGroup(this);
	}

	/**
	 * Destroys a cop that left the group (despawned, killed or invalid) and drops it from every squad. The caller
	 * removes it from {@link #getCops()}: both call sites are iterating that list.
	 */
	public void release(CopNpc cop, NpcMarkManager markManager) {
		squad.remove(cop);
		leaveAttackerSquads(cop);
		cop.destroy(entity -> markManager.removeMark(entity));
	}

	/** Moves a live cop out of this group (a radio responder joining another one); the cop is not destroyed. */
	public void detach(CopNpc cop) {
		cops.remove(cop);
		squad.remove(cop);
		leaveAttackerSquads(cop);
		cop.setGroup(null);
	}

	/**
	 * The squad this group's cops share against {@code id}, someone other than the group's wanted player. A new one
	 * speaks on the group's radio, uses the group's formation arc and starts from {@code seed}.
	 */
	public NpcSquad attackerSquad(UUID id, Location seed) {
		return attackerSquads.computeIfAbsent(id, key -> {
			NpcSquad created = new NpcSquad();
			created.setListener(listener);
			created.setFormationArc(squad.getFormationArc());
			created.reportSighting(seed);
			return created;
		});
	}

	/** Who {@code s} hunts, when it is one of this group's attacker squads. */
	public @Nullable UUID attackerOf(NpcSquad s) {
		for (Map.Entry<UUID, NpcSquad> entry : attackerSquads.entrySet())
			if (entry.getValue() == s) return entry.getKey();
		return null;
	}

	public void leaveAttackerSquads(CopNpc cop) {
		for (NpcSquad attackers : attackerSquads.values())
			attackers.remove(cop);
	}

	public void pruneAttackerSquads() {
		attackerSquads.values().removeIf(NpcSquad::isEmpty);
	}

	/** Grants a backup request unless backup is off or the last one is still cooling down. */
	public boolean requestBackup(long now, BackupSettings b) {
		if (!b.enabled() || b.extraCops() <= 0 || now < backupReadyAt) return false;
		backupUntil   = now + b.durationMs();
		backupReadyAt = now + b.cooldownMs();
		return true;
	}

	/** Cops added on top of the wanted-level count while a granted backup lasts. */
	public int backupExtra(long now, BackupSettings b) {
		return backupUntil != 0 && now < backupUntil ? b.extraCops() : 0;
	}

	/** Once a granted backup runs out, queues its extra cops to walk home; {@code true} on that edge. */
	public boolean consumeBackupExpiry(long now, BackupSettings b) {
		if (backupUntil == 0 || now < backupUntil) return false;
		pendingRelease += b.extraCops();
		backupUntil = 0;
		return true;
	}

	/** The suspect {@code targetId} resisted this group: it fights from now on, and says so once. */
	public void escalate(UUID targetId) {
		if (!targetPlayerId.equals(targetId)) return;
		if (!combatAlert) resistingPending = true;
		combatAlert = true;
	}

	/** {@code true} once after an {@link #escalate}. */
	public boolean pollResisting() {
		boolean pending = resistingPending;
		resistingPending = false;
		return pending;
	}

	/** The suspect is no longer wanted: a new episode starts calm. */
	public void clearCombatAlert() {
		combatAlert      = false;
		resistingPending = false;
	}

	public boolean isEmpty() {
		return cops.isEmpty();
	}

	public void destroyAll(NpcMarkManager markManager) {
		for (CopNpc cop : cops) {
			cop.destroy(entity -> markManager.removeMark(entity));
		}
		cops.clear();
	}
}
