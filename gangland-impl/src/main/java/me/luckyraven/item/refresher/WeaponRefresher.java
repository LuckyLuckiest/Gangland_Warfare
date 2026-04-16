package me.luckyraven.item.refresher;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.ItemRefresher;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.WeaponTag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Rebuilds a gangland weapon ItemStack into a factory-fresh copy with full ammo, clean NBT, and the default selective-
 * fire mode. Mirrors {@link me.luckyraven.item.converter.WeaponConverter}'s build path so the refreshed item matches
 * what the converter would produce.
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
