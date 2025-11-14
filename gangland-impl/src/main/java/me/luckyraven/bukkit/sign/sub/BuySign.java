package me.luckyraven.bukkit.sign.sub;

import me.luckyraven.Gangland;
import org.bukkit.Location;

public class BuySign extends BaseTradeSign {

	public BuySign(Gangland gangland, double price, int amount, Location location) {
		super(gangland, "glw-buy", price, amount, location);
	}

}
