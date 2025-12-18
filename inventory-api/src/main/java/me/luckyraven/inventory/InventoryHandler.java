package me.luckyraven.inventory;

import com.cryptomorin.xseries.XEnchantment;
import com.google.common.base.Preconditions;
import lombok.Getter;
import me.luckyraven.inventory.service.InventoryRegistry;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.Placeholder;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.color.ColorUtil;
import me.luckyraven.util.color.MaterialType;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static me.luckyraven.inventory.util.InventoryUtil.titleRefactor;

public class InventoryHandler implements Listener, Comparable<InventoryHandler> {

	// region Constants and Static State

	public static final int MAX_SLOTS = 54;

	private static final Map<NamespacedKey, InventoryHandler> SPECIAL_INVENTORIES = new HashMap<>();

	private final @Getter int  size;
	private final @Getter UUID owner;

	private final Map<Integer, TriConsumer<Player, InventoryHandler, ItemBuilder>> clickableSlots;

	private final List<Integer>             draggableSlots;
	private final Map<Integer, ItemBuilder> clickableItems;

	private @Getter Inventory     inventory;
	private @Getter NamespacedKey title;
	private @Getter String        displayTitle;

	public InventoryHandler(String title, int size, NamespacedKey namespacedKey, @Nullable UUID owner) {
		this.displayTitle = title;
		this.owner        = owner;
		this.title        = namespacedKey;

		int realSize = factorOfNine(size);
		this.size = Math.min(realSize, MAX_SLOTS);

		this.inventory      = Bukkit.createInventory(null, this.size, ChatUtil.color(title));
		this.draggableSlots = new ArrayList<>();
		this.clickableSlots = new HashMap<>();
		this.clickableItems = new HashMap<>();
	}

	public InventoryHandler(JavaPlugin plugin, String title, int size, String special, boolean add) {
		this(title, size, new NamespacedKey(plugin, titleRefactor(special)), null);

		if (add) {
			SPECIAL_INVENTORIES.put(this.title, this);
		}
	}

	public InventoryHandler(String title, int size, Player player, NamespacedKey namespacedKey) {
		this(title, size, namespacedKey, player != null ? player.getUniqueId() : null);

		if (player != null) {
			InventoryRegistry.getInstance().registerInventory(player.getUniqueId(), this);
		}
	}

	public InventoryHandler(JavaPlugin plugin, String title, int size, Player player) {
		this(title, size, player, new NamespacedKey(plugin, titleRefactor(title)));
	}

	public InventoryHandler(JavaPlugin plugin, String title, int size) {
		this(plugin, title, size, title, true);
	}

	public static Map<NamespacedKey, InventoryHandler> getSpecialInventories() {
		return Collections.unmodifiableMap(SPECIAL_INVENTORIES);
	}

	public static void removeAllSpecialInventories() {
		SPECIAL_INVENTORIES.clear();
	}

	public static int factorOfNine(int value) {
		return (int) Math.ceil((double) value / 9) * 9;
	}

	public void rename(JavaPlugin plugin, String name) {
		ItemStack[] contents = inventory.getContents();

		UUID ownerUUID = owner;

		unregister();

		inventory = Bukkit.createInventory(null, size, ChatUtil.color(name));
		inventory.setContents(contents);
		displayTitle = name;
		title        = new NamespacedKey(plugin, titleRefactor(name));

		if (ownerUUID != null) {
			InventoryRegistry.getInstance().registerInventory(ownerUUID, this);
		}
	}

	public void unregister() {
		if (owner != null) {
			InventoryRegistry.getInstance().unregisterInventory(owner, this);
		}
	}

	public void copyContent(Placeholder placeholder, InventoryHandler inventoryHandler, Player player) {
		Preconditions.checkArgument(inventoryHandler.getSize() == size, "Inventory sizes not equal.");

		for (int i = 0; i < size; i++) {
			ItemStack item = inventoryHandler.getInventory().getItem(i);
			if (item == null) continue;

			Material resolvedType = resolveMaterialWithColor(placeholder, player, item);
			ItemMeta meta         = item.getItemMeta();

			String       displayName = null;
			List<String> lore        = null;
			boolean      enchanted   = false;

			if (meta != null) {
				displayName = applyPlaceholders(placeholder, meta.getDisplayName(), player);
				lore        = meta.getLore();
				if (lore != null) lore = lore.stream()
											 .map(line -> applyPlaceholders(placeholder, line, player))
											 .toList();
				enchanted = meta.hasEnchants();
			}

			setItem(i, resolvedType, displayName, lore, enchanted, inventoryHandler.draggableSlots.contains(i),
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
			item.addEnchantment(XEnchantment.UNBREAKING.get(), 1).addItemFlags(ItemFlag.HIDE_ENCHANTS);
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

	public Map<Integer, TriConsumer<Player, InventoryHandler, ItemBuilder>> getClickableSlots() {
		return new HashMap<>(clickableSlots);
	}

	public List<Integer> getDraggableSlots() {
		return new ArrayList<>(draggableSlots);
	}

	public Map<Integer, ItemBuilder> getClickableItems() {
		return new HashMap<>(clickableItems);
	}

	@Override
	public int compareTo(@NotNull InventoryHandler handler) {
		if (this.title.equals(handler.title)) return 0;
		return this.title.toString().compareTo(handler.title.toString());
	}

	private String applyPlaceholders(Placeholder placeholder, @Nullable String text, Player player) {
		if (text == null) return null;
		return placeholder.convert(player, text);
	}

	private Material resolveMaterialWithColor(Placeholder placeholder, Player player, ItemStack item) {
		Material    type        = item.getType();
		ItemBuilder itemBuilder = new ItemBuilder(item);
		String      dataTag     = "color";
		if (itemBuilder.hasNBTTag(dataTag)) {
			String       value    = placeholder.convert(player, itemBuilder.getStringTagData(dataTag));
			MaterialType material = MaterialType.WOOL;
			for (MaterialType materialType : MaterialType.values()) {
				if (type.name().contains(materialType.name())) {
					material = materialType;
					break;
				}
			}
			type = ColorUtil.getMaterialByColor(value, material.name());
		}
		return type;
	}
}
