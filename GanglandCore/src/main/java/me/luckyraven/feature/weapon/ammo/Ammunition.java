package me.luckyraven.feature.weapon.ammo;

import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.bukkit.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

@AllArgsConstructor
@RequiredArgsConstructor
@Getter
public class Ammunition {

	private final String       name;
	private final String       displayName;
	private final Material     material;
	private       List<String> lore;

	public static boolean isAmmunition(ItemStack item) {
		Preconditions.checkNotNull(item, "Item can't be null!");
		Preconditions.checkArgument(item.getType().equals(Material.AIR) || item.getAmount() == 0,
									"Item can't be air or amount of 0.");

		return new ItemBuilder(item).hasNBTTag("ammo");
	}

	public ItemStack buildItem() {
		return buildItem(1);
	}

	public ItemStack buildItem(int amount) {
		ItemBuilder builder = new ItemBuilder(material);

		builder.setDisplayName(displayName).setLore(lore);
		builder.setAmount(amount);
		builder.addTag("ammo", name);

		return builder.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Ammunition that = (Ammunition) o;

		return Objects.equals(name, that.name) && material == that.material;
	}

	@Override
	public String toString() {
		return String.format("Ammunition{ammo='%s',name='%s',material='%s'", name, displayName, material.toString());
	}

}
