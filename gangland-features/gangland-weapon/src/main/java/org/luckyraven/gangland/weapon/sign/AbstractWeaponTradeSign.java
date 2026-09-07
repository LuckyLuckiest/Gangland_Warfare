package org.luckyraven.gangland.weapon.sign;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.sign.aspect.ItemTransferAspect.ItemSimilarityChecker;
import org.luckyraven.gangland.sign.type.trade.BaseTradeSign;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.ammo.Ammunition;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.types.WeaponType;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * The weapon/ammo half of the core's former {@code BaseTradeSign} — carries {@code weaponService}/
 * {@code ammunitionManager} plus the four lookup/similarity helpers, moved out verbatim when the core class was
 * stripped to its unique/material lookup only. {@code WearableBuySign}/{@code WearableSellSign} never used these
 * two params, so they extend plain {@code BaseTradeSign} directly instead.
 */
@Getter
@RequiredArgsConstructor
public abstract class AbstractWeaponTradeSign extends BaseTradeSign {

	private final WeaponService     weaponService;
	private final AmmunitionManager ammunitionManager;

	protected ItemStack getWeaponItem(String weaponName) {
		Weapon template = weaponService.getWeaponTemplates()
				.stream()
				.filter(w -> w.getName().equalsIgnoreCase(weaponName))
				.findFirst()
				.orElse(null);
		if (template == null) return null;

		UUID uuid;
		if (template.getCategory() == WeaponType.THROWABLE) {
			uuid = UUID.nameUUIDFromBytes(("throwable:" + template.getName()).getBytes(StandardCharsets.UTF_8));
		} else {
			uuid = UUID.randomUUID();
		}
		return template.copyWithUUID(uuid).buildItem();
	}

	protected ItemStack getAmmoItem(String ammoKey) {
		return ammunitionManager.getAmmunitionKeys()
				.stream()
				.filter(k -> k.equalsIgnoreCase(ammoKey))
				.map(ammunitionManager::getAmmunition)
				.filter(Objects::nonNull)
				.findFirst()
				.map(Ammunition::buildItem)
				.orElse(null);
	}

	protected ItemSimilarityChecker weaponSimilarityChecker() {
		return (player, a, b) -> {
			if (a.getType() != b.getType()) return false;
			if (!weaponService.isWeapon(a) || !weaponService.isWeapon(b)) return a.isSimilar(b);
			Weapon w1 = weaponService.validateAndGetWeapon(player, a);
			Weapon w2 = weaponService.validateAndGetWeapon(player, b);
			return w1 != null && w2 != null && weaponService.compare(w1, w2) == 0;
		};
	}

	protected ItemSimilarityChecker ammoSimilarityChecker() {
		return (player, a, b) -> {
			if (a.getType() != b.getType()) return false;
			if (!Ammunition.isAmmunition(a) || !Ammunition.isAmmunition(b)) return a.isSimilar(b);
			Ammunition ammo1 = ammunitionManager.getAmmunition(new ItemBuilder(a).getStringTagData(Ammunition.NBT_KEY));
			Ammunition ammo2 = ammunitionManager.getAmmunition(new ItemBuilder(b).getStringTagData(Ammunition.NBT_KEY));
			return ammo1 != null && ammo2 != null && ammo1.compareTo(ammo2) == 0;
		};
	}
}
