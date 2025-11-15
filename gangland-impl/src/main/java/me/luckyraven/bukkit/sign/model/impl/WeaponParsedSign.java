package me.luckyraven.bukkit.sign.model.impl;

import me.luckyraven.bukkit.sign.SignType;
import me.luckyraven.bukkit.sign.model.BaseParsedSign;
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
