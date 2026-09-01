package org.luckyraven.gangland.item.refresher;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.item.ItemRefresher;
import org.luckyraven.gangland.item.converter.WeaponConverter;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.WeaponTag;

/**
 * Rebuilds a gangland weapon ItemStack into a factory-fresh copy with full ammo, clean NBT, and the default selective-
 * fire mode. Mirrors {@link WeaponConverter}'s build path so the refreshed item matches what the converter would
 * produce.
 */
@RequiredArgsConstructor
public class WeaponRefresher implements ItemRefresher {

	private final WeaponService weaponService;

	@Override
	public boolean canRefresh(ItemStack source) {
		if (source == null) return false;
		String name = new ItemBuilder(source).getStringTagData(Weapon.getTagProperName(WeaponTag.WEAPON));
		return name != null && !name.isEmpty();
	}

	@Override
	@Nullable
	public ItemStack refresh(ItemStack source, @Nullable Player context) {
		String name = new ItemBuilder(source).getStringTagData(Weapon.getTagProperName(WeaponTag.WEAPON));
		if (name == null || name.isEmpty()) return null;

		Weapon weapon = weaponService.getWeapon(name);
		if (weapon == null) return null;

		Weapon    clone = weapon.clone();
		ItemStack built = context != null ? clone.buildItem(context) : clone.buildItem();
		if (built == null) return null;

		built.setAmount(source.getAmount());
		return built;
	}

}
