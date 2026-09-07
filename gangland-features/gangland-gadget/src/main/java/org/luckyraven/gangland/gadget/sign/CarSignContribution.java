package org.luckyraven.gangland.gadget.sign;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.gadget.car.CarManager;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.extension.SignTypeContribution;
import org.luckyraven.gangland.sign.type.Sign;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;

import java.util.List;

/**
 * Adds the {@code car-buy} and {@code car-sell} sign types to the core catalogue. Rebuilds exactly what
 * {@code SignManager.setupSigns()} used to build inline before the sign extension seam existed.
 */
public class CarSignContribution implements SignTypeContribution {

	private final UserManager<Player> userManager;
	private final CarManager          carManager;
	private final WeaponService       weaponService;
	private final AmmunitionManager   ammunitionManager;

	public CarSignContribution(UserManager<Player> userManager, CarManager carManager, WeaponService weaponService,
	                           AmmunitionManager ammunitionManager) {
		this.userManager       = userManager;
		this.carManager        = carManager;
		this.weaponService     = weaponService;
		this.ammunitionManager = ammunitionManager;
	}

	@Override
	public List<Sign> signs(String signPrefix) {
		SignType carBuyType  = new SignType(signPrefix + "car-buy", "CAR-BUY");
		SignType carSellType = new SignType(signPrefix + "car-sell", "CAR-SELL");

		return List.of(new CarBuySign(userManager, carManager, weaponService, ammunitionManager, carBuyType),
		               new CarSellSign(userManager, carManager, weaponService, ammunitionManager, carSellType));
	}
}
