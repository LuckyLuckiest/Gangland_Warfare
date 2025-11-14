package me.luckyraven.bukkit.sign.sub;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.sign.Sign;
import me.luckyraven.weapon.Weapon;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;

public class ViewSign extends Sign {

	private final Gangland gangland;

	public ViewSign(Gangland gangland, Location location) {
		super("glw-view", location);

		this.gangland = gangland;
	}

	@Override
	public void validate(String[] lines) {
		super.validate(lines);

		// the second line should have: item name
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
	}

}
