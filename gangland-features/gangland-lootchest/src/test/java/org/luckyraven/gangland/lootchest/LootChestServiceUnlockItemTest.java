package org.luckyraven.gangland.lootchest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.luckyraven.gangland.lootchest.LootChestService.OpenResult;
import org.luckyraven.gangland.lootchest.config.LootChestConfig;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.data.LootTable;
import org.luckyraven.gangland.lootchest.data.LootTier;
import org.luckyraven.gangland.lootchest.support.TestItemParsers;
import org.luckyraven.keystone.hologram.Hologram;
import org.luckyraven.keystone.hologram.HologramService;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how {@link LootChestService} resolves the unlock item of a tiered chest (the NBT {@code loot_key} value a
 * LOCKPICK / KEY tier asks for) in both places that need it: the pre-check in {@code checkUnlockRequirement} and the
 * consumption in {@code openChestDirectly}. Reached through the public {@code tryOpenChest}.
 *
 * <p>The sentinel for "the unlock check passed" is {@link OpenResult#INVALID_LOOT_TABLE}: no loot table is registered
 * in the check-only tests, so a chest that gets past the tier check stops right after it.
 */
class LootChestServiceUnlockItemTest {

	private static final String PREFIX = "glw";

	private final Map<ItemStack, String> tagByStack = new IdentityHashMap<>();

	private MockedStatic<Bukkit>            bukkit;
	private MockedConstruction<ItemBuilder> builders;
	private HologramService                 holograms;
	private LootChestManager                service;
	private Player                          player;
	private PlayerInventory                 playerInventory;

	@BeforeEach
	void setUp() {
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
		      .thenReturn(mock(Inventory.class));
		bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

		// ItemBuilder wraps NBT-API, which needs a live server; read the loot_key tag from a side table instead.
		builders = mockConstruction(ItemBuilder.class, (builder, context) -> {
			String tag = tagByStack.get((ItemStack) context.arguments().get(0));
			when(builder.hasNBTTag("loot_key")).thenReturn(tag != null);
			when(builder.getStringTagData("loot_key")).thenReturn(tag);
		});

		holograms = mock(HologramService.class);
		when(holograms.createHologram(any(Location.class), any(String[].class))).thenReturn(mock(Hologram.class));
		service = new LootChestManager(mock(JavaPlugin.class), PREFIX, holograms, mock(RepositoryRegistry.class),
		                               TestItemParsers.materialOnly(), null, mock(InventoryService.class));
		// The session-start handler plays the opening sound from the config.
		service.setConfig(LootChestConfig.builder().tiers(Map.of()).lootTables(Map.of())
		                                 .openingSound("BLOCK_CHEST_OPEN").closingSound("BLOCK_CHEST_CLOSE").build());

		player          = mock(Player.class);
		playerInventory = mock(PlayerInventory.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getInventory()).thenReturn(playerInventory);
	}

	@AfterEach
	void tearDown() {
		builders.close();
		bukkit.close();
	}

	@ParameterizedTest(name = "{0} tier {1} with unlockItemId {2}, holding {3} gives {4}")
	@DisplayName("the unlock item a LOCKPICK / KEY tier asks for: its own unlockItemId, else lockpick / key_<tier id>")
	@CsvSource(nullValues = "null", value = {
			"LOCKPICK, pick, null, null, REQUIRES_LOCKPICK",
			"LOCKPICK, pick, null, lockpick, INVALID_LOOT_TABLE",
			"LOCKPICK, pick, null, key_pick, REQUIRES_LOCKPICK",
			"LOCKPICK, pick, skeleton, lockpick, REQUIRES_LOCKPICK",
			"LOCKPICK, pick, skeleton, skeleton, INVALID_LOOT_TABLE",
			"KEY, vault, null, null, REQUIRES_KEY",
			"KEY, vault, null, key_vault, INVALID_LOOT_TABLE",
			"KEY, vault, null, vault, REQUIRES_KEY",
			"KEY, vault, null, lockpick, REQUIRES_KEY",
			"KEY, vault, gold, key_vault, REQUIRES_KEY",
			"KEY, vault, gold, gold, INVALID_LOOT_TABLE"
	})
	void tryOpenChest_unlockItemIsMatchedByItsId(LootTier.UnlockRequirement requirement, String tierId,
	                                            String unlockItemId, String heldTag, OpenResult expected) {
		LootTier tier = new LootTier(tierId, "Tier", 1, requirement, unlockItemId);
		holdItems(heldTag == null ? new ItemStack[]{null} : new ItemStack[]{tagged(heldTag, 1)});

		assertEquals(expected, service.tryOpenChest(player, chest(tier)));
	}

	@ParameterizedTest(name = "{0} tier {1}, permission {2} gives {3}")
	@DisplayName("NONE passes, PERMISSION asks the player for glw.lootchest.tier.<tier id>")
	@CsvSource({
			"NONE, plain, false, INVALID_LOOT_TABLE",
			"PERMISSION, vip, true, INVALID_LOOT_TABLE",
			"PERMISSION, vip, false, NO_PERMISSION"
	})
	void tryOpenChest_noneAndPermissionTiers(LootTier.UnlockRequirement requirement, String tierId, boolean granted,
	                                        OpenResult expected) {
		when(player.hasPermission(PREFIX + ".lootchest.tier." + tierId)).thenReturn(granted);
		holdItems(new ItemStack[]{null});

		assertEquals(expected, service.tryOpenChest(player, chest(new LootTier(tierId, "Tier", 1, requirement))));
	}

	@Test
	@DisplayName("an already unlocked LOCKPICK chest passes without the player holding anything")
	void tryOpenChest_unlockedLockpickChest_needsNoItem() {
		holdItems(new ItemStack[]{null});
		LootChestData chest = chest(new LootTier("pick", "Tier", 1, LootTier.UnlockRequirement.LOCKPICK));
		chest.setUnlocked(true);

		assertEquals(OpenResult.INVALID_LOOT_TABLE, service.tryOpenChest(player, chest));
	}

	@ParameterizedTest(name = "{0} tier {1} with unlockItemId {2} burns the {3} item")
	@DisplayName("the first successful open burns one unit of the unlock item, unlocks the chest, then shows the hologram")
	@CsvSource(nullValues = "null", value = {
			"LOCKPICK, pick, null, lockpick",
			"LOCKPICK, pick, skeleton, skeleton",
			"KEY, vault, null, key_vault",
			"KEY, vault, gold, gold"
	})
	void tryOpenChest_firstOpen_consumesTheUnlockItem(LootTier.UnlockRequirement requirement, String tierId,
	                                                 String unlockItemId, String burnedTag) {
		registerLootTable();
		LootTier  tier      = new LootTier(tierId, "Tier", 1, requirement, unlockItemId);
		ItemStack decoy     = tagged("decoy", 5);
		ItemStack burned    = tagged(burnedTag, 2);
		LootChestData chest = chest(tier);
		// The chest must still be locked when the item is burned, and unlocked by the time the hologram is drawn.
		doAnswer(invocation -> {
			assertFalse(chest.isUnlocked(), "the item is consumed before the chest flips to unlocked");
			return null;
		}).when(burned).setAmount(1);
		holdItems(new ItemStack[]{decoy, burned});

		OpenResult result = service.tryOpenChest(player, chest);

		assertEquals(OpenResult.SUCCESS, result);
		verify(burned).setAmount(1);
		verify(decoy, never()).setAmount(anyInt());
		assertTrue(chest.isUnlocked());
		assertTrue(service.getActiveSession(player).isPresent());
		ArgumentCaptor<String[]> lines = ArgumentCaptor.forClass(String[].class);
		verify(holograms).createHologram(any(Location.class), lines.capture());
		assertTrue(lines.getValue()[1].contains("Unlocked"), String.join(" | ", lines.getValue()));
	}

	@Test
	@DisplayName("a single burned item is removed from its slot instead of shrinking the stack")
	void tryOpenChest_lastUnitOfTheUnlockItem_clearsTheSlot() {
		registerLootTable();
		ItemStack decoy  = tagged("decoy", 5);
		ItemStack burned = tagged("key_vault", 1);
		holdItems(new ItemStack[]{decoy, burned});

		OpenResult result = service.tryOpenChest(player, chest(new LootTier("vault", "Tier", 1,
		                                                                    LootTier.UnlockRequirement.KEY)));

		assertEquals(OpenResult.SUCCESS, result);
		verify(playerInventory).setItem(1, null);
		verify(burned, never()).setAmount(anyInt());
	}

	@ParameterizedTest(name = "{0} tier consumes nothing and stays locked")
	@DisplayName("NONE and PERMISSION tiers never burn an item, never flip the unlocked flag and draw no hologram")
	@CsvSource({"NONE", "PERMISSION"})
	void tryOpenChest_nonConsumableTiers_doNotBurnAnything(LootTier.UnlockRequirement requirement) {
		registerLootTable();
		when(player.hasPermission(PREFIX + ".lootchest.tier.t")).thenReturn(true);
		ItemStack bystander = tagged("lockpick", 3);
		holdItems(new ItemStack[]{bystander});
		LootChestData chest = chest(new LootTier("t", "Tier", 1, requirement));

		assertEquals(OpenResult.SUCCESS, service.tryOpenChest(player, chest));

		verify(bystander, never()).setAmount(anyInt());
		assertFalse(chest.isUnlocked());
		verify(holograms, never()).createHologram(any(Location.class), any(String[].class));
	}

	@Test
	@DisplayName("a chest already unlocked this cycle is opened without burning another item")
	void tryOpenChest_alreadyUnlocked_burnsNothing() {
		registerLootTable();
		ItemStack spare = tagged("lockpick", 3);
		holdItems(new ItemStack[]{spare});
		LootChestData chest = chest(new LootTier("pick", "Tier", 1, LootTier.UnlockRequirement.LOCKPICK));
		chest.setUnlocked(true);

		assertEquals(OpenResult.SUCCESS, service.tryOpenChest(player, chest));

		verify(spare, never()).setAmount(anyInt());
		verify(holograms, never()).createHologram(any(Location.class), any(String[].class));
	}

	private void registerLootTable() {
		service.registerLootTable(new LootTable("common", "Common", List.of(), 1, 1, List.of(), Map.of()));
	}

	private void holdItems(ItemStack[] contents) {
		when(playerInventory.getContents()).thenReturn(contents);
	}

	private ItemStack tagged(String lootKey, int amount) {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getAmount()).thenReturn(amount);
		tagByStack.put(stack, lootKey);
		return stack;
	}

	/** A chest that still holds an item, so the open path reuses it instead of rolling loot. */
	private LootChestData chest(LootTier tier) {
		ItemStack inside = mock(ItemStack.class);
		when(inside.getType()).thenReturn(Material.STONE);

		LootChestData chest = LootChestData.builder().id(UUID.randomUUID()).location(new Location(null, 1, 2, 3))
		                                   .lootTableId("common").tier(tier).inventorySize(27).displayName("Chest")
		                                   .build();
		chest.setCurrentInventory(List.of(inside));
		return chest;
	}
}
