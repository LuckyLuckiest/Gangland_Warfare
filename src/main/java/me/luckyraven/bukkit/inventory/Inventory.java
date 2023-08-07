package me.luckyraven.bukkit.inventory;

import com.google.common.base.Preconditions;
import lombok.Getter;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Inventory implements Listener {

	public static final   int                           MAX_SLOTS   = 54;
	private static final  Map<NamespacedKey, Inventory> INVENTORIES = new HashMap<>();
	private final @Getter int                           size;

	private final Map<Integer, TriConsumer<Player, Inventory, ItemBuilder>> clickableSlots;

	private final List<Integer>             draggableSlots;
	private final Map<Integer, ItemBuilder> clickableItems;
	private final JavaPlugin                plugin;

	private @Getter org.bukkit.inventory.Inventory inventory;
	private @Getter NamespacedKey                  title;
	private @Getter String                         displayTitle;

	public Inventory(JavaPlugin plugin, String title, int size, NamespacedKey namespacedKey) {
		this.plugin = plugin;
		this.displayTitle = title;
		this.title = namespacedKey;

		int realSize = factorOfNine(size);
		this.size = Math.min(realSize, MAX_SLOTS);

		this.inventory = Bukkit.createInventory(null, this.size, ChatUtil.color(title));
		this.draggableSlots = new ArrayList<>();
		this.clickableSlots = new HashMap<>();
		this.clickableItems = new HashMap<>();

		INVENTORIES.put(this.title, this);
	}

	public Inventory(JavaPlugin plugin, String title, int size) {
		this(plugin, title, size, new NamespacedKey(plugin, titleRefactor(title)));
	}

	protected static String titleRefactor(@NotNull String title) {
		Preconditions.checkNotNull(title, "Title can't be null");

		String pattern = "[^a-z0-9/._-]";
		return ChatUtil.replaceColorCodes(title, "").replaceAll(" ", "_").toLowerCase().replaceAll(pattern, "");
	}

	private int factorOfNine(int value) {
		return (int) Math.ceil((double) value / 9) * 9;
	}

	public void rename(String name) {
		ItemStack[] contents = inventory.getContents();

		inventory = Bukkit.createInventory(null, this.size, ChatUtil.color(name));
		inventory.setContents(contents);

		// remove the old inventory
		INVENTORIES.remove(title);

		this.displayTitle = name;
		this.title = new NamespacedKey(plugin, titleRefactor(name));
		INVENTORIES.put(this.title, this);
	}

	public void setItem(int slot, Material material, @Nullable String displayName, @Nullable List<String> lore,
	                    boolean enchanted, boolean draggable) {
		setItem(slot, material, displayName, lore, enchanted, draggable, null);
	}

	public void setItem(int slot, Material material, @Nullable String displayName, @Nullable List<String> lore,
	                    boolean enchanted, boolean draggable, TriConsumer<Player, Inventory, ItemBuilder> clickable) {
		ItemBuilder item = new ItemBuilder(material).setDisplayName(displayName).setLore(lore);

		if (enchanted) item.addEnchantment(Enchantment.DURABILITY, 1);

		setItem(slot, item, draggable, clickable);
	}

	public void setItem(int slot, ItemBuilder itemBuilder, boolean draggable,
	                    TriConsumer<Player, Inventory, ItemBuilder> clickable) {
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
	                    TriConsumer<Player, Inventory, ItemBuilder> clickable) {
		setItem(slot, new ItemBuilder(itemStack), draggable, clickable);
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

		Inventory inv = INVENTORIES.values().stream().filter(
				inventory -> clickedInventory.equals(inventory.getInventory())).findFirst().orElse(null);

		if (inv == null) return;

		int    rawSlot = event.getRawSlot();
		Player player  = (Player) event.getWhoClicked();

		inv.clickableSlots.getOrDefault(rawSlot, (pl, i, item) -> {}).accept(player, inv,
		                                                                     inv.clickableItems.getOrDefault(rawSlot,
		                                                                                                     null));
		event.setCancelled(!inv.draggableSlots.contains(rawSlot));
	}

}
