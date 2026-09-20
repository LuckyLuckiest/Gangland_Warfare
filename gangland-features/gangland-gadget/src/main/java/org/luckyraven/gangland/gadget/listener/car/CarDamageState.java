package org.luckyraven.gangland.gadget.listener.car;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared mutable state crossing the {@code CarDamageListener}/{@code CarWeaponDamageListener} split (§8 Risk 4/C2,
 * WS7 G5). Both sets used to be private fields on the single {@code CarDamageListener}; now constructed once (the
 * only new {@code @Bean} this gate adds) and injected into both listeners. Read/consume goes through this class's
 * own methods, never a raw field access from either listener — centralizing this is what keeps the token race
 * GD-27 (§8 Risk 5) from getting structurally worse after the split, even though it doesn't fix the race itself.
 */
public class CarDamageState {

	private final Set<UUID> pendingRightClickInteract  = ConcurrentHashMap.newKeySet();
	private final Set<UUID> recentWeaponExplosionDamage = ConcurrentHashMap.newKeySet();

	public void markPendingRightClickInteract(UUID playerUUID) {
		pendingRightClickInteract.add(playerUUID);
	}

	/** Removes and returns whether {@code playerUUID} was pending — the "consume" read. */
	public boolean consumePendingRightClickInteract(UUID playerUUID) {
		return pendingRightClickInteract.remove(playerUUID);
	}

	/** Non-consuming read (checks presence without removing). */
	public boolean isPendingRightClickInteract(UUID playerUUID) {
		return pendingRightClickInteract.contains(playerUUID);
	}

	public void removePendingRightClickInteract(UUID playerUUID) {
		pendingRightClickInteract.remove(playerUUID);
	}

	public void markRecentWeaponExplosionDamage(UUID entityUUID) {
		recentWeaponExplosionDamage.add(entityUUID);
	}

	/** Removes and returns whether {@code entityUUID} was marked — the "consume" read. */
	public boolean consumeRecentWeaponExplosionDamage(UUID entityUUID) {
		return recentWeaponExplosionDamage.remove(entityUUID);
	}
}
