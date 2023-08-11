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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InventoryHandler implements Listener {

	public static final   int                                  MAX_SLOTS   = 54;
	private static final  Map<NamespacedKey, InventoryHandler> INVENTORIES = new HashMap<>();
	private static        int                                  ID          = 0;
	private final @Getter int                                  size;

	private final Map<Integer, TriConsumer<Player, InventoryHandler, ItemBuilder>> clickableSlots;

	private final List<Integer>             draggableSlots;
	private final Map<Integer, ItemBuilder> clickableItems;
	private final JavaPlugin                plugin;

	private @Getter Inventory     inventory;
	private @Getter NamespacedKey title;
	private @Getter String        displayTitle;

	public InventoryHandler(JavaPlugin plugin, String title, int size, @Nullable Player player,
	                        NamespacedKey namespacedKey) {
		this.plugin = plugin;
		this.displayTitle = title;

		String data = player == null ? "null_" + ID++ : player.getUniqueId().toString();
		this.title = new NamespacedKey(plugin, namespacedKey.getKey() + "_" + data);

		int realSize = factorOfNine(size);
		this.size = Math.min(realSize, MAX_SLOTS);

		this.inventory = Bukkit.createInventory(null, this.size, ChatUtil.color(title));
		this.draggableSlots = new ArrayList<>();
		this.clickableSlots = new HashMap<>();
		this.clickableItems = new HashMap<>();

		INVENTORIES.put(this.title, this);
	}

	public InventoryHandler(JavaPlugin plugin, String title, int size, Player player) {
		this(plugin, title, size, player, new NamespacedKey(plugin, titleRefactor(title)));
	}

	public static String titleRefactor(@NotNull String title) {
		Preconditions.checkNotNull(title, "Title can't be null");

		String pattern = "[^a-z0-9/._-]";
		return ChatUtil.replaceColorCodes(title, "").replaceAll(" ", "_").toLowerCase().replaceAll(pattern, "");
	}

	public static Map<NamespacedKey, InventoryHandler> getInventories() {
		return new HashMap<>(INVENTORIES);
	}

	public static void removeAllInventories(Player player) {
		for (NamespacedKey key : getPlayerInventories(player).keySet())
			removeInventory(key);
	}

	public static void removeInventory(NamespacedKey key) {
		INVENTORIES.remove(key);
	}

	public static Map<NamespacedKey, InventoryHandler> getPlayerInventories(Player player) {
		String uuid = player.getUniqueId().toString();
		return INVENTORIES.entrySet().stream().filter(entry -> {
			NamespacedKey key     = entry.getKey();
			int           index   = key.getKey().lastIndexOf("_") + 1;
			String        keyUuid = key.getKey().substring(index);
			return keyUuid.equalsIgnoreCase(uuid);
		}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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

	public void clear() {
		inventory.clear();
	}

	public void open(Player player) {
		player.openInventory(inventory);
	}

	public void close(Player player) {
		player.closeInventory();

		// need to remove all inventory instances of that player
		removeAllInventories(player);
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		org.bukkit.inventory.Inventory clickedInventory = event.getClickedInventory();
		if (clickedInventory == null) return;

		InventoryHandler inv = INVENTORIES.values().stream().filter(
				inventory -> clickedInventory.equals(inventory.getInventory())).findFirst().orElse(null);

		if (inv == null) return;

		int    rawSlot = event.getRawSlot();
		Player player  = (Player) event.getWhoClicked();

		TriConsumer<Player, InventoryHandler, ItemBuilder> slots = inv.clickableSlots.getOrDefault(rawSlot,
		                                                                                           (pl, i, item) -> {});
		slots.accept(player, inv, inv.clickableItems.getOrDefault(rawSlot, null));
		event.setCancelled(!inv.draggableSlots.contains(rawSlot));
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public synchronized void onPlayerQuit(PlayerQuitEvent event) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			Player player = event.getPlayer();

			// remove all the inventories of that player only
			removeAllInventories(player);
		});
	}

}
