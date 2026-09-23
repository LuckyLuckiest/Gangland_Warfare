package org.luckyraven.gangland.gadget.sign;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.gadget.car.CarManager;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.extension.SignTypeContribution;
import org.luckyraven.gangland.sign.type.Sign;

import java.util.List;

import org.luckyraven.keystone.bean.Qualifier;
/**
 * Adds the {@code car-buy} and {@code car-sell} sign types to the core catalogue. Rebuilds exactly what
 * {@code SignManager.setupSigns()} used to build inline before the sign extension seam existed.
 */
public class CarSignContribution implements SignTypeContribution {

	private final UserManager<Player> userManager;
	private final CarManager          carManager;

	public CarSignContribution(@Qualifier("online") UserManager<Player> userManager, CarManager carManager) {
		this.userManager = userManager;
		this.carManager  = carManager;
	}

	@Override
	public List<Sign> signs(String signPrefix) {
		SignType carBuyType  = new SignType(signPrefix + "car-buy", "CAR-BUY");
		SignType carSellType = new SignType(signPrefix + "car-sell", "CAR-SELL");

		return List.of(new CarBuySign(userManager, carManager, carBuyType),
		               new CarSellSign(userManager, carManager, carSellType));
	}
}
