package org.luckyraven.gangland.sign.model;

import org.bukkit.Location;
import org.luckyraven.gangland.sign.SignType;

public class ViewParsedSign extends BaseParsedSign {

	public ViewParsedSign(SignType signType, String content, Location location, String[] rawLines) {
		super(signType, content, 0.0, 0, location, rawLines);
	}

	public String getItemName() {
		return getContent();
	}

}
