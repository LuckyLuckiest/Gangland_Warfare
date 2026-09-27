package org.luckyraven.gangland.copsncrooks.npc.police;

import lombok.Getter;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehaviorFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * ({@link CopNpc#leaveSquad()}).
 * <p>
 * {@link #isStaffed()} turns true once the first cop is {@link #add(CopNpc) assigned} and stays true: line-of-sight
 * evasion (0.12) only takes over a chase from the old decay timer once a cop has actually been sent after the player.
 * <p>
 * Backup waves (0.12 F4): cops lost from the roster (killed, invalidated) do not respawn on the very next spawn
 * check — {@link #addLosses(int, long)} queues them behind {@code Backup_Delay_Seconds}, and {@link CopManager}'s
 * spawn task only lets the queued count through once {@link #isBackupDue(long)} says the delay has elapsed. The
 * initial response to a fresh wanted episode is unaffected: {@link #markInitialResponseDone()} is only set after
 * that first spawn pass, and losses are queued only once it is set.
 * <p>
 * Losses are counted in {@link #release(CopNpc, NpcMarkManager)} itself, not by whichever loop happens to remove the
 * cop: {@link CopManager}'s AI task (ticks far more often than the spawn task, and starts immediately) usually prunes
 * a dead cop long before the spawn task's own pass ever sees it, so counting only the spawn task's removals would
 * leave {@link #drainLost()} at zero almost every time. Both call sites reach {@link #release} either way.
 */
@Getter
public class CopGroup {

	private final UUID         targetPlayerId;
	private final List<CopNpc> cops;
	private final NpcSquad     squad;

	private volatile boolean staffed;

	private int     pendingBackup;
	private long    backupDueAt;
	private boolean initialResponseDone;
	private int     lostSinceSpawnCheck;

	public CopGroup(UUID targetPlayerId) {
		this.targetPlayerId = targetPlayerId;
		this.cops           = Collections.synchronizedList(new ArrayList<>());
		this.squad          = new NpcSquad();
	}

	public void add(CopNpc cop) {
		cops.add(cop);
		squad.add(cop);
		cop.setGroup(this);
		staffed = true;
	}

	/**
	 * Queues {@code count} cops lost from the roster to respawn once their backup wave is due. Does nothing to the
	 * due time if a wave is already queued: several loss ticks before the first wave lands must not keep pushing the
	 * deadline back.
	 *
	 * @param count how many cops were just lost
	 * @param dueAt the {@link System#currentTimeMillis()} timestamp at which the wave may spawn
	 */
	public void addLosses(int count, long dueAt) {
		if (count <= 0) return;

		pendingBackup += count;
		if (backupDueAt <= 0) backupDueAt = dueAt;
	}

	/**
	 * @param now the current {@link System#currentTimeMillis()}
	 *
	 * @return {@code true} once a queued backup wave's delay has elapsed
	 */
	public boolean isBackupDue(long now) {
		return pendingBackup > 0 && backupDueAt > 0 && now >= backupDueAt;
	}

	/**
	 * Clears the queued backup wave. Called once its cops have been (re)spawned.
	 */
	public void clearBackup() {
		pendingBackup = 0;
		backupDueAt   = 0;
	}

	/**
	 * Marks the group's initial response to the current wanted episode as sent, so future losses are queued as
	 * backup waves instead of respawning on the next spawn check.
	 */
	public void markInitialResponseDone() {
		initialResponseDone = true;
	}

	/**
	 * Resets this group's backup-wave bookkeeping for a fresh wanted episode. A {@link CopGroup} can outlive the
	 * wanted episode that created it while its cops walk home, and {@link CopManager#onWantedStart} reuses it via
	 * {@code computeIfAbsent} instead of always creating a new one. Without this reset, a new chase that starts
	 * within {@code Backup_Delay_Seconds} of the previous one would inherit the previous episode's pending backup
	 * wave and {@link #isInitialResponseDone()} flag, cutting its own initial response short.
	 */
	public void resetEpisode() {
		clearBackup();
		initialResponseDone = false;
	}

	/**
	 * Destroys a cop that left the group (despawned, killed or invalid) and drops it from the squad. The caller removes
	 * it from {@link #getCops()}: both call sites are iterating that list. Counts towards {@link #drainLost()}.
	 */
	public void release(CopNpc cop, NpcMarkManager markManager) {
		squad.remove(cop);
		cop.destroy(entity -> markManager.removeMark(entity));
		lostSinceSpawnCheck++;
	}

	/**
	 * Reads and resets the count of cops lost (via {@link #release}) since the last time this was called. The spawn
	 * task calls this once per pass instead of counting only the cops its own removal loop finds, since the AI task's
	 * much more frequent pruning usually removes a dead cop first (0.12 F4).
	 *
	 * @return how many cops were lost since the last call
	 */
	public int drainLost() {
		int lost = lostSinceSpawnCheck;
		lostSinceSpawnCheck = 0;
		return lost;
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
