package org.luckyraven.gangland.weapon.command;

import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.Collection;
import java.util.List;

/**
 * Attaches {@code /glw debug weapon} under the core's {@code /glw debug} argument. Rebuilds exactly the
 * {@code getGiveGun()} argument deleted from {@code DebugCommand} when the weapon stack moved into this module.
 */
public final class DebugWeaponContribution implements CommandContribution {

	public static final String PARENT = "debug";

	private final Gangland      gangland;
	private final WeaponManager weaponManager;

	public DebugWeaponContribution(Gangland gangland, WeaponManager weaponManager) {
		this.gangland      = gangland;
		this.weaponManager = weaponManager;
	}

	@Override
	public String parent() {
		return PARENT;
	}

	@Override
	public List<Argument> create(Tree<Argument> tree, Argument parent) {
		Argument argument = new Argument(gangland, "weapon", tree, (arg, sender, args) -> {
			Collection<Weapon> values = weaponManager.getWeapons().values();
			for (Weapon weapon : values) {
				sender.sendMessage(weapon.getUuid().toString());
			}
		});
		return List.of(argument);
	}
}
