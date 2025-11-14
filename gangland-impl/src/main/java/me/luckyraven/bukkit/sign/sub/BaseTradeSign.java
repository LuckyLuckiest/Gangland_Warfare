package me.luckyraven.bukkit.sign.sub;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.sign.Sign;
import me.luckyraven.util.utilities.NumberUtil;
import me.luckyraven.weapon.Weapon;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;

@Getter
public abstract class BaseTradeSign extends Sign {

	protected final double   price;
	protected final int      amount;
	private final   Gangland gangland;

	public BaseTradeSign(Gangland gangland, String signType, double price, int amount, Location location) {
		super(signType, location);

		this.price    = price;
		this.amount   = amount;
		this.gangland = gangland;
	}

	@Override
	public void validate(String[] lines) {
		super.validate(lines);

		String  checkItem = lines[1].toLowerCase();
		boolean itemFound = false;

		// check if it was a weapon
		for (Map.Entry<UUID, Weapon> entry : gangland.getInitializer().getWeaponManager().getWeapons().entrySet())
			if (entry.getValue().getName().equalsIgnoreCase(checkItem)) {
				itemFound = true;
				break;
			}

		if (!itemFound) for (String ammunition : gangland.getInitializer().getAmmunitionAddon().getAmmunitionKeys())
			if (ammunition.equalsIgnoreCase(checkItem)) {
				itemFound = true;
				break;
			}

		if (!itemFound) throw new IllegalArgumentException("The item name line is wrong!");

		// any number length should be a maximum of 8 characters, 2 characters are used for coloring
		// the third line should have: quantity
		String checkQuantity = lines[2];

		if (!NumberUtil.isValueFormatted(checkQuantity)) try {
			Integer.parseInt(checkQuantity);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("The quantity is not a number!");
		}

		if (checkQuantity.length() > 8)
			throw new IllegalArgumentException("The quantity value should be 8 characters long!");

		// the fourth line should have: price
		String checkPrice = lines[3];

		if (!NumberUtil.isValueFormatted(checkPrice)) try {
			Double.parseDouble(checkPrice);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("The price is not a number!");
		}

		if (checkPrice.length() > 8) {
			throw new IllegalArgumentException("The price value should be 8 characters long!");
		}
	}

}
