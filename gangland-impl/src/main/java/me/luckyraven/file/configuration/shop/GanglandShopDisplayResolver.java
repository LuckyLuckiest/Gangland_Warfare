package me.luckyraven.file.configuration.shop;

import lombok.RequiredArgsConstructor;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Default {@link ShopDisplayResolver} for gangland. Resolves weapon items through {@link WeaponService} so the clean
 * configured display name is returned instead of the item's live display name (which carries the dynamic magazine
 * counter appended by {@code Weapon.buildDisplayName()}). Non-weapon items fall back to the stored display name or a
 * humanised material name.
 */
@RequiredArgsConstructor
public final class GanglandShopDisplayResolver implements ShopDisplayResolver {

	private static final String WEAPON_NBT_KEY = "weapon";

	private final WeaponService weaponService;

	@Override
	public String cleanDisplayName(ItemStack item) {
		if (item == null) return "item";

		String weaponName = new ItemBuilder(item).getStringTagData(WEAPON_NBT_KEY);
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
