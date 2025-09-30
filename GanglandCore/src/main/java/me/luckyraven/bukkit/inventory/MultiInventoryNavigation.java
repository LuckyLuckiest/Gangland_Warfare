package me.luckyraven.bukkit.inventory;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Utility for adding navigation buttons to paged inventories.
 */
final class MultiInventoryNavigation {

	private MultiInventoryNavigation() { }

	static void addNavigationButtons(InventoryHandler currentInv, InventoryHandler nextInv, String homeTitle,
									 int displayCurrentPageIndex, int totalPages, Consumer<Player> onNext,
									 Consumer<Player> onPrev, Consumer<Player> onHome) {
		// next page -> nextInv
		addNextPageItem(nextInv, displayCurrentPageIndex, totalPages, onNext);
		// home page
		addHomePageItem(currentInv, homeTitle, onHome);
		// prev page -> currentInv (acts on previous page)
		addPreviousPageItem(currentInv, onPrev);
	}

	private static void addNextPageItem(InventoryHandler linkedInventory, int displayCurrentPageIndex, int totalPages,
										Consumer<Player> onClick) {
		ItemBuilder item  = createItemHead("&a->", String.format("&7(%d/%d)", displayCurrentPageIndex + 1, totalPages));
		String      arrow = SettingAddon.getNextPage();
		item.customHead(arrow);
		linkedInventory.setItem(linkedInventory.getSize() - 1, item.build(), false,
								(player, inventory, itemBuilder) -> {
									onClick.accept(player);
									buttonClickSound(player);
								});
	}

	private static void addPreviousPageItem(InventoryHandler linkedInventory, Consumer<Player> onClick) {
		ItemBuilder item  = createItemHead("&c<-");
		String      arrow = SettingAddon.getPreviousPage();
		item.customHead(arrow);
		linkedInventory.setItem(linkedInventory.getSize() - 9, item.build(), false,
								(player, inventory, itemBuilder) -> {
									onClick.accept(player);
									buttonClickSound(player);
								});
	}

	private static void addHomePageItem(InventoryHandler linkedInventory, String homeTitle, Consumer<Player> onClick) {
		ItemBuilder item = createItemHead("&cBack to " + homeTitle);
		String      home = SettingAddon.getHomePage();
		item.customHead(home);
		linkedInventory.setItem(linkedInventory.getSize() - 5, item.build(), false,
								(player, inventory, itemBuilder) -> {
									onClick.accept(player);
									buttonClickSound(player);
								});
	}

	private static ItemBuilder createItemHead(String name, @Nullable String... lore) {
		ItemBuilder item = new ItemBuilder(XMaterial.PLAYER_HEAD.get()).setDisplayName(name);
		if (lore != null) item.setLore(lore);
		return item;
	}

	private static void buttonClickSound(Player player) {
		Sound sound = XSound.BLOCK_WOODEN_BUTTON_CLICK_ON.get();
		if (sound != null) player.playSound(player.getLocation(), sound, 1F, 1F);
	}
}