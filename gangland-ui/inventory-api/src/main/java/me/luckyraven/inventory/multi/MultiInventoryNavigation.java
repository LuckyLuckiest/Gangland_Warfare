package me.luckyraven.inventory.multi;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.ButtonTags;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Utility for adding navigation buttons to paged inventories.
 */
final class MultiInventoryNavigation {

	private MultiInventoryNavigation() { }

	static void addNavigationButtons(JavaPlugin plugin, InventoryHandler currentInv, InventoryHandler nextInv,
									 String homeTitle, int displayCurrentPageIndex, int totalPages,
									 ButtonTags buttonTags, Consumer<Player> onNext, Consumer<Player> onPrev,
									 Consumer<Player> onHome) {
		String titleWithPages = String.format("%s &8[&b%d&8/&3%d&8]&r", homeTitle, displayCurrentPageIndex + 1,
											  totalPages);

		currentInv.rename(plugin, titleWithPages);

		// next page -> only show if there is a next page
		if (nextInv != null && displayCurrentPageIndex < totalPages - 1) {
			addNextPageItem(currentInv, buttonTags.nextPage(), displayCurrentPageIndex, totalPages, onNext);
		}

		// show on all pages except the first
		if (displayCurrentPageIndex > 0) {
			// home page
			addHomePageItem(currentInv, buttonTags.homePage(), homeTitle, onHome);
			// prev page
			addPreviousPageItem(currentInv, buttonTags.previousPage(), displayCurrentPageIndex, totalPages, onPrev);
		}
	}

	private static void addNextPageItem(InventoryHandler linkedInventory, String nextPageTag,
										int displayCurrentPageIndex, int totalPages, Consumer<Player> onClick) {
		String      page = String.format("&7(%d/%d)", displayCurrentPageIndex + 2, totalPages);
		ItemBuilder item = createItemHead("&a->", page);

		item.customHead(nextPageTag);
		linkedInventory.setItem(linkedInventory.getSize() - 1, item.build(), false,
								(player, inventory, itemBuilder) -> {
									onClick.accept(player);
									buttonClickSound(player);
								});
	}

	private static void addPreviousPageItem(InventoryHandler linkedInventory, String previousPageTag,
											int displayCurrentPageIndex, int totalPages, Consumer<Player> onClick) {
		String      page = String.format("&7(%d/%d)", displayCurrentPageIndex, totalPages);
		ItemBuilder item = createItemHead("&c<-", page);

		item.customHead(previousPageTag);
		linkedInventory.setItem(linkedInventory.getSize() - 9, item.build(), false,
								(player, inventory, itemBuilder) -> {
									onClick.accept(player);
									buttonClickSound(player);
								});
	}

	private static void addHomePageItem(InventoryHandler linkedInventory, String homePageTag, String homeTitle,
										Consumer<Player> onClick) {
		ItemBuilder item = createItemHead("&cBack to " + homeTitle);

		item.customHead(homePageTag);
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