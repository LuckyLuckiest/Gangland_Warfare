package org.luckyraven.gangland.sign.model;

import org.bukkit.Location;
import org.luckyraven.gangland.sign.SignType;

public class BountyParsedSign extends BaseParsedSign {

	public BountyParsedSign(SignType signType, String content, double price, int amount, Location location,
	                        String[] rawLines) {
		super(signType, content, price, amount, location, rawLines);
	}
}
