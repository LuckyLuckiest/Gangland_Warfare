package me.luckyraven.inventory.multi;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.ButtonTags;
import me.luckyraven.util.ItemBuilder;
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
									 int displayCurrentPageIndex, int totalPages, ButtonTags buttonTags,
									 Consumer<Player> onNext, Consumer<Player> onPrev, Consumer<Player> onHome) {
		// next page -> nextInv
		addNextPageItem(nextInv, buttonTags.nextPage(), displayCurrentPageIndex, totalPages, onNext);
		// home page
		addHomePageItem(currentInv, buttonTags.homePage(), homeTitle, onHome);
		// prev page -> currentInv (acts on previous page)
		addPreviousPageItem(currentInv, buttonTags.previousPage(), onPrev);
	}

	private static void addNextPageItem(InventoryHandler linkedInventory, String nextPageTag,
										int displayCurrentPageIndex, int totalPages, Consumer<Player> onClick) {
		ItemBuilder item = createItemHead("&a->", String.format("&7(%d/%d)", displayCurrentPageIndex + 1, totalPages));

		item.customHead(nextPageTag);
		linkedInventory.setItem(linkedInventory.getSize() - 1, item.build(), false,
								(player, inventory, itemBuilder) -> {
									onClick.accept(player);
									buttonClickSound(player);
								});
	}

	private static void addPreviousPageItem(InventoryHandler linkedInventory, String previousPageTag,
											Consumer<Player> onClick) {
		ItemBuilder item = createItemHead("&c<-");

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