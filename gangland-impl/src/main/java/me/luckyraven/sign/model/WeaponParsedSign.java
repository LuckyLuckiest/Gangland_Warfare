package me.luckyraven.sign.model;

import me.luckyraven.sign.SignType;
import org.bukkit.Location;

public class WeaponParsedSign extends BaseParsedSign {

	public WeaponParsedSign(SignType signType, String content, double price, int amount, Location location,
							String[] rawLines) {
		super(signType, content, price, amount, location, rawLines);
	}

	public String getWeaponName() {
		return getContent();
	}
}
