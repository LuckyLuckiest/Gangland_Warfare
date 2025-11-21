package me.luckyraven.sign.model;

import me.luckyraven.sign.SignType;
import org.bukkit.Location;

public class ViewParsedSign extends BaseParsedSign {

	public ViewParsedSign(SignType signType, String content, Location location, String[] rawLines) {
		super(signType, content, 0.0, 0, location, rawLines);
	}

	public String getItemName() {
		return getContent();
	}

}
