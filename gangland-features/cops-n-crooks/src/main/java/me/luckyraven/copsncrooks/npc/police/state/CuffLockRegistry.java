package me.luckyraven.copsncrooks.npc.police.state;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which cop currently owns the cuff attempt for each target player. Ownership is explicit: one target -> one
 * cop.
 */
public final class CuffLockRegistry {

	private final Map<UUID, UUID> ownerByTarget = new ConcurrentHashMap<>();

	/**
	 * Attempts to reserve the target for the given cop.
	 *
	 * @param targetId the player being cuffed
	 * @param copId the cop attempting to cuff
	 *
	 * @return true if the lock was acquired, false if another cop already owns it
	 */
	public boolean tryAcquire(UUID targetId, UUID copId) {
		if (targetId == null || copId == null) return false;
		return ownerByTarget.putIfAbsent(targetId, copId) == null;
	}

	/**
	 * Returns whether the given cop owns the lock for the target.
	 */
	public boolean isOwner(UUID targetId, UUID copId) {
		if (targetId == null || copId == null) return false;
		return copId.equals(ownerByTarget.get(targetId));
	}

	/**
	 * Releases the lock only if it is owned by the given cop.
	 */
	public void release(UUID targetId, UUID copId) {
		if (targetId == null || copId == null) return;
		ownerByTarget.remove(targetId, copId);
	}

	/**
	 * Returns the current owner cop UUID for a target, or null if unlocked.
	 */
	public UUID getOwner(UUID targetId) {
		return targetId == null ? null : ownerByTarget.get(targetId);
	}

	/**
	 * Releases any lock currently held for the target.
	 */
	public void forceRelease(UUID targetId) {
		if (targetId == null) return;
		ownerByTarget.remove(targetId);
	}

	public void releaseByCop(UUID copId) {
		if (copId == null) return;
		ownerByTarget.entrySet().removeIf(entry -> copId.equals(entry.getValue()));
	}
}
