package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** The lock-on filter registered on {@code CopPerception}: may this cop pick this player up (0.20.0 lane disguise). */
@CustomLog
public class DisguiseFilter implements BiPredicate<CopNpc, Player> {

	private final Supplier<DisguiseSettings>  settings;
	private final SuspectDescriptions         descriptions;
	private final PoliceOutfit                outfit;
	private final ToIntFunction<Player>       levelOf;
	private final Predicate<Player>           freshSighting;
	private final Map<UUID, String>           lastState = new ConcurrentHashMap<>();

	public DisguiseFilter(Supplier<DisguiseSettings> settings, SuspectDescriptions descriptions, PoliceOutfit outfit,
	                      ToIntFunction<Player> levelOf, Predicate<Player> freshSighting) {
		this.settings = settings;
		this.descriptions = descriptions;
		this.outfit = outfit;
		this.levelOf = levelOf;
		this.freshSighting = freshSighting;
	}

	public boolean allows(CopNpc cop, Player player) {
		DisguiseSettings settings = this.settings.get();
		if (!settings.enabled()) {
			return true;
		}

		int level = levelOf.applyAsInt(player);
		if (outfit.hides(player, level)) {
			return report(player, "outfit", false);
		}

		SuspectDescription description = descriptions.latest(player.getUniqueId());
		if (description == null || freshSighting.test(player)) {
			return true;
		}

		boolean blown = outfit.worn(player) && level <= settings.outfitMaxLevel();
		boolean match = descriptions.matches(player, description, settings.matchNeeded());
		return report(player, blown ? "blown" : match ? "match" : "mismatch", match);
	}

	@Override
	public boolean test(CopNpc cop, Player player) {
		return allows(cop, player);
	}

	/** Logs the state only when it changes for this player, then returns {@code allowed}. */
	private boolean report(Player player, String state, boolean allowed) {
		String previous = lastState.put(player.getUniqueId(), state);
		if (!state.equals(previous)) {
			log.debug("DISGUISE {} state={}", player.getName(), state);
		}
		return allowed;
	}
}
