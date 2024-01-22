package me.luckyraven.feature.weapon.ammo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.bukkit.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@AllArgsConstructor
@RequiredArgsConstructor
@Getter
public class Ammunition {

	private final String       name;
	private final String       displayName;
	private final Material     material;
	private       List<String> lore;

	public static boolean isAmmunition(ItemStack item) {
		return new ItemBuilder(item).hasNBTTag("ammo");
	}

	public ItemStack getItem() {
		return getItem(1);
	}

	public ItemStack getItem(int amount) {
		ItemBuilder builder = new ItemBuilder(material);

		builder.setDisplayName(displayName).setLore(lore);
		builder.setAmount(amount);
		builder.addTag("ammo", name);

		return builder.build();
	}

	@Override
	public String toString() {
		return String.format("Ammunition{ammo='%s',name='%s',material=%s", name, displayName, material.toString());
	}

}
