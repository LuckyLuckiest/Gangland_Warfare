package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.keystone.npc.NpcDifficulty;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
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
	private final Predicate<Player>           hunted;
	private final Function<Player, NpcDifficulty> difficulty;
	private final Map<UUID, String>           lastState = new ConcurrentHashMap<>();

	public DisguiseFilter(Supplier<DisguiseSettings> settings, SuspectDescriptions descriptions, PoliceOutfit outfit,
	                      ToIntFunction<Player> levelOf, Predicate<Player> hunted,
	                      Function<Player, NpcDifficulty> difficulty) {
		this.settings = settings;
		this.descriptions = descriptions;
		this.outfit = outfit;
		this.levelOf = levelOf;
		this.hunted = hunted;
		this.difficulty = difficulty;
	}

	/** Whether the cops still hunt this player: stars above 0, or a post-escape search for them. */
	public boolean hunted(Player player) {
		return hunted.test(player);
	}

	/** How many pieces must differ for the cops at his stars right now. */
	public int changesNeeded(Player player) {
		return settings.get().changesNeeded(difficulty.apply(player));
	}

	/** How long the cops remember him after the last sighting, at his stars right now. */
	public long memoryMillis(Player player) {
		return settings.get().memoryMillis(difficulty.apply(player));
	}

	/** Disabled, or recognised on the last AI tick ({@code seen}), or else {@link #knows}. */
	public boolean allows(CopNpc cop, Player player) {
		if (!settings.get().enabled()) {
			return true;
		}
		if (descriptions.isRecognised(player.getUniqueId())) {
			return report(player, "seen", true);
		}
		return knows(player);
	}

	/** Whether a cop who has not been watching him knows him by the description: the outfit, then the match. */
	public boolean knows(Player player) {
		DisguiseSettings settings = this.settings.get();
		int              level    = levelOf.applyAsInt(player);
		if (outfit.hides(player, level)) {
			return report(player, "outfit", false);
		}
		if (!hunted.test(player)) {
			return true;
		}

		SuspectDescription description = descriptions.latest(player.getUniqueId());
		if (description == null) {
			return true;
		}

		boolean blown = outfit.worn(player) && level <= settings.outfitMaxLevel();
		boolean match = descriptions.matches(player, description, changesNeeded(player));
		return report(player, blown ? "blown" : match ? "match" : "mismatch", blown || match);
	}

	/** The police outfit fools the cops at his wanted level right now. */
	public boolean outfitHides(Player player) {
		return outfit.hides(player, levelOf.applyAsInt(player));
	}

	@Override
	public boolean test(CopNpc cop, Player player) {
		return allows(cop, player);
	}

	/** Forgets the last logged state for {@code id}: the chase ended. */
	public void forget(UUID id) {
		lastState.remove(id);
	}

	/** The last logged state for {@code id}; tests. */
	@Nullable String lastState(UUID id) {
		return lastState.get(id);
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
