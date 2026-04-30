package org.luckyraven.gangland.file.configuration.shop;

import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.luckyraven.gangland.core.ItemBuilder;
import org.luckyraven.gangland.core.utilities.ChatUtil;
import org.luckyraven.gangland.shop.message.ShopDisplayResolver;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.WeaponTag;

/**
 * Default {@link ShopDisplayResolver} for gangland. Resolves weapon items through {@link WeaponService} so the clean
 * configured display name is returned instead of the item's live display name (which carries the dynamic magazine
 * counter appended by {@code Weapon.buildDisplayName()}). Non-weapon items fall back to the stored display name or a
 * humanised material name.
 */
@RequiredArgsConstructor
public final class GanglandShopDisplayResolver implements ShopDisplayResolver {

	private final WeaponService weaponService;

	@Override
	public String cleanDisplayName(ItemStack item) {
		if (item == null) return "item";

		String weaponName = new ItemBuilder(item).getStringTagData(Weapon.getTagProperName(WeaponTag.WEAPON));
		if (weaponName != null && !weaponName.isEmpty()) {
			Weapon weapon = weaponService.getWeapon(weaponName);
			if (weapon != null && weapon.getDisplayName() != null && !weapon.getDisplayName().isBlank()) {
				// Weapon#getDisplayName returns the raw YAML string with '&' codes — translate before returning
				// so callers can drop the result straight into item display names / chat messages.
				return ChatUtil.color(weapon.getDisplayName());
			}
		}

		if (item.hasItemMeta()) {
			ItemMeta meta = item.getItemMeta();
			if (meta != null && meta.hasDisplayName()) {
				String displayName = meta.getDisplayName();
				if (!displayName.isBlank()) return ChatUtil.color(displayName);
			}
		}

		return ChatUtil.color(ChatUtil.capitalize(item.getType().name().toLowerCase().replace('_', ' ')));
	}

}
