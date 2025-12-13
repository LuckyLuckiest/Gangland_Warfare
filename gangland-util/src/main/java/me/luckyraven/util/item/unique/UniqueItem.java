package me.luckyraven.util.item.unique;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;

import java.util.List;

@AllArgsConstructor
@RequiredArgsConstructor
@Getter
@Builder
public class UniqueItem implements Comparable<ItemStack> {

	private final String   permission;
	private final String   uniqueItem;
	private final Material material;
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

	@Override
	public int compareTo(@NonNull ItemStack itemStack) {
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
		ItemBuilder itemBuilder = new ItemBuilder(material);

		itemBuilder.setDisplayName(name);

		if (lore != null) itemBuilder.setLore(lore);

		itemBuilder.addTag("uniqueItem", uniqueItem);

		return itemBuilder.build();
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

		inventory.setItem(inventorySlot, buildItem());
	}

}
