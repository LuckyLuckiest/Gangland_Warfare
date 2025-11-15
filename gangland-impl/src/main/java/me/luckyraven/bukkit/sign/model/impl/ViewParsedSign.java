package me.luckyraven.bukkit.sign.model.impl;

import me.luckyraven.bukkit.sign.SignType;
import me.luckyraven.bukkit.sign.model.BaseParsedSign;
import org.bukkit.Location;

public class ViewParsedSign extends BaseParsedSign {

	public ViewParsedSign(SignType signType, String content, Location location, String[] rawLines) {
		super(signType, content, 0.0, 0, location, rawLines);
	}

	public String getItemName() {
		return getContent();
	}

}
