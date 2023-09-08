package me.luckyraven.bukkit.inventory;

import com.google.common.base.Preconditions;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.data.user.User;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.color.ColorUtil;
import me.luckyraven.util.color.MaterialType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryHandler implements Listener {

	public static final  int                                  MAX_SLOTS           = 54;
	private static final Map<NamespacedKey, InventoryHandler> SPECIAL_INVENTORIES = new HashMap<>();

	private final @Getter int size;

	private final Map<Integer, TriConsumer<Player, InventoryHandler, ItemBuilder>> clickableSlots;

	private final List<Integer>             draggableSlots;
	private final Map<Integer, ItemBuilder> clickableItems;
	private final Gangland                  gangland;

	private @Getter Inventory     inventory;
	private @Getter NamespacedKey title;
	private @Getter String        displayTitle;

	public InventoryHandler(Gangland gangland, String title, int size, NamespacedKey namespacedKey) {
		this.gangland = gangland;
		this.displayTitle = title;

		this.title = namespacedKey;

		int realSize = factorOfNine(size);
		this.size = Math.min(realSize, MAX_SLOTS);

		this.inventory = Bukkit.createInventory(null, this.size, ChatUtil.color(title));
		this.draggableSlots = new ArrayList<>();
		this.clickableSlots = new HashMap<>();
		this.clickableItems = new HashMap<>();
	}

	public InventoryHandler(Gangland gangland, String title, int size, String special, boolean add) {
		this(gangland, title, size, new NamespacedKey(gangland, special));

		if (add) SPECIAL_INVENTORIES.put(this.title, this);
	}

	public InventoryHandler(Gangland gangland, String title, int size, User<Player> user, NamespacedKey namespacedKey) {
		this(gangland, title, size, namespacedKey);

		user.addInventory(this);
	}

	public InventoryHandler(Gangland gangland, String title, int size, User<Player> user) {
		this(gangland, title, size, user, new NamespacedKey(gangland, titleRefactor(title)));
	}

	public InventoryHandler(Gangland gangland, String title, int size) {
		this(gangland, title, size, title, true);
	}

	public static String titleRefactor(@NotNull String title) {
		Preconditions.checkNotNull(title, "Title can't be null");

		String pattern = "[^a-z0-9/._-]";
		return ChatUtil.replaceColorCodes(title, "").replaceAll(" ", "_").toLowerCase().replaceAll(pattern, "");
	}

	public static Map<NamespacedKey, InventoryHandler> getSpecialInventories() {
		return new HashMap<>(SPECIAL_INVENTORIES);
	}

	public static void removeAllSpecialInventories() {
		SPECIAL_INVENTORIES.clear();
	}

	public static void rename(User<Player> user, Gangland gangland, InventoryHandler inventoryHandler, String name) {
		Inventory   inventory = inventoryHandler.inventory;
		ItemStack[] contents  = inventory.getContents();

		inventory = Bukkit.createInventory(null, inventoryHandler.size, ChatUtil.color(name));
		inventory.setContents(contents);

		user.removeInventory(inventoryHandler);

		inventoryHandler.displayTitle = name;
		inventoryHandler.title = new NamespacedKey(gangland, titleRefactor(name));
	}

	private int factorOfNine(int value) {
		return (int) Math.ceil((double) value / 9) * 9;
	}

	public void copyContent(InventoryHandler inventoryHandler, Player player) {
		Preconditions.checkArgument(inventoryHandler.getSize() == size, "Inventory sizes not equal.");

		for (int i = 0; i < size; i++) {
			ItemStack item = inventoryHandler.getInventory().getItem(i);
			if (item == null) continue;

			// assume that item has a very special nbt which is the data
			String      dataTag     = "color";
			ItemBuilder itemBuilder = new ItemBuilder(item);
			Material    type        = item.getType();
			if (itemBuilder.hasNBTTag(dataTag)) {
				// special treatment for colored data
				String value = gangland.usePlaceholder(player, itemBuilder.getTagData(dataTag).toString());

				MaterialType material = MaterialType.WOOL;
				for (MaterialType materialType : MaterialType.values()) {
					if (type.name().contains(materialType.name())) {
						material = materialType;
						break;
					}
				}

				type = ColorUtil.getMaterialByColor(value, material.name());
			}

			ItemMeta itemMeta = item.getItemMeta();

			String       displayName = null;
			List<String> lore        = null;
			boolean      enchanted   = false;

			if (itemMeta != null) {
				displayName = gangland.usePlaceholder(player, itemMeta.getDisplayName());
				lore = itemMeta.getLore();
				if (lore != null) lore = lore.stream().map(line -> gangland.usePlaceholder(player, line)).toList();
				enchanted = itemMeta.hasEnchants();
			}

			setItem(i, type, displayName, lore, enchanted, inventoryHandler.draggableSlots.contains(i),
			        inventoryHandler.clickableSlots.get(i));
		}
	}

	public void setItem(int slot, Material material, @Nullable String displayName, @Nullable List<String> lore,
	                    boolean enchanted, boolean draggable) {
		setItem(slot, material, displayName, lore, enchanted, draggable, null);
	}

	public void setItem(int slot, Material material, @Nullable String displayName, @Nullable List<String> lore,
	                    boolean enchanted, boolean draggable,
	                    TriConsumer<Player, InventoryHandler, ItemBuilder> clickable) {
		ItemBuilder item = new ItemBuilder(material).setDisplayName(displayName).setLore(lore);

		if (enchanted) {
			item.addEnchantment(Enchantment.DURABILITY, 1).addItemFlags(ItemFlag.HIDE_ENCHANTS);
		}

		setItem(slot, item, draggable, clickable);
	}

	public void setItem(int slot, ItemBuilder itemBuilder, boolean draggable,
	                    TriConsumer<Player, InventoryHandler, ItemBuilder> clickable) {
		setItem(slot, itemBuilder.build(), draggable);

		if (clickable != null) {
			clickableSlots.put(slot, clickable);
			clickableItems.put(slot, itemBuilder);
		}
	}

	public void removeItem(int slot) {
		inventory.setItem(slot, null);

		draggableSlots.remove((Integer) slot);
		clickableSlots.remove(slot);
		clickableItems.remove(slot);
	}

	public void setItem(int slot, ItemStack itemStack, boolean draggable) {
		inventory.setItem(slot, itemStack);
		if (draggable) draggableSlots.add(slot);
	}

	public void setItem(int slot, ItemStack itemStack, boolean draggable,
	                    TriConsumer<Player, InventoryHandler, ItemBuilder> clickable) {
		setItem(slot, new ItemBuilder(itemStack), draggable, clickable);
	}

	public boolean itemOccupied(int slot) {
		return inventory.getItem(slot) != null;
	}

	public void clear() {
		inventory.clear();
	}

	public void open(Player player) {
		player.openInventory(inventory);
	}

	public void close(Player player) {
		player.closeInventory();
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		org.bukkit.inventory.Inventory clickedInventory = event.getClickedInventory();
		if (clickedInventory == null) return;

		Player       player = (Player) event.getWhoClicked();
		User<Player> user   = gangland.getInitializer().getUserManager().getUser(player);
		InventoryHandler inv = user.getInventoryHandlers().stream().filter(
				inventory -> clickedInventory.equals(inventory.getInventory())).findFirst().orElse(null);

		if (inv == null) return;

		int rawSlot = event.getRawSlot();

		TriConsumer<Player, InventoryHandler, ItemBuilder> slots = inv.clickableSlots.getOrDefault(rawSlot,
		                                                                                           (pl, i, item) -> {});
		slots.accept(player, inv, inv.clickableItems.getOrDefault(rawSlot, null));
		event.setCancelled(!inv.draggableSlots.contains(rawSlot));
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public synchronized void onPlayerQuit(PlayerQuitEvent event) {
		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
			Player       player = event.getPlayer();
			User<Player> user   = gangland.getInitializer().getUserManager().getUser(player);

			// remove all the inventories of that player only
			user.clearInventories();
		});
	}

}
