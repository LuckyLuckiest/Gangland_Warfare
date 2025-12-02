package me.luckyraven.sign.model;

import me.luckyraven.sign.SignType;
import org.bukkit.Location;

public class BountyParsedSign extends BaseParsedSign {

	public BountyParsedSign(SignType signType, String content, double price, int amount, Location location,
							String[] rawLines) {
		super(signType, content, price, amount, location, rawLines);
	}
}
