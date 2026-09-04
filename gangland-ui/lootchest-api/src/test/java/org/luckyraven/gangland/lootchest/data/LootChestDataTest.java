package org.luckyraven.gangland.lootchest.data;

import org.bukkit.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the {@code LootChestData} cooldown/respawn state machine (Test Surface,
 * lootchests-signs-waypoints.md: "LootChestData state machine —
 * startCooldown/isOnCooldown/isBlocked/canRespawn/respawn/clearInventory/hasItemsRemaining").
 *
 * <p>Avoids sleeping on the wall clock by writing {@code cooldownEndTime}/{@code lastOpened}
 * directly (both have public setters) at offsets from {@code System.currentTimeMillis()} — the
 * comparisons under test are all against "now", so a few hundred milliseconds of test-execution
 * slack does not affect the assertions.
 */
class LootChestDataTest {

	@Test
	@DisplayName("a freshly built chest is not on cooldown and cannot legacy-respawn with respawnTime == 0")
	void freshChest_notOnCooldown_cannotRespawn() {
		LootChestData chest = chest(0);

		assertFalse(chest.isOnCooldown());
		assertFalse(chest.isBlocked(), "empty AND on cooldown — cooldown is false so isBlocked must be false too");
		assertFalse(chest.canRespawn(), "respawnTime == 0 disables the legacy respawn path entirely");
		assertTrue(chest.isEmpty());
		assertFalse(chest.hasItemsRemaining());
	}

	@Test
	@DisplayName("startCooldown sets isLooted and an active cooldown window")
	void startCooldown_setsLootedAndCooldown() {
		LootChestData chest = chest(0);

		chest.startCooldown(30);

		assertTrue(chest.isLooted());
		assertTrue(chest.isOnCooldown());
		assertTrue(chest.isBlocked(), "empty inventory + active cooldown must block re-opening");
		assertFalse(chest.canRespawn(), "cannot respawn while still on cooldown");
		assertTrue(chest.getRemainingCooldownSeconds() > 0);
		assertTrue(chest.getRemainingCooldownMillis() > 0);
	}

	@Test
	@DisplayName("a cooldown whose end time has passed reports 0 remaining and allows respawn")
	void expiredCooldown_reportsZeroRemaining_allowsRespawn() {
		LootChestData chest = chest(0);
		chest.setCooldownEndTime(System.currentTimeMillis() - 1_000);

		assertFalse(chest.isOnCooldown());
		assertEquals(0, chest.getRemainingCooldownSeconds());
		assertEquals(0, chest.getRemainingCooldownMillis());
		assertTrue(chest.canRespawn(), "cooldownEndTime > 0 and already passed must allow respawn");
	}

	@Test
	@DisplayName("legacy respawnTime path allows respawn once enough time has elapsed since lastOpened")
	void legacyRespawnTime_allowsRespawnAfterElapsed() {
		LootChestData chest = chest(1); // respawnTime = 1 second
		chest.setLastOpened(System.currentTimeMillis() - 5_000);

		assertTrue(chest.canRespawn(), "5s elapsed >= the 1s legacy respawnTime");
	}

	@Test
	@DisplayName("legacy respawnTime path blocks respawn before enough time has elapsed")
	void legacyRespawnTime_blocksRespawnBeforeElapsed() {
		LootChestData chest = chest(1_000); // respawnTime = 1000 seconds
		chest.setLastOpened(System.currentTimeMillis());

		assertFalse(chest.canRespawn());
	}

	@Test
	@DisplayName("respawn clears looted state, cooldown, inventory and the unlocked flag")
	void respawn_clearsAllTransientState() {
		LootChestData chest = chest(0);
		chest.startCooldown(30);
		chest.setUnlocked(true);
		chest.setCurrentInventory(java.util.List.of());
		chest.setCurrentSlotMapping(new int[]{0});

		chest.respawn();

		assertFalse(chest.isLooted());
		assertEquals(0, chest.getCooldownEndTime());
		assertNull(chest.getCurrentInventory());
		assertNull(chest.getCurrentSlotMapping());
		assertFalse(chest.isUnlocked());
	}

	@Test
	@DisplayName("clearInventory only touches the inventory fields, not looted/cooldown state")
	void clearInventory_onlyTouchesInventoryFields() {
		LootChestData chest = chest(0);
		chest.startCooldown(30);
		chest.setCurrentInventory(java.util.List.of());
		chest.setCurrentSlotMapping(new int[]{0});

		chest.clearInventory();

		assertNull(chest.getCurrentInventory());
		assertNull(chest.getCurrentSlotMapping());
		assertTrue(chest.isLooted(), "clearInventory must not reset isLooted — that is respawn()'s job");
		assertTrue(chest.isOnCooldown(), "clearInventory must not touch the cooldown window");
	}

	@Test
	@DisplayName("markAsLooted sets isLooted and stamps lastOpened")
	void markAsLooted_setsLootedAndStampsTime() {
		LootChestData chest = chest(0);
		long           before = System.currentTimeMillis();

		chest.markAsLooted();

		assertTrue(chest.isLooted());
		assertTrue(chest.getLastOpened() >= before);
	}

	private static LootChestData chest(long respawnTime) {
		return LootChestData.builder()
		                    .id(UUID.randomUUID())
		                    .location(new Location(null, 0, 64, 0))
		                    .lootTableId("common")
		                    .tier(null)
		                    .respawnTime(respawnTime)
		                    .inventorySize(27)
		                    .displayName("Test Chest")
		                    .build();
	}

}
