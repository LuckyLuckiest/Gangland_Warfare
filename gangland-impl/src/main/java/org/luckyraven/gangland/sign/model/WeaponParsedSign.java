package org.luckyraven.gangland.sign.model;

import org.bukkit.Location;
import org.luckyraven.gangland.sign.SignType;

public class WeaponParsedSign extends BaseParsedSign {

	public WeaponParsedSign(SignType signType, String content, double price, int amount, Location location,
	                        String[] rawLines) {
		super(signType, content, price, amount, location, rawLines);
	}

	public String getWeaponName() {
		return getContent();
	}
}
