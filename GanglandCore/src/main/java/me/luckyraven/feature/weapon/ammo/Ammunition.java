package me.luckyraven.feature.weapon.ammo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.Gangland;
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
		if (item == null || item.getType().equals(Material.AIR) || item.getAmount() == 0) return false;

		return new ItemBuilder(item).hasNBTTag("ammo");
	}

	public static Ammunition getHeldAmmunition(Gangland gangland, ItemStack itemHeld) {
		if (itemHeld == null || itemHeld.getType().equals(Material.AIR) || itemHeld.getAmount() == 0) return null;
		if (!isAmmunition(itemHeld)) return null;

		ItemBuilder itemBuilder    = new ItemBuilder(itemHeld);
		String      ammunitionName = itemBuilder.getStringTagData("ammo");

		return gangland.getInitializer().getAmmunitionAddon().getAmmunition(ammunitionName);
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
		return String.format("Ammunition{ammo='%s',name='%s',material='%s'}", name, displayName, material.toString());
	}

}
