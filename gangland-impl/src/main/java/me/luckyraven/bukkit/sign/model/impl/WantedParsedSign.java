package me.luckyraven.bukkit.sign.model.impl;

import me.luckyraven.bukkit.sign.SignType;
import me.luckyraven.bukkit.sign.model.BaseParsedSign;
import org.bukkit.Location;

public class WantedParsedSign extends BaseParsedSign {

	public WantedParsedSign(SignType signType, String action, int stars, Location location, String[] rawLines) {
		super(signType, action, 0.0, stars, location, rawLines);

		setMetadata("action", action);
	}

	public String getAction() {
		return getContent();
	}

	public int getStars() {
		return getAmount();
	}

}
