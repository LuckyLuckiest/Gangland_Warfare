package me.luckyraven.inventory;

import com.cryptomorin.xseries.XEnchantment;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.inventory.service.InventoryRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static me.luckyraven.inventory.util.InventoryUtil.titleRefactor;

public class InventoryHandler implements Listener, Comparable<InventoryHandler> {

	public static final int MAX_SLOTS = 54;

	private static final Map<NamespacedKey, InventoryHandler> SPECIAL_INVENTORIES = new HashMap<>();

	/**
	 * Single static seam set once at startup by the host plugin's KERNEL bean. Mirrors the {@code Messages.init(...)}
	 * pattern: every {@code InventoryHandler} instance shares one global registry, but the registry is now a proper
	 * bean instead of a {@code getInstance()} singleton — so listeners, factories, and the user class can constructor-
	 * inject it directly. Threading the registry through every {@code InventoryHandler} constructor + the
	 * {@code InventoryBuilder.createInventory(...)} cascade would touch 20+ files; this seam keeps the surface change
	 * small while still removing the singleton.
	 */
	@Setter
	private static @Nullable InventoryRegistry registry;

	private final @Getter int  size;
	private final @Getter UUID owner;

	private final Map<Integer, TriConsumer<Player, InventoryHandler, ItemBuilder>> clickableSlots;
	private final Map<Integer, TriConsumer<Player, InventoryHandler, ItemBuilder>> rightClickSlots;

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

		this.inventory       = Bukkit.createInventory(null, this.size, ChatUtil.color(title));
		this.draggableSlots  = new ArrayList<>();
		this.clickableSlots  = new HashMap<>();
		this.rightClickSlots = new HashMap<>();
		this.clickableItems  = new HashMap<>();
	}

	public InventoryHandler(JavaPlugin plugin, String title, int size, String special, boolean add) {
		this(title, size, new NamespacedKey(plugin, titleRefactor(special)), null);

		if (add) {
			SPECIAL_INVENTORIES.put(this.title, this);
		}
	}

	public InventoryHandler(String title, int size, Player player, NamespacedKey namespacedKey) {
		this(title, size, namespacedKey, player != null ? player.getUniqueId() : null);

		if (player != null && registry != null) {
			registry.registerInventory(player.getUniqueId(), this);
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

		if (ownerUUID != null && registry != null) {
			registry.registerInventory(ownerUUID, this);
		}
	}

	public void unregister() {
		if (owner != null && registry != null) {
			registry.unregisterInventory(owner, this);
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

	public void setItem(int slot, ItemBuilder itemBuilder, boolean draggable,
	                    TriConsumer<Player, InventoryHandler, ItemBuilder> leftClick,
	                    TriConsumer<Player, InventoryHandler, ItemBuilder> rightClick) {
		setItem(slot, itemBuilder.build(), draggable);

		if (leftClick != null) {
			clickableSlots.put(slot, leftClick);
			clickableItems.put(slot, itemBuilder);
		}
		if (rightClick != null) {
			rightClickSlots.put(slot, rightClick);
			if (leftClick == null) {
				clickableItems.put(slot, itemBuilder);
			}
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
		if (owner != null && registry != null) {
			registry.registerInventory(owner, this);
		}
		player.openInventory(inventory);
	}

	public void close(Player player) {
		player.closeInventory();
	}

	public Map<Integer, TriConsumer<Player, InventoryHandler, ItemBuilder>> getClickableSlots() {
		return new HashMap<>(clickableSlots);
	}

	public Map<Integer, TriConsumer<Player, InventoryHandler, ItemBuilder>> getRightClickSlots() {
		return new HashMap<>(rightClickSlots);
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
}
