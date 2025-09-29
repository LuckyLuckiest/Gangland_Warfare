package me.luckyraven.bukkit.sign.sub;

import me.luckyraven.bukkit.sign.Sign;
import me.luckyraven.util.NumberUtil;
import org.bukkit.Location;

public class WantedSign extends Sign {

	public WantedSign(Location location) {
		super("glw-wanted", location);
	}

	@Override
	public void validate(String[] lines) {
		String checkType = lines[1].toLowerCase();

		if (!(checkType.equals("add") || checkType.equals("remove") || checkType.equals("set")))
			throw new IllegalArgumentException("Unable to determine the type!");

		// the number of stars to do the action
		String amount = lines[2];

		try {
			Integer.parseInt(amount);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("The amount is not a number!");
		}

		// the payment to be done if any
		String checkPrice = lines[3];

		if (!NumberUtil.isValueFormatted(checkPrice)) try {
			Double.parseDouble(checkPrice);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("The price is not a number!");
		}
	}

}
