package org.luckyraven.gangland.gadget.sign;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gadget.car.CarManager;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.aspect.ItemTransferAspect;
import org.luckyraven.gangland.sign.aspect.MoneyAspect;
import org.luckyraven.keystone.bean.Qualifier;

public class CarSellSign extends AbstractCarTradeSign {

	public CarSellSign(@Qualifier("online") UserManager<Player> userManager, CarManager carManager, SignType signType) {
		super(userManager, carManager, signType, MoneyAspect.TransactionType.DEPOSIT, ItemTransferAspect.TransferType.TAKE);
	}

}
