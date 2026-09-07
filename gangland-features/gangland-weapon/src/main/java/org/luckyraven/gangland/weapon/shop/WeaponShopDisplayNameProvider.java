package org.luckyraven.gangland.weapon.shop;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.file.configuration.shop.ShopDisplayNameProvider;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.WeaponTag;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.ChatUtil;

/**
 * The weapon branch removed from the core's {@code GanglandShopDisplayResolver} when the shop-display seam was
 * introduced. Resolves weapon items through {@link WeaponService} so the clean configured display name is
 * returned instead of the item's live display name (which carries the dynamic magazine counter appended by
 * {@code Weapon.buildDisplayName()}).
 */
public final class WeaponShopDisplayNameProvider implements ShopDisplayNameProvider {

	private final WeaponService weaponService;

	public WeaponShopDisplayNameProvider(WeaponService weaponService) {
		this.weaponService = weaponService;
	}

	@Override
	@Nullable
	public String cleanDisplayName(ItemStack item) {
		if (item == null) return null;

		String weaponName = new ItemBuilder(item).getStringTagData(Weapon.getTagProperName(WeaponTag.WEAPON));
		if (weaponName == null || weaponName.isEmpty()) return null;

		Weapon weapon = weaponService.getWeaponTemplate(weaponName);
		if (weapon == null || weapon.getDisplayName() == null || weapon.getDisplayName().isBlank()) return null;

		// Weapon#getDisplayName returns the raw YAML string with '&' codes — translate before returning
		// so callers can drop the result straight into item display names / chat messages.
		return ChatUtil.color(weapon.getDisplayName());
	}
}
