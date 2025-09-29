package me.luckyraven.bukkit.sign.sub;

import me.luckyraven.Gangland;
import org.bukkit.Location;

public class SellSign extends BaseTradeSign {

	public SellSign(Gangland gangland, double price, int amount, Location location) {
		super(gangland, "glw-sell", price, amount, location);
	}

}
