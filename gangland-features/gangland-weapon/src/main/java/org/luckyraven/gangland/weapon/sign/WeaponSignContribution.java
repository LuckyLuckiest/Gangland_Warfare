package org.luckyraven.gangland.weapon.sign;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.extension.SignTypeContribution;
import org.luckyraven.gangland.sign.type.Sign;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.wearable.WearableService;

import java.util.List;

/**
 * Adds the {@code weapon-buy}, {@code weapon-sell}, {@code ammo-buy}, {@code ammo-sell}, {@code wearable-buy} and
 * {@code wearable-sell} sign types to the core catalogue. Rebuilds exactly what {@code SignManager.setupSigns()}
 * used to build inline before the sign extension seam existed.
 */
public class WeaponSignContribution implements SignTypeContribution {

	private final WeaponService       weaponService;
	private final AmmunitionManager   ammunitionManager;
	private final WearableService     wearableService;
	private final UserManager<Player> userManager;

	public WeaponSignContribution(WeaponService weaponService, AmmunitionManager ammunitionManager,
	                              WearableService wearableService, UserManager<Player> userManager) {
		this.weaponService     = weaponService;
		this.ammunitionManager = ammunitionManager;
		this.wearableService   = wearableService;
		this.userManager       = userManager;
	}

	@Override
	public List<Sign> signs(String signPrefix) {
		SignType weaponBuyType    = new SignType(signPrefix + "weapon-buy", "WEAPON-BUY");
		SignType weaponSellType   = new SignType(signPrefix + "weapon-sell", "WEAPON-SELL");
		SignType ammoBuyType      = new SignType(signPrefix + "ammo-buy", "AMMO-BUY");
		SignType ammoSellType     = new SignType(signPrefix + "ammo-sell", "AMMO-SELL");
		SignType wearableBuyType  = new SignType(signPrefix + "wearable-buy", "WEARABLE-BUY");
		SignType wearableSellType = new SignType(signPrefix + "wearable-sell", "WEARABLE-SELL");

		return List.of(
				new WeaponBuySign(userManager, weaponService, ammunitionManager, weaponBuyType),
				new WeaponSellSign(userManager, weaponService, ammunitionManager, weaponSellType),
				new AmmoBuySign(userManager, weaponService, ammunitionManager, ammoBuyType),
				new AmmoSellSign(userManager, weaponService, ammunitionManager, ammoSellType),
				new WearableBuySign(userManager, wearableService, wearableBuyType),
				new WearableSellSign(userManager, wearableService, wearableSellType));
	}
}
