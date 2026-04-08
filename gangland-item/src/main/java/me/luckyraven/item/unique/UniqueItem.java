package me.luckyraven.item.unique;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.item.fuel.Fuel;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@RequiredArgsConstructor
@Getter
@Builder
public class UniqueItem implements Comparable<ItemStack> {

	private final String   permission;
	private final String   uniqueItem;
	private final Material material;
	private final int      customModelData;
	private final String   name;
	private final boolean  addOnJoin;
	private final boolean  addOnRespawn;
	private final boolean  dropOnDeath;
	private final boolean  allowDuplicates;
	private final boolean  addToInventory;

	private List<String> lore;
	private int          inventorySlot;
	private boolean      overridesSlot;
	private boolean      movable;
	private boolean      droppable;

	private String lootKey;

	@Nullable
	private Fuel fuel;

	/**
	 * Placeholder resolver injected by {@code UniqueItemAddon} via the builder so {@link #buildItem(Player)} can
	 * resolve {@code %gangland_*%} tokens in the display name and lore.
	 */
	@Nullable
	private Placeholder placeholder;

	@Override
	public int compareTo(@NotNull ItemStack itemStack) {
		ItemMeta meta = itemStack.getItemMeta();

		if (meta == null) return 0;
		if (!UniqueItemUtil.isUniqueItem(itemStack)) return 0;

		int result;

		result = this.name.compareToIgnoreCase(meta.getDisplayName());
		if (result != 0) return result;

		result = this.material.compareTo(itemStack.getType());

		return result;
	}

	public boolean addItemToInventory(Player player) {
		if (!addToInventory) return false;
		return !addItem(player, inventorySlot);
	}

	public ItemStack buildItem() {
		return buildItem(null);
	}

	public ItemStack buildItem(@Nullable Player player) {
		ItemBuilder itemBuilder = new ItemBuilder(material);

		itemBuilder.setDisplayName(resolvePlaceholder(player, name));

		List<String> resolvedLore = resolvePlaceholder(player, lore);
		if (resolvedLore != null) itemBuilder.setLore(resolvedLore);

		if (customModelData > 0) {
			itemBuilder.setCustomModelData(customModelData);
		}

		itemBuilder.addTag(UniqueItemKeys.UNIQUE_ITEM_KEY, uniqueItem);

		if (lootKey != null && !lootKey.isEmpty()) {
			itemBuilder.addTag("loot_key", lootKey);
		}

		if (fuel != null) {
			fuel.stampNBT(itemBuilder);
		}

		return itemBuilder.build();
	}

	private String resolvePlaceholder(@Nullable Player player, @Nullable String text) {
		if (text == null || text.isEmpty() || placeholder == null) return text;
		return placeholder.convert(player, text);
	}

	private List<String> resolvePlaceholder(@Nullable Player player, @Nullable List<String> loreLines) {
		if (loreLines == null || loreLines.isEmpty() || placeholder == null) return loreLines;
		List<String> resolved = new ArrayList<>(loreLines.size());
		for (String line : loreLines) {
			resolved.add(line == null ? null : placeholder.convert(player, line));
		}
		return resolved;
	}

	private boolean addItem(Player player, int inventorySlot) {
		PlayerInventory inventory = player.getInventory();

		if (inventorySlot >= inventory.getSize() || inventorySlot > 35) {
			return false;
		}

		if (inventory.getItem(inventorySlot) != null) {
			if (overridesSlot) {
				createItem(player, inventorySlot);
				return true;
			}

			return addItem(player, inventorySlot + 1);
		} else createItem(player, inventorySlot);

		return true;
	}

	private void createItem(Player player, int inventorySlot) {
		PlayerInventory inventory = player.getInventory();

		inventory.setItem(inventorySlot, buildItem(player));
	}

}
