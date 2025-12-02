package me.luckyraven.sign.model;

import me.luckyraven.sign.SignType;
import org.bukkit.Location;

public class WantedParsedSign extends BaseParsedSign {

	public WantedParsedSign(SignType signType, String action, int stars, double price, Location location,
							String[] rawLines) {
		super(signType, action, price, stars, location, rawLines);

		setMetadata("action", action);
	}

	public String getAction() {
		return getContent();
	}

	public int getStars() {
		return getAmount();
	}

}
