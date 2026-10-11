package org.luckyraven.gangland.gang;

import org.bukkit.World;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.file.GangRules;

import java.util.Locale;
import java.util.Set;

/**
 * Where gangs exist (GR-40). Gangs are off everywhere when {@code settings.yml} {@code Gang.Enable} is false, and off in
 * every world listed in {@code gang_rules.yml} {@code Worlds.Disabled}. A {@code null} world follows the global switch.
 */
public class GangWorldPolicy {

	private final GangRules rules;

	public GangWorldPolicy(GangRules rules) {
		this.rules = rules;
	}

	/** Whether gangs are turned on at all on this server. */
	public boolean enabled() {
		return Settings.isGangEnabled();
	}

	/** Whether gangs exist in {@code world}; a {@code null} world answers {@link #enabled()}. */
	public boolean enabledIn(@Nullable World world) {
		if (!enabled()) return false;
		if (world == null) return true;

		Set<String> disabled = rules.getDisabledWorlds();
		return !disabled.contains(world.getName().toLowerCase(Locale.ROOT));
	}
}
