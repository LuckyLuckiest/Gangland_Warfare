package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The AI tick hook: keeps the description current while seen and radios it once per change (0.20.0 lane disguise). */
public class DisguiseTick {

	private static final String LINE = "Suspect_Description";

	private final Supplier<DisguiseSettings>        settings;
	private final SuspectDescriptions               descriptions;
	private final DisguiseFilter                    filter;
	private final PoliceOutfit                      outfit;
	private final BiPredicate<Player, CopGroup>     eyesOn;
	private final CopRadio                          radio;
	private final Map<UUID, String>                 spoken  = new ConcurrentHashMap<>();
	private final List<Consumer<UUID>>              forgetHooks = new CopyOnWriteArrayList<>();

	public DisguiseTick(Supplier<DisguiseSettings> settings, SuspectDescriptions descriptions, DisguiseFilter filter,
	                    PoliceOutfit outfit, BiPredicate<Player, CopGroup> eyesOn, CopRadio radio) {
		this.settings     = settings;
		this.descriptions = descriptions;
		this.filter       = filter;
		this.outfit       = outfit;
		this.eyesOn       = eyesOn;
		this.radio        = radio;
	}

	public void tick(Player player, @Nullable CopGroup group) {
		UUID id = player.getUniqueId();

		// before the group check: shutdown and an emptied group tick with no group (rows 411, 430)
		if (!filter.hunted(player)) {
			if (descriptions.latest(id) != null || spoken.containsKey(id)) {
				descriptions.clear(id);
				forget(id);
			}
			return;
		}
		if (group == null) {
			spoken.remove(id);
			return;
		}
		if (!settings.get().enabled()) {
			return;
		}

		NpcSquad squad = group.getSquad();
		if (squad != null) {
			if (eyesOn.test(player, group) || squad.hasFreshSighting()) {
				descriptions.store(id, descriptions.describe(player));
			}
		}

		SuspectDescription latest = descriptions.latest(id);
		if (latest == null) {
			return;
		}
		String text = descriptions.format(latest);
		if (text.equals(spoken.get(id))) {
			return;
		}
		if (radio.sayFromLeader(group, LINE)) {
			spoken.put(id, text);
		}
	}

	/** Registers a hook run with the player id whenever a chase ends ({@link #forget}). */
	public void onForget(Consumer<UUID> hook) {
		forgetHooks.add(hook);
	}

	/** Forgets the line spoken for {@code playerId}: the chase ended, so the next chase says it again. */
	public void forget(UUID playerId) {
		spoken.remove(playerId);
		forgetHooks.forEach(hook -> hook.accept(playerId));
	}

	/** The {@code %suspect%} placeholder for the hunted player {@code id}; the Unknown word when no description is known. */
	public Map<String, String> extras(@Nullable UUID id) {
		SuspectDescription latest = id == null ? null : descriptions.latest(id);

		return Map.of("suspect", latest == null ? descriptions.unknownText() : descriptions.format(latest));
	}
}
