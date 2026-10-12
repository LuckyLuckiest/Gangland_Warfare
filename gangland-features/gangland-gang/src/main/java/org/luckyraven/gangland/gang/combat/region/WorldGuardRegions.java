package org.luckyraven.gangland.gang.combat.region;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;

import java.util.Optional;

/** The only gang class naming WorldGuard; reach it only when the plugin is present. */
public final class WorldGuardRegions {

	private static final String FLAG_NAME = "glw-gang-pvp";

	private WorldGuardRegions() {
	}

	public static FriendlyFireRegionSource create() {
		return at -> {
			// looked up by name each call: the flag exists only if the host registered it in onLoad
			Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get(FLAG_NAME);
			if (!(flag instanceof StateFlag) || at == null || at.getWorld() == null) {
				return Optional.empty();
			}
			StateFlag.State state = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
			                                  .queryState(BukkitAdapter.adapt(at), null, (StateFlag) flag);
			if (state == null) {
				return Optional.empty();
			}
			return Optional.of(state == StateFlag.State.ALLOW);
		};
	}
}
