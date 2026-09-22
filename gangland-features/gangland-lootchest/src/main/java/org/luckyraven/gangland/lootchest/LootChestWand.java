package org.luckyraven.gangland.lootchest;

import com.cryptomorin.xseries.XMaterial;
import de.tr7zw.nbtapi.NBT;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.click.ClickContext;
import org.luckyraven.keystone.inventory.component.BorderComponent;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.page.PageConfig;
import org.luckyraven.keystone.inventory.page.PagedRegion;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.data.LootTable;
import org.luckyraven.gangland.lootchest.data.LootTier;
import org.luckyraven.gangland.lootchest.item.LootItemReference;

import java.util.*;

import static org.luckyraven.gangland.lootchest.LootChestWandTag.*;

public class LootChestWand {

	private static final int PREVIEW_SIZE = 54;
	private static final int PREVIEW_ROWS = PREVIEW_SIZE / 9;

	// PagedRegion interior grid: rows 1..4, cols 1..7 (28 entries per page) — same footprint the old
	// hand-rolled PREVIEW_INTERIOR_SLOTS array covered, now driven by PageConfig/PagedRegion math (LS migration).
	private static final int PREVIEW_FIRST_ROW = 1;
	private static final int PREVIEW_LAST_ROW  = 4;
	private static final int PREVIEW_FIRST_COL = 1;
	private static final int PREVIEW_LAST_COL  = 7;

	private static final int PREVIEW_SLOT_BACK      = 45;
	private static final int PREVIEW_SLOT_PREV      = 48;
	private static final int PREVIEW_SLOT_PAGE_INFO = 49;
	private static final int PREVIEW_SLOT_NEXT      = 50;

	private static final SoundEffect PREVIEW_PAGE_SOUND = new SoundEffect(
			SoundEffect.SoundType.VANILLA, "UI_BUTTON_CLICK", 0.6f, 1.2f);

	private final JavaPlugin       gangland;
	private final LootChestManager lootChestManager;
	private final String           prefix;

	public LootChestWand(JavaPlugin gangland, LootChestManager lootChestManager, String prefix) {
		this.gangland         = gangland;
		this.lootChestManager = lootChestManager;
		this.prefix           = prefix;
	}

	public static boolean isLootChestWand(ItemStack item) {
		if (item == null || item.getType().equals(Material.AIR) || item.getAmount() == 0) return false;

		ItemBuilder builder = new ItemBuilder(item);
		return builder.hasNBTTag(WAND_KEY.toString());
	}

	public static boolean isConfigured(ItemStack item) {
		if (!isLootChestWand(item)) return false;

		ItemBuilder builder     = new ItemBuilder(item);
		String      lootTableId = builder.getStringTagData(LOOT_TABLE_ID.toString());
		return lootTableId != null && !lootTableId.isEmpty();
	}

	public static LootChestWand getWand(ItemStack item, JavaPlugin gangland, LootChestManager lootChestManager) {
		if (!isLootChestWand(item)) return null;

		return new LootChestWand(gangland, lootChestManager, GanglandApi.SHORT_PREFIX);
	}

	public ItemStack createWand() {
		Material material = XMaterial.STICK.get();
		if (material == null) material = Material.STICK;

		ItemBuilder itemBuilder = new ItemBuilder(material);

		List<String> lore = List.of("&7Left-click to configure.", "&7Right-click on a block to",
		                            "&7create a loot chest.", "", "&eStatus: &cNot Configured", "",
		                            "&7Or use &e/" + prefix + " lootchest edit", "&7while holding to configure.");
		return itemBuilder.setDisplayName("&6&lLoot Chest Wand")
		                  .setLore(lore)
		                  .addTag(WAND_KEY.toString(), true)
		                  .addTag(CONFIGURED.toString(), false)
		                  .addTag(LOOT_TABLE_ID.toString(), "")
		                  .addTag(TIER_ID.toString(), "")
		                  .addTag(RESPAWN_TIME.toString(), 300L)
		                  .addTag(INVENTORY_SIZE.toString(), 27)
		                  .addTag(DISPLAY_NAME.toString(), "&eLoot Chest")
		                  .build();
	}

	/**
	 * Opens the wand config menu. {@code fillMaterial}/{@code fillName} replace the old {@code Fill} record (deleted
	 * with {@code gangland-ui/inventory-api}). LS-30: the wand being configured is identified once here, by the
	 * hotbar slot it occupied at the moment this menu opened ({@code wandSlot}), and threaded through every nested
	 * screen/anvil prompt below — every read/write re-fetches {@code player.getInventory().getItem(wandSlot)}
	 * rather than {@code getItemInMainHand()}, so switching the held hotbar slot mid-configuration can no longer
	 * misdirect an edit onto whatever the player happens to be holding when they finally click "save".
	 */
	public void openConfigInventory(Player player, String fillMaterial, String fillName) {
		int       wandSlot = player.getInventory().getHeldItemSlot();
		ItemStack heldItem = player.getInventory().getItem(wandSlot);

		if (!LootChestWand.isLootChestWand(heldItem)) {
			player.sendMessage(ChatUtil.color("&cYou must be holding a Loot Chest Wand!"));
			return;
		}

		ItemBuilder wandBuilder = new ItemBuilder(heldItem);

		// Get current settings from wand NBT
		String currentLootTable   = wandBuilder.getStringTagData(LOOT_TABLE_ID.toString());
		String currentTier        = wandBuilder.getStringTagData(TIER_ID.toString());
		int    currentInvSize     = wandBuilder.getIntegerTagData(INVENTORY_SIZE.toString());
		String currentDisplayName = wandBuilder.getStringTagData(DISPLAY_NAME.toString());

		if (currentInvSize == 0) currentInvSize = 27;
		if (currentDisplayName == null || currentDisplayName.isEmpty()) currentDisplayName = "&eLoot Chest";

		ChestMenuBuilder builder = ChestMenu.builder(lootChestManager.getInventoryService())
		                                    .title("&6&lLoot Chest Wand Config")
		                                    .rows(5);

		// Loot Table Selection (slot 11)
		var lootTableDisplay = currentLootTable.isEmpty() ? "&cNone Selected" : "&a" + currentLootTable;
		var lootTableLore    = List.of("&7Current: " + lootTableDisplay, "", "&aClick to select a loot table");
		var lootTableItem    = new ItemBuilder(Material.BOOK).setDisplayName("&e&lLoot Table").setLore(lootTableLore);

		builder.slot(11, ItemComponent.of(lootTableItem).onAnyClick(ctx ->
				openLootTableSelection(ctx.player(), fillMaterial, fillName, wandSlot)));

		// Tier Selection (slot 13)
		var tierDisplay = currentTier.isEmpty() ? "&7None (Optional)" : "&a" + currentTier;
		var tierLore     = List.of("&7Current: " + tierDisplay, "", "&aClick to select a tier");
		var tierItem     = new ItemBuilder(Material.DIAMOND).setDisplayName("&b&lTier").setLore(tierLore);

		builder.slot(13, ItemComponent.of(tierItem).onAnyClick(ctx ->
				openTierSelection(ctx.player(), fillMaterial, fillName, wandSlot)));

		// Display Name (slot 15)
		var finalDisplayName = currentDisplayName;
		var displayNameLore  = List.of("&7Current: " + currentDisplayName, "", "&aClick to set display name");
		var displayNameItem  = new ItemBuilder(Material.NAME_TAG).setDisplayName("&d&lDisplay Name")
		                                                         .setLore(displayNameLore);

		builder.slot(15, ItemComponent.of(displayNameItem).onAnyClick(ctx -> {
			ctx.closeMenu();
			openAnvilInput(ctx.player(), "Display Name", finalDisplayName, DISPLAY_NAME.toString(), fillMaterial,
			               fillName, wandSlot);
		}));

		// Inventory Size (slot 29)
		var invSizeLore = List.of("&7Current: &a" + currentInvSize, "", "&aLeft-click to increase",
		                         "&cRight-click to decrease");
		var invSizeItem = new ItemBuilder(Material.CHEST).setDisplayName("&6&lInventory Size").setLore(invSizeLore);

		builder.slot(29, ItemComponent.of(invSizeItem)
		                              .onLeftClick(ctx ->
				handleInvSizeChange(ctx.player(), true, fillMaterial, fillName, wandSlot))
		                              .onRightClick(ctx ->
				handleInvSizeChange(ctx.player(), false, fillMaterial, fillName, wandSlot)));

		// Respawn Time (slot 31)
		long respawnTime    = getRespawnTimeFromWand(heldItem);
		var  respawnTimeLore = List.of("&7Current: &a" + respawnTime + " seconds", "", "&aClick to set respawn time");
		var  respawnTimeItem = new ItemBuilder(Material.CLOCK).setDisplayName("&c&lRespawn Time")
		                                                      .setLore(respawnTimeLore);

		builder.slot(31, ItemComponent.of(respawnTimeItem).onAnyClick(ctx -> {
			ctx.closeMenu();
			openAnvilInput(ctx.player(), "Respawn Time (seconds)", String.valueOf(respawnTime),
			               RESPAWN_TIME.toString(), fillMaterial, fillName, wandSlot);
		}));

		// Confirm Button (slot 40)
		var confirmLore = List.of("&7Click to save settings", "&7to your wand.");
		var confirmItem = new ItemBuilder(XMaterial.LIME_WOOL.get()).setDisplayName("&a&lSave Configuration")
		                                                            .setLore(confirmLore);

		builder.slot(40, ItemComponent.of(confirmItem).onAnyClick(ctx -> {
			ctx.closeMenu();
			updateWandLore(ctx.player(), wandSlot);
			ctx.player().sendMessage(ChatUtil.color("&aWand configuration saved!"));
		}));

		builder.fill(FillComponent.of(materialOf(fillMaterial)).name(fillName));

		builder.build().open(player);
	}

	public void createLootChestFromWand(Player player, ItemStack wand, Location location) {
		ItemBuilder builder = new ItemBuilder(wand);

		String lootTableId = builder.getStringTagData(LOOT_TABLE_ID.toString());
		String tierId      = builder.getStringTagData(TIER_ID.toString());
		int    invSize     = builder.getIntegerTagData(INVENTORY_SIZE.toString());
		String displayName = builder.getStringTagData(DISPLAY_NAME.toString());
		long   respawnTime = getRespawnTimeFromWand(wand);

		if (invSize == 0) invSize = 27;
		if (displayName == null || displayName.isEmpty()) displayName = "&eLoot Chest";

		// Get tier if specified
		LootTier tier = null;
		if (tierId != null && !tierId.isEmpty()) {
			tier = lootChestManager.getTier(tierId).orElse(null);
		}

		// Create chest data
		var chestData = LootChestData.builder()
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
		lootChestManager.registerChest(chestData);

		player.sendMessage(ChatUtil.color("&a&lLoot Chest Created!"));
		player.sendMessage(ChatUtil.color(
				"&7Location: &f" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()));
		player.sendMessage(ChatUtil.color("&7Loot Table: &f" + lootTableId));
		player.sendMessage(
				ChatUtil.color("&7Tier: &f" + (tierId == null || tierId.isEmpty() ? "None" : tierId)));
	}

	private void openLootTableSelection(Player player, String fillMaterial, String fillName, int wandSlot) {
		Collection<LootTable> lootTables = lootChestManager.getAllLootTables();

		int size = Math.min(54, ((lootTables.size() / 9) + 1) * 9 + 9);
		int rows = size / 9;

		ChestMenuBuilder builder = ChestMenu.builder(lootChestManager.getInventoryService())
		                                    .title("&6&lSelect Loot Table")
		                                    .rows(rows);

		int slot = 0;
		for (LootTable table : lootTables) {
			var lore = List.of("&7ID: &f" + table.getId(), "&7Items: &f" + table.getItemReferences().size(),
			                   "&7Min Items: &f" + table.getMinItems(), "&7Max Items: &f" + table.getMaxItems(), "",
			                   "&aLeft-click to select", "&bRight-click to preview contents");
			var item = new ItemBuilder(Material.PAPER).setDisplayName("&e" + table.getDisplayName()).setLore(lore);

			String tableId = table.getId();
			builder.slot(slot++, ItemComponent.of(item)
			                                  .onLeftClick(ctx -> {
				setWandNBT(ctx.player(), wandSlot, LOOT_TABLE_ID.toString(), tableId);
				ctx.player().sendMessage(ChatUtil.color("&aSelected loot table: &e" + tableId));
				openConfigInventory(ctx.player(), fillMaterial, fillName);
			                                  })
			                                  .onRightClick(ctx ->
					openLootTablePreview(ctx.player(), tableId, fillMaterial, fillName, wandSlot, 0)));

			if (slot >= size - 9) break;
		}

		// Back button
		var backItem = new ItemBuilder(Material.ARROW).setDisplayName("&c&lBack");
		builder.slot(size - 5, ItemComponent.of(backItem).onAnyClick(ctx ->
				openConfigInventory(ctx.player(), fillMaterial, fillName)));

		builder.build().open(player);
	}

	private void openTierSelection(Player player, String fillMaterial, String fillName, int wandSlot) {
		Collection<LootTier> tiers = lootChestManager.getAllTiers();

		int size = Math.min(54, ((tiers.size() / 9) + 2) * 9);
		int rows = size / 9;

		ChestMenuBuilder builder = ChestMenu.builder(lootChestManager.getInventoryService())
		                                    .title("&b&lSelect Tier")
		                                    .rows(rows);

		// None option
		var lore     = List.of("&7Remove tier requirement", "", "&aClick to select");
		var noneItem = new ItemBuilder(Material.BARRIER).setDisplayName("&7&lNo Tier").setLore(lore);

		builder.slot(0, ItemComponent.of(noneItem).onAnyClick(ctx -> {
			setWandNBT(ctx.player(), wandSlot, TIER_ID.toString(), "");
			ctx.player().sendMessage(ChatUtil.color("&aTier removed from wand."));
			openConfigInventory(ctx.player(), fillMaterial, fillName);
		}));

		int slot = 1;
		for (LootTier tier : tiers) {
			var lore1 = List.of("&7ID: &f" + tier.id(), "&7Level: &f" + tier.level(),
			                    "&7Unlock: &f" + tier.unlockRequirement().name(), "", "&aClick to select");
			var item = new ItemBuilder(Material.DIAMOND).setDisplayName("&b" + tier.displayName()).setLore(lore1);

			String tierId = tier.id();

			builder.slot(slot++, ItemComponent.of(item).onAnyClick(ctx -> {
				setWandNBT(ctx.player(), wandSlot, TIER_ID.toString(), tierId);
				ctx.player().sendMessage(ChatUtil.color("&aSelected tier: &e" + tierId));
				openConfigInventory(ctx.player(), fillMaterial, fillName);
			}));

			if (slot >= size - 9) break;
		}

		// Back button
		var backItem = new ItemBuilder(Material.ARROW).setDisplayName("&c&lBack");
		builder.slot(size - 5, ItemComponent.of(backItem).onAnyClick(ctx ->
				openConfigInventory(ctx.player(), fillMaterial, fillName)));

		builder.build().open(player);
	}

	private void openAnvilInput(Player player, String title, String defaultText, String nbtKey, String fillMaterial,
	                            String fillName, int wandSlot) {
		new AnvilGUI.Builder().onClick((slot, stateSnapshot) -> {
			if (slot != AnvilGUI.Slot.OUTPUT) {
				return Collections.emptyList();
			}

			String input = stateSnapshot.getText();

			if (nbtKey.equals(RESPAWN_TIME.toString())) {
				try {
					long respawnTime = Long.parseLong(input);
					setWandNBT(stateSnapshot.getPlayer(), wandSlot, nbtKey, respawnTime);
					stateSnapshot.getPlayer()
					             .sendMessage(
									 ChatUtil.color(
											 "&aRespawn time set to: &e" + respawnTime + " seconds"));
				} catch (NumberFormatException e) {
					stateSnapshot.getPlayer()
					             .sendMessage(ChatUtil.color("&cInvalid number! Please enter a valid number."));
				}
			} else {
				setWandNBT(stateSnapshot.getPlayer(), wandSlot, nbtKey, input);
				stateSnapshot.getPlayer().sendMessage(ChatUtil.color("&a" + title + " set to: &e" + input));
			}

			return List.of(AnvilGUI.ResponseAction.close(), AnvilGUI.ResponseAction.run(() -> {
				// Delay to ensure inventory closes properly
				gangland.getServer().getScheduler().runTaskLater(gangland, () -> {
					openConfigInventory(stateSnapshot.getPlayer(), fillMaterial, fillName);
				}, 1L);
			}));
		}).text(ChatUtil.color(defaultText)).title(ChatUtil.color(title)).plugin(gangland).open(player);
	}

	private void handleInvSizeChange(Player player, boolean increase, String fillMaterial, String fillName,
	                                 int wandSlot) {
		ItemStack heldItem = player.getInventory().getItem(wandSlot);
		if (!LootChestWand.isLootChestWand(heldItem)) return;

		ItemBuilder builder     = new ItemBuilder(heldItem);
		int         currentSize = builder.getIntegerTagData(INVENTORY_SIZE.toString());
		if (currentSize == 0) currentSize = 27;

		if (increase) {
			currentSize = Math.min(54, currentSize + 9);
		} else {
			currentSize = Math.max(9, currentSize - 9);
		}

		setWandNBT(player, wandSlot, INVENTORY_SIZE.toString(), currentSize);
		player.sendMessage(ChatUtil.color("&aInventory size set to: &e" + currentSize));
		openConfigInventory(player, fillMaterial, fillName);
	}

	private long getRespawnTimeFromWand(ItemStack item) {
		if (!LootChestWand.isLootChestWand(item)) return 300L;

		ItemBuilder builder = new ItemBuilder(item);
		int         value   = builder.getIntegerTagData(RESPAWN_TIME.toString());

		return value == 0 ? 300L : value;
	}

	private void updateWandLore(Player player, int wandSlot) {
		ItemStack heldItem = player.getInventory().getItem(wandSlot);
		if (!LootChestWand.isLootChestWand(heldItem)) return;

		ItemBuilder builder = new ItemBuilder(heldItem);

		String lootTableId = builder.getStringTagData(LOOT_TABLE_ID.toString());
		String tierId      = builder.getStringTagData(TIER_ID.toString());
		int    invSize     = builder.getIntegerTagData(INVENTORY_SIZE.toString());
		String displayName = builder.getStringTagData(DISPLAY_NAME.toString());
		long   respawnTime = getRespawnTimeFromWand(heldItem);

		boolean configured = lootTableId != null && !lootTableId.isEmpty();

		List<String> lore = new ArrayList<>();
		lore.add("&7Left-click to configure.");
		lore.add("&7Right-click on a block to");
		lore.add("&7create a loot chest.");
		lore.add("");

		if (configured) {
			lore.add("&eStatus: &aConfigured");
			lore.add("");
			lore.add("&7Loot Table: &f" + lootTableId);
			lore.add("&7Tier: &f" + (tierId == null || tierId.isEmpty() ? "None" : tierId));
			lore.add("&7Size: &f" + invSize);
			lore.add("&7Respawn: &f" + respawnTime + "s");
			lore.add("&7Name: &f" + displayName);
		} else {
			lore.add("&eStatus: &cNot Configured");
		}

		lore.add("");
		lore.add("&7Or use &e/" + prefix + " lootchest edit");
		lore.add("&7while holding to configure.");

		builder.setLore(lore);
		setWandNBT(player, wandSlot, CONFIGURED.toString(), configured);

		// Update the item at the slot that opened this config session (LS-30 — not blindly "whatever is
		// currently in main hand", which may have changed since the config menu was opened).
		player.getInventory().setItem(wandSlot, builder.build());
	}

	private void openLootTablePreview(Player player, String tableId, String fillMaterial, String fillName,
	                                  int wandSlot, int page) {
		ChestMenu menu = buildLootTablePreview(player, tableId, fillMaterial, fillName, wandSlot, page);
		if (menu != null) menu.open(player);
	}

	private ChestMenu buildLootTablePreview(Player player, String tableId, String fillMaterial, String fillName,
	                                        int wandSlot, int page) {
		LootTable table = lootChestManager.getLootTable(tableId).orElse(null);
		if (table == null) {
			player.sendMessage(ChatUtil.color("&cLoot table '&e" + tableId + "&c' no longer exists."));
			openLootTableSelection(player, fillMaterial, fillName, wandSlot);
			return null;
		}

		ItemParser parser = lootChestManager.getItemParser();

		List<PagedRegion.Entry> entries = table.getItemReferences().stream()
				.map(entry -> new PagedRegion.Entry(buildPreviewDisplay(entry, parser).build()))
				.toList();

		PageConfig pageConfig  = PageConfig.forSize(PREVIEW_ROWS, PREVIEW_FIRST_ROW, PREVIEW_LAST_ROW,
		                                            PREVIEW_FIRST_COL, PREVIEW_LAST_COL, entries.size());
		int        currentPage = Math.max(0, Math.min(page, pageConfig.pageCount() - 1));

		String title = "&6&lPreview: &e" + table.getDisplayName();

		ChestMenuBuilder builder = ChestMenu.builder(lootChestManager.getInventoryService())
		                                    .title(title)
		                                    .rows(PREVIEW_ROWS);

		PagedRegion.render(builder, pageConfig, entries, currentPage);

		renderPreviewNavigation(builder, fillMaterial, fillName, wandSlot, tableId, table, currentPage,
		                        pageConfig.pageCount());

		builder.border(BorderComponent.of(materialOf(fillMaterial)).name(fillName));

		return builder.build();
	}

	private void renderPreviewNavigation(ChestMenuBuilder builder, String fillMaterial, String fillName,
	                                     int wandSlot, String tableId, LootTable table, int currentPage,
	                                     int totalPages) {
		var back = new ItemBuilder(Material.ARROW).setDisplayName("&eBack to loot tables");
		builder.slot(PREVIEW_SLOT_BACK, ItemComponent.of(back).onAnyClick(ctx ->
				openLootTableSelection(ctx.player(), fillMaterial, fillName, wandSlot)));

		if (currentPage > 0) {
			var prev = new ItemBuilder(Material.ARROW).setDisplayName("&e◄ Previous page")
			                                          .setLore("&7Go to page " + currentPage + ".");
			builder.slot(PREVIEW_SLOT_PREV, ItemComponent.of(prev).onAnyClick(ctx -> {
				ChestMenu fresh = buildLootTablePreview(ctx.player(), tableId, fillMaterial, fillName, wandSlot,
				                                        currentPage - 1);
				swapPreviewPage(ctx, fresh);
			}));
		}

		var info = new ItemBuilder(Material.PAPER)
				.setDisplayName("&bPage &f" + (currentPage + 1) + "&7/&f" + totalPages)
				.setLore("&7" + table.getItemReferences().size() + " item(s) total.");
		builder.slot(PREVIEW_SLOT_PAGE_INFO, ItemComponent.of(info));

		if (currentPage < totalPages - 1) {
			var next = new ItemBuilder(Material.ARROW).setDisplayName("&eNext page ►")
			                                          .setLore("&7Go to page " + (currentPage + 2) + ".");
			builder.slot(PREVIEW_SLOT_NEXT, ItemComponent.of(next).onAnyClick(ctx -> {
				ChestMenu fresh = buildLootTablePreview(ctx.player(), tableId, fillMaterial, fillName, wandSlot,
				                                        currentPage + 1);
				swapPreviewPage(ctx, fresh);
			}));
		}
	}

	/** Swaps the freshly-built page into the already-open menu in place (no close/reopen flicker) — same
	 *  {@code ChestMenu#adoptComponentsFrom} pattern {@code SimplePagedMenu}/{@code InventoryBuilder} use for
	 *  their own next/previous buttons. */
	private static void swapPreviewPage(ClickContext ctx, ChestMenu fresh) {
		if (fresh == null) return;
		if (ctx.menu() instanceof ChestMenu current) current.adoptComponentsFrom(fresh);
		PREVIEW_PAGE_SOUND.playSound(ctx.player());
	}

	private ItemBuilder buildPreviewDisplay(LootItemReference entry, ItemParser parser) {
		ItemStack resolved = parser == null ? null : parser.parse(entry.getItemString());

		ItemBuilder builder;
		if (resolved != null) {
			ItemStack clone = resolved.clone();
			// Mirror the roll clamp used by LootTable#createItemFromReference so the preview
			// shows the realistic maximum stack rather than a confusing value > maxStackSize.
			int rolled = Math.max(1, Math.min(entry.getMaxAmount(), clone.getMaxStackSize()));
			clone.setAmount(rolled);
			builder = new ItemBuilder(clone);
		} else {
			builder = new ItemBuilder(Material.BARRIER);
		}

		LootItemReference.Rarity rarity = entry.getRarity();
		builder.setDisplayName(rarity.getColorPrefix() + "&l" + entry.getId());

		List<String> lore = new ArrayList<>();
		lore.add("&7Item: &f" + entry.getItemString());
		lore.add("&7Drop Chance: " + rarity.getColorPrefix() + rarity.name());
		lore.add("&7Amount: &f" + entry.getMinAmount() + " &7- &f" + entry.getMaxAmount());
		lore.add("&7Weight: &f" + entry.getWeight());

		if (resolved == null) {
			lore.add("");
			lore.add("&cItem string did not resolve.");
		}

		builder.setLore(lore);

		return builder;
	}

	private void setWandNBT(Player player, int wandSlot, String key, Object value) {
		ItemStack heldItem = player.getInventory().getItem(wandSlot);
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

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}

}
