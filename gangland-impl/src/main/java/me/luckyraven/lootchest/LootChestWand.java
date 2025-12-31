package me.luckyraven.lootchest;

import com.cryptomorin.xseries.XMaterial;
import de.tr7zw.nbtapi.NBT;
import me.luckyraven.Gangland;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.loot.data.LootChestData;
import me.luckyraven.inventory.loot.data.LootTable;
import me.luckyraven.inventory.loot.data.LootTier;
import me.luckyraven.util.ItemBuilder;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class LootChestWand {

	private final Gangland gangland;
	private final String   prefix;

	public LootChestWand(Gangland gangland) {
		this.gangland = gangland;
		this.prefix   = gangland.getShortPrefix();
	}

	public static boolean isLootChestWand(ItemStack item) {
		if (item == null || item.getType().name().contains("AIR")) return false;

		ItemBuilder builder = new ItemBuilder(item);
		return builder.hasNBTTag(LootChestWandTag.WAND_KEY.toString());
	}

	public static boolean isConfigured(ItemStack item) {
		if (!isLootChestWand(item)) return false;

		ItemBuilder builder     = new ItemBuilder(item);
		String      lootTableId = builder.getStringTagData(LootChestWandTag.LOOT_TABLE_ID.toString());
		return lootTableId != null && !lootTableId.isEmpty();
	}

	public static LootChestWand getWand(ItemStack item, Gangland gangland) {
		if (!isLootChestWand(item)) return null;

		return new LootChestWand(gangland);
	}

	public ItemStack createWand() {
		Material material = XMaterial.STICK.get();
		if (material == null) material = Material.STICK;

		ItemBuilder itemBuilder = new ItemBuilder(material);

		return itemBuilder.setDisplayName("&6&lLoot Chest Wand")
						  .setLore(List.of("&7Left-click to configure.", "&7Right-click on a block to",
										   "&7create a loot chest.", "", "&eStatus: &cNot Configured", "",
										   "&7Or use §e/" + prefix + " lootchest edit",
										   "§7while holding to configure."))
						  .addTag(LootChestWandTag.WAND_KEY.toString(), true)
						  .addTag(LootChestWandTag.CONFIGURED.toString(), false)
						  .addTag(LootChestWandTag.LOOT_TABLE_ID.toString(), "")
						  .addTag(LootChestWandTag.TIER_ID.toString(), "")
						  .addTag(LootChestWandTag.RESPAWN_TIME.toString(), 300L)
						  .addTag(LootChestWandTag.INVENTORY_SIZE.toString(), 27)
						  .addTag(LootChestWandTag.DISPLAY_NAME.toString(), "§eLoot Chest")
						  .build();
	}

	public void openConfigInventory(Player player) {
		ItemStack heldItem = player.getInventory().getItemInMainHand();

		if (!LootChestWand.isLootChestWand(heldItem)) {
			player.sendMessage("§cYou must be holding a Loot Chest Wand!");
			return;
		}

		ItemBuilder wandBuilder = new ItemBuilder(heldItem);

		// Get current settings from wand NBT
		String currentLootTable   = wandBuilder.getStringTagData(LootChestWandTag.LOOT_TABLE_ID.toString());
		String currentTier        = wandBuilder.getStringTagData(LootChestWandTag.TIER_ID.toString());
		int    currentInvSize     = wandBuilder.getIntegerTagData(LootChestWandTag.INVENTORY_SIZE.toString());
		String currentDisplayName = wandBuilder.getStringTagData(LootChestWandTag.DISPLAY_NAME.toString());

		if (currentInvSize == 0) currentInvSize = 27;
		if (currentDisplayName == null || currentDisplayName.isEmpty()) currentDisplayName = "§eLoot Chest";

		// Create config inventory
		NamespacedKey    key       = new NamespacedKey(gangland, "loot_chest_wand_config");
		InventoryHandler inventory = new InventoryHandler(gangland, "§6§lLoot Chest Wand Config", 45, player);

		// Loot Table Selection (slot 11)
		String lootTableDisplay = currentLootTable.isEmpty() ? "§cNone Selected" : "§a" + currentLootTable;
		ItemStack lootTableItem = new ItemBuilder(Material.BOOK).setDisplayName("§e§lLoot Table")
																.setLore(List.of("§7Current: " + lootTableDisplay, "",
																				 "§aClick to select a loot table"))
																.build();
		inventory.setItem(11, new ItemBuilder(lootTableItem), false, (p, inv, builder) -> {
			openLootTableSelection(p);
		});

		// Tier Selection (slot 13)
		String tierDisplay = currentTier.isEmpty() ? "§7None (Optional)" : "§a" + currentTier;
		ItemStack tierItem = new ItemBuilder(Material.DIAMOND).setDisplayName("§b§lTier")
															  .setLore(List.of("§7Current: " + tierDisplay, "",
																			   "§aClick to select a tier"))
															  .build();
		inventory.setItem(13, new ItemBuilder(tierItem), false, (p, inv, builder) -> {
			openTierSelection(p);
		});

		// Display Name (slot 15)
		String finalCurrentDisplayName = currentDisplayName;
		ItemStack displayNameItem = new ItemBuilder(Material.NAME_TAG).setDisplayName("§d§lDisplay Name")
																	  .setLore(List.of("§7Current: " +
																					   currentDisplayName, "",
																					   "§aClick to set display name"))
																	  .build();
		inventory.setItem(15, new ItemBuilder(displayNameItem), false, (p, inv, builder) -> {
			p.closeInventory();
			openAnvilInput(p, "Display Name", finalCurrentDisplayName, LootChestWandTag.DISPLAY_NAME.toString());
		});

		// Inventory Size (slot 29)
		int finalCurrentInvSize = currentInvSize;
		ItemStack invSizeItem = new ItemBuilder(Material.CHEST).setDisplayName("§6§lInventory Size")
															   .setLore(List.of("§7Current: §a" + currentInvSize, "",
																				"§aLeft-click to increase",
																				"§cRight-click to decrease"))
															   .build();
		inventory.setItem(29, new ItemBuilder(invSizeItem), false, (p, inv, builder) -> {
			handleInvSizeChange(p, true);
		}, (p, inv, builder) -> {
			handleInvSizeChange(p, false);
		});

		// Respawn Time (slot 31)
		ItemStack respawnTimeItem = new ItemBuilder(Material.CLOCK).setDisplayName("§c§lRespawn Time")
																   .setLore(List.of("§7Current: §a" +
																					getRespawnTimeFromWand(heldItem) +
																					" seconds", "",
																					"§aClick to set respawn time"))
																   .build();
		inventory.setItem(31, new ItemBuilder(respawnTimeItem), false, (p, inv, builder) -> {
			p.closeInventory();
			openAnvilInput(p, "Respawn Time (seconds)", String.valueOf(getRespawnTimeFromWand(heldItem)),
						   LootChestWandTag.RESPAWN_TIME.toString());
		});

		// Confirm Button (slot 40)
		ItemStack confirmItem = new ItemBuilder(XMaterial.LIME_WOOL.get()).setDisplayName("§a§lSave Configuration")
																		  .setLore(List.of("§7Click to save settings",
																						   "§7to your wand."))
																		  .build();
		inventory.setItem(40, new ItemBuilder(confirmItem), false, (p, inv, builder) -> {
			p.closeInventory();
			updateWandLore(p);
			p.sendMessage("§aWand configuration saved!");
		});

		// Fill empty slots
		ItemStack filler = new ItemBuilder(XMaterial.GRAY_STAINED_GLASS_PANE.get()).setDisplayName(" ").build();
		for (int i = 0; i < 45; i++) {
			if (inventory.getInventory().getItem(i) == null) {
				inventory.setItem(i, new ItemBuilder(filler), false, null);
			}
		}

		player.openInventory(inventory.getInventory());
	}

	public void createLootChestFromWand(Player player, ItemStack wand, Location location) {
		ItemBuilder builder = new ItemBuilder(wand);

		String lootTableId = builder.getStringTagData(LootChestWandTag.LOOT_TABLE_ID.toString());
		String tierId      = builder.getStringTagData(LootChestWandTag.TIER_ID.toString());
		int    invSize     = builder.getIntegerTagData(LootChestWandTag.INVENTORY_SIZE.toString());
		String displayName = builder.getStringTagData(LootChestWandTag.DISPLAY_NAME.toString());
		long   respawnTime = getRespawnTimeFromWand(wand);

		if (invSize == 0) invSize = 27;
		if (displayName == null || displayName.isEmpty()) displayName = "§eLoot Chest";

		LootChestManager manager = gangland.getInitializer().getLootChestManager();

		// Get tier if specified
		LootTier tier = null;
		if (tierId != null && !tierId.isEmpty()) {
			tier = manager.getTier(tierId).orElse(null);
		}

		// Create chest data
		LootChestData chestData = LootChestData.builder()
											   .id(UUID.randomUUID())
											   .location(location)
											   .lootTableId(lootTableId)
											   .tier(tier)
											   .respawnTime(respawnTime)
											   .inventorySize(invSize)
											   .displayName(displayName)
											   .lastOpened(0L)
											   .isLooted(false)
											   .build();

		// Register with manager
		manager.registerChest(chestData);

		player.sendMessage("§a§lLoot Chest Created!");
		player.sendMessage(
				"§7Location: §f" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
		player.sendMessage("§7Loot Table: §f" + lootTableId);
		player.sendMessage("§7Tier: §f" + (tierId == null || tierId.isEmpty() ? "None" : tierId));
	}

	private void openLootTableSelection(Player player) {
		LootChestManager      manager    = gangland.getInitializer().getLootChestManager();
		Collection<LootTable> lootTables = manager.getAllLootTables();

		NamespacedKey    key       = new NamespacedKey(gangland, "loot_table_selection");
		int              size      = Math.min(54, ((lootTables.size() / 9) + 1) * 9 + 9);
		InventoryHandler inventory = new InventoryHandler(gangland, "§6§lSelect Loot Table", size, player);

		int slot = 0;
		for (LootTable table : lootTables) {
			ItemStack item = new ItemBuilder(Material.PAPER).setDisplayName("§e" + table.getDisplayName())
															.setLore(List.of("§7ID: §f" + table.getId(), "§7Items: §f" +
																										 table.getItemReferences()
																											  .size(),
																			 "§7Min Items: §f" + table.getMinItems(),
																			 "§7Max Items: §f" + table.getMaxItems(),
																			 "", "§aClick to select"))
															.build();

			String tableId = table.getId();
			inventory.setItem(slot++, new ItemBuilder(item), false, (p, inv, builder) -> {
				setWandNBT(p, LootChestWandTag.LOOT_TABLE_ID.toString(), tableId);
				p.sendMessage("§aSelected loot table: §e" + tableId);
				openConfigInventory(p);
			});

			if (slot >= size - 9) break;
		}

		// Back button
		ItemStack backItem = new ItemBuilder(Material.ARROW).setDisplayName("§c§lBack").build();
		inventory.setItem(size - 5, new ItemBuilder(backItem), false, (p, inv, builder) -> {
			openConfigInventory(p);
		});

		player.openInventory(inventory.getInventory());
	}

	private void openTierSelection(Player player) {
		LootChestManager     manager = gangland.getInitializer().getLootChestManager();
		Collection<LootTier> tiers   = manager.getAllTiers();

		NamespacedKey    key       = new NamespacedKey(gangland, "tier_selection");
		int              size      = Math.min(54, ((tiers.size() / 9) + 2) * 9);
		InventoryHandler inventory = new InventoryHandler(gangland, "§b§lSelect Tier", size, player);

		// None option
		ItemStack noneItem = new ItemBuilder(Material.BARRIER).setDisplayName("§7§lNo Tier")
															  .setLore(List.of("§7Remove tier requirement", "",
																			   "§aClick to select"))
															  .build();
		inventory.setItem(0, new ItemBuilder(noneItem), false, (p, inv, builder) -> {
			setWandNBT(p, LootChestWandTag.TIER_ID.toString(), "");
			p.sendMessage("§aTier removed from wand.");
			openConfigInventory(p);
		});

		int slot = 1;
		for (LootTier tier : tiers) {
			ItemStack item = new ItemBuilder(Material.DIAMOND).setDisplayName("§b" + tier.displayName())
															  .setLore(List.of("§7ID: §f" + tier.id(),
																			   "§7Level: §f" + tier.level(),
																			   "§7Unlock: §f" +
																			   tier.unlockRequirement().name(), "",
																			   "§aClick to select"))
															  .build();

			String tierId = tier.id();
			inventory.setItem(slot++, new ItemBuilder(item), false, (p, inv, builder) -> {
				setWandNBT(p, LootChestWandTag.TIER_ID.toString(), tierId);
				p.sendMessage("§aSelected tier: §e" + tierId);
				openConfigInventory(p);
			});

			if (slot >= size - 9) break;
		}

		// Back button
		ItemStack backItem = new ItemBuilder(Material.ARROW).setDisplayName("§c§lBack").build();
		inventory.setItem(size - 5, new ItemBuilder(backItem), false, (p, inv, builder) -> {
			openConfigInventory(p);
		});

		player.openInventory(inventory.getInventory());
	}

	private void openAnvilInput(Player player, String title, String defaultText, String nbtKey) {
		new AnvilGUI.Builder().onClick((slot, stateSnapshot) -> {
			if (slot != AnvilGUI.Slot.OUTPUT) {
				return Collections.emptyList();
			}

			String input = stateSnapshot.getText();

			if (nbtKey.equals(LootChestWandTag.RESPAWN_TIME.toString())) {
				try {
					long respawnTime = Long.parseLong(input);
					setWandNBT(stateSnapshot.getPlayer(), nbtKey, respawnTime);
					stateSnapshot.getPlayer().sendMessage("§aRespawn time set to: §e" + respawnTime + " seconds");
				} catch (NumberFormatException e) {
					stateSnapshot.getPlayer().sendMessage("§cInvalid number! Please enter a valid number.");
				}
			} else {
				setWandNBT(stateSnapshot.getPlayer(), nbtKey, input);
				stateSnapshot.getPlayer().sendMessage("§a" + title + " set to: §e" + input);
			}

			return List.of(AnvilGUI.ResponseAction.close(), AnvilGUI.ResponseAction.run(() -> {
				// Delay to ensure inventory closes properly
				gangland.getServer().getScheduler().runTaskLater(gangland, () -> {
					openConfigInventory(stateSnapshot.getPlayer());
				}, 1L);
			}));
		}).text(defaultText).title(title).plugin(gangland).open(player);
	}

	private void handleInvSizeChange(Player player, boolean increase) {
		ItemStack heldItem = player.getInventory().getItemInMainHand();
		if (!LootChestWand.isLootChestWand(heldItem)) return;

		ItemBuilder builder     = new ItemBuilder(heldItem);
		int         currentSize = builder.getIntegerTagData(LootChestWandTag.INVENTORY_SIZE.toString());
		if (currentSize == 0) currentSize = 27;

		if (increase) {
			currentSize = Math.min(54, currentSize + 9);
		} else {
			currentSize = Math.max(9, currentSize - 9);
		}

		setWandNBT(player, LootChestWandTag.INVENTORY_SIZE.toString(), currentSize);
		player.sendMessage("§aInventory size set to: §e" + currentSize);
		openConfigInventory(player);
	}

	private long getRespawnTimeFromWand(ItemStack item) {
		if (!LootChestWand.isLootChestWand(item)) return 300L;

		ItemBuilder builder = new ItemBuilder(item);
		int         value   = builder.getIntegerTagData(LootChestWandTag.RESPAWN_TIME.toString());
		return value == 0 ? 300L : value;
	}

	private void updateWandLore(Player player) {
		ItemStack heldItem = player.getInventory().getItemInMainHand();
		if (!LootChestWand.isLootChestWand(heldItem)) return;

		ItemBuilder builder = new ItemBuilder(heldItem);

		String lootTableId = builder.getStringTagData(LootChestWandTag.LOOT_TABLE_ID.toString());
		String tierId      = builder.getStringTagData(LootChestWandTag.TIER_ID.toString());
		int    invSize     = builder.getIntegerTagData(LootChestWandTag.INVENTORY_SIZE.toString());
		String displayName = builder.getStringTagData(LootChestWandTag.DISPLAY_NAME.toString());
		long   respawnTime = getRespawnTimeFromWand(heldItem);

		boolean configured = lootTableId != null && !lootTableId.isEmpty();

		List<String> lore = new ArrayList<>();
		lore.add("§7Left-click to configure.");
		lore.add("§7Right-click on a block to");
		lore.add("§7create a loot chest.");
		lore.add("");

		if (configured) {
			lore.add("§eStatus: §aConfigured");
			lore.add("");
			lore.add("§7Loot Table: §f" + lootTableId);
			lore.add("§7Tier: §f" + (tierId == null || tierId.isEmpty() ? "None" : tierId));
			lore.add("§7Size: §f" + invSize);
			lore.add("§7Respawn: §f" + respawnTime + "s");
			lore.add("§7Name: §f" + displayName);
		} else {
			lore.add("§eStatus: §cNot Configured");
		}

		lore.add("");
		lore.add("§7Or use §e/" + prefix + " lootchest edit");
		lore.add("§7while holding to configure.");

		builder.setLore(lore);
		setWandNBT(player, LootChestWandTag.CONFIGURED.toString(), configured);

		// Update the item in the player's hand
		player.getInventory().setItemInMainHand(builder.build());
	}

	private void setWandNBT(Player player, String key, Object value) {
		ItemStack heldItem = player.getInventory().getItemInMainHand();
		if (!LootChestWand.isLootChestWand(heldItem)) return;

		NBT.modify(heldItem, nbt -> {
			if (value instanceof String) {
				nbt.setString(key, (String) value);
			} else if (value instanceof Long) {
				nbt.setLong(key, (Long) value);
			} else if (value instanceof Integer) {
				nbt.setInteger(key, (Integer) value);
			} else if (value instanceof Boolean) {
				nbt.setBoolean(key, (Boolean) value);
			}
		});
	}

}
