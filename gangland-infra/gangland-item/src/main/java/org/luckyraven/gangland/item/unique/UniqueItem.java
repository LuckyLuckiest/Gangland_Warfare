package org.luckyraven.gangland.item.unique;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.Placeholder;
import org.luckyraven.gangland.item.fuel.Fuel;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@RequiredArgsConstructor
@Getter
@Builder
public class UniqueItem implements Comparable<ItemStack> {

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

	/**
	 * Returns the permission node for this unique item, derived from its registry key.
	 *
	 * @return {@code "gangland.uniqueitem.<uniqueItem>"}
	 */
	public String getPermission() {
		return "gangland.uniqueitem." + uniqueItem;
	}

	/**
	 * Identity check against a live stack: does {@code stack} carry this item's registry key in its
	 * {@link UniqueItemKeys#UNIQUE_ITEM_KEY} NBT tag, on the material this item is configured for?
	 *
	 * <p>The tag is stamped by {@link #buildItem(Player)}, so it survives colour codes, placeholder
	 * resolution and lore edits — unlike the display name, which is stored raw in the config
	 * ({@code &6Phone}) but rendered translated ({@code §6Phone}) on the stack, and so could never match.
	 *
	 * @param stack the stack to test; {@code null}, AIR and untagged stacks are never a match
	 *
	 * @return {@code true} only when the stack is this exact unique item
	 */
	public boolean matches(@Nullable ItemStack stack) {
		if (stack == null) return false;

		String key = UniqueItemUtil.getUniqueItemKey(stack);

		if (key == null) return false;

		return this.uniqueItem.equals(key) && this.material == stack.getType();
	}

	/**
	 * Orders by unique-item registry key, then by material, so {@code compareTo(stack) == 0} is exactly
	 * {@link #matches(ItemStack)}.
	 *
	 * <p>This is a <em>partial</em> order over {@link ItemStack}: every stack that is not a unique item
	 * sorts after every unique item ({@code 1}) rather than comparing equal. Returning {@code 0} for those
	 * would make every caller that treats {@code 0} as "this is my item" — {@code UniqueItemUtil
	 * .hasUniqueItem} and {@code LoadUniqueItem.removeItem} — act on plain inventory contents.
	 */
	@Override
	public int compareTo(@NotNull ItemStack itemStack) {
		String key = UniqueItemUtil.getUniqueItemKey(itemStack);

		if (key == null) return 1;

		int result = this.uniqueItem.compareTo(key);
		if (result != 0) return result;

		return this.material.compareTo(itemStack.getType());
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
