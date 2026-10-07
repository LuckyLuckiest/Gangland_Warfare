package org.luckyraven.gangland.copsncrooks.npc.police;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.RegroupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.PendingUnit;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.SpawnBias;
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
	/** Cuffs each suspect broke out of, across every officer of the group (the lock passes between them). */
	private final Map<UUID, Integer> cuffFailures = new HashMap<>();
	/** Spawn origins of recycled cops, and until when replacements skip them. */
	private final Map<Location, Long> avoidedSpawns = new HashMap<>();

	/**
	 * Until when (the radio clock, ms) the group's cops fall back to cover, hurt or not: the squad just lost its
	 * Commander ({@code CopRetreat}). 0 when not falling back.
	 */
	@Setter
	private long fallBackUntil;

	/** Backup cops still to send home after a backup ran out; kept until enough are free to go. */
	@Setter
	private int  pendingRelease;

	/** Radio-clock ms of the latest stuck-recycle tip-off; 0 when none. */
	private long tipOffAt;

	/** Radio-clock ms of recent casualties, for {@link #shouldRegroup}. */
	private final List<Long> casualtyTimes = new ArrayList<>();
	private       boolean    regrouping;
	private       long       regroupReadyAt;

	/** A pending unit stops counting as en route this long past its ETA ({@link #unitsEnRoute}). */
	private static final long EN_ROUTE_GRACE_MS = 10_000L;

	/** Dispatched units on their way, in enqueue order ({@link #takeDue}). */
	private final List<PendingUnit> pending = new ArrayList<>();
	/** The hand-off's spawn bias; read through {@link #biasAt}, which drops it once expired. */
	@Getter(AccessLevel.NONE)
	private @Nullable SpawnBias bias;
	private boolean backupHeld;
	/** Radio-clock ms of the latest casualty; a regroup clears {@link #casualtyTimes} but not this. */
	private long    lastCasualtyAt;
	private long    breatherUntil;

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

	public boolean isFallingBack(long now) {
		return now < fallBackUntil;
	}

	/** Grants a backup request unless backup is off or held (cold trail) or the last one is still cooling down. */
	public boolean requestBackup(long now, BackupSettings b) {
		if (backupHeld || !b.enabled() || b.extraCops() <= 0 || now < backupReadyAt) return false;
		backupUntil   = now + b.durationMs();
		backupReadyAt = now + b.cooldownMs();
		return true;
	}

	/**
	 * A regroup waits for backup, so it grants it itself when none is active, ignoring the backup cooldown (the
	 * regroup's own cooldown stops spam); a held backup (cold trail) grants nothing. With a backup already active it just waits for that one.
	 * {@code true} when backup is, or now is, on its way.
	 */
	public boolean grantRegroupBackup(long now, BackupSettings b) {
		if (backupHeld || !b.enabled() || b.extraCops() <= 0) return false;
		if (backupExtra(now, b) > 0) return true;
		backupUntil   = now + b.durationMs();
		backupReadyAt = Math.max(backupReadyAt, now + b.cooldownMs());
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

	/**
	 * One more cuff {@code target} broke out of, whichever officer tried; {@code true} (and the count restarts) when
	 * it reaches {@code max}.
	 */
	public boolean recordCuffFailure(UUID target, int max) {
		if (cuffFailures.merge(target, 1, Integer::sum) < max) return false;
		cuffFailures.remove(target);
		return true;
	}

	/** {@code target} was cuffed: his break-out count restarts. */
	public void resetCuffFailures(UUID target) {
		cuffFailures.remove(target);
	}

	/** The suspect is no longer wanted: a new episode starts calm. */
	public void clearCombatAlert() {
		combatAlert      = false;
		resistingPending = false;
		cuffFailures.clear();
	}

	/** Replacements skip {@code spawn} (the spawner a recycled cop came from) until {@code until}. */
	public void avoid(Location spawn, long until) {
		avoidedSpawns.put(spawn.clone(), until);
	}

	/** {@code location} is within 3 blocks of a spawn origin still {@link #avoid avoided} at {@code now}. */
	public boolean isAvoided(Location location, long now) {
		avoidedSpawns.values().removeIf(until -> now >= until);
		for (Location spawn : avoidedSpawns.keySet())
			if (spawn.getWorld() == location.getWorld() && spawn.distanceSquared(location) <= 9) return true;
		return false;
	}

	/** Stamps the latest stuck-recycle tip-off at {@code now} (radio-clock ms). */
	public void markTipOff(long now) {
		tipOffAt = now;
	}

	/** A tip-off was stamped no more than {@code windowMs} before {@code now}. */
	public boolean tippedOffWithin(long now, long windowMs) {
		return tipOffAt > 0 && now - tipOffAt <= windowMs;
	}

	/** A cop of the group went down at {@code now}; casualties older than the longest window are not kept. */
	public void recordCasualty(long now) {
		casualtyTimes.add(now);
		lastCasualtyAt = Math.max(lastCasualtyAt, now);
		// ponytail: fixed 5 min cap, so a window longer than that is cut short; windows are seconds
		casualtyTimes.removeIf(at -> now - at > 300_000L);
	}

	/** A fighting group lost {@code r.casualties()} cops inside the window and its regroup is off cooldown. */
	public boolean shouldRegroup(long now, RegroupSettings r) {
		if (!r.enabled() || !combatAlert || regrouping || now < regroupReadyAt) return false;
		return casualtyTimes.stream().filter(at -> now - at <= r.windowMs()).count() >= r.casualties();
	}

	/** The group pulls back to cover for up to {@code r.fallBackMs()} and waits for backup. */
	public void startRegroup(long now, RegroupSettings r) {
		regrouping     = true;
		fallBackUntil  = Math.max(fallBackUntil, now + r.fallBackMs());
		regroupReadyAt = now + r.cooldownMs();
		casualtyTimes.clear();
	}

	public boolean isRegrouping() {
		return regrouping;
	}

	/** The regroup is over: the cops leave cover on their next AI tick. */
	public void endRegroup() {
		regrouping    = false;
		fallBackUntil = 0;
	}

	// ── Dispatch queue (0.16) ─────────────────────────────────────────────────

	public void enqueue(PendingUnit unit) {
		pending.add(unit);
	}

	/** Removes and returns every pending unit due at {@code now} ({@code arriveAt <= now}). */
	public List<PendingUnit> takeDue(long now) {
		List<PendingUnit> due = new ArrayList<>();
		pending.removeIf(unit -> unit.arriveAt() <= now && due.add(unit));
		return due;
	}

	/** Puts back a due unit that found no spot; it is retried on the next spawn run. */
	public void requeue(PendingUnit unit) {
		pending.add(unit);
	}

	public int pendingCount() {
		return pending.size();
	}

	public boolean hasPendingUnits() {
		return !pending.isEmpty();
	}

	public void clearPending() {
		pending.clear();
	}

	/**
	 * Any pending unit with {@code arriveAt + 10_000 > now}.
	 */
	// ponytail: a unit overdue by 10 s (it keeps failing to spawn) stops counting, so evasion falls back to 0.15
	// behaviour instead of holding the stars forever; make it a knob if 10 s proves wrong.
	public boolean unitsEnRoute(long now) {
		for (PendingUnit unit : pending)
			if (unit.arriveAt() + EN_ROUTE_GRACE_MS > now) return true;
		return false;
	}

	/** The hand-off's spawn bias for units enqueued from now on ({@code null} clears it). */
	public void setBias(@Nullable SpawnBias bias) {
		this.bias = bias;
	}

	/** The hand-off bias, {@code null} once it expired. */
	public @Nullable SpawnBias biasAt(long now) {
		if (bias != null && !bias.activeAt(now)) bias = null;
		return bias;
	}

	/** Cold trail: while held, {@link #requestBackup} and {@link #grantRegroupBackup} grant nothing. */
	public void setBackupHeld(boolean held) {
		this.backupHeld = held;
	}

	public boolean isBackupHeld() {
		return backupHeld;
	}

	/** A cop of the group went down no more than {@code windowMs} before {@code now}. */
	public boolean casualtyWithin(long now, long windowMs) {
		return lastCasualtyAt > 0 && now - lastCasualtyAt <= windowMs;
	}

	/** Radio-clock ms before which no dispatched unit arrives (post-wipe breather or rejoin grace); 0 when none. */
	public long getBreatherUntil() {
		return breatherUntil;
	}

	public void setBreatherUntil(long until) {
		this.breatherUntil = until;
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
