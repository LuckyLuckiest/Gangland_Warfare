package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The AI tick hook: a cop of the chase sees the player and knows them - recognised; then the description follows what
 * they wear, and it is radioed once per change (0.20.0 lane disguise, 0.21.5 live sight). Recognition outlives the
 * sighting by the memory for the player's stars ({@link DisguiseFilter#memoryMillis}, 0.21.6): a change made inside it
 * is learned at the next sighting, and only a sighting teaches the cops anything.
 */
public class DisguiseTick {

	private static final String LINE = "Suspect_Description";

	private final Supplier<DisguiseSettings>    settings;
	private final SuspectDescriptions           descriptions;
	private final DisguiseFilter                filter;
	private final PoliceOutfit                  outfit;
	private final BiPredicate<Player, CopGroup> eyesOn;
	private final CopRadio                      radio;
	private final WantedMessages                messages;
	private final Map<UUID, String>             spoken      = new ConcurrentHashMap<>();
	private final Map<UUID, WantedMessages.Key> told        = new ConcurrentHashMap<>();
	private final Map<UUID, Long>               lastSeen    = new ConcurrentHashMap<>();
	private final List<Consumer<UUID>>          forgetHooks = new CopyOnWriteArrayList<>();

	public DisguiseTick(Supplier<DisguiseSettings> settings, SuspectDescriptions descriptions, DisguiseFilter filter,
	                    PoliceOutfit outfit, BiPredicate<Player, CopGroup> eyesOn, CopRadio radio,
	                    WantedMessages messages) {
		this.settings     = settings;
		this.descriptions = descriptions;
		this.filter       = filter;
		this.outfit       = outfit;
		this.eyesOn       = eyesOn;
		this.radio        = radio;
		this.messages     = messages;
	}

	public void tick(Player player, @Nullable CopGroup group) {
		UUID id = player.getUniqueId();

		// before the group check: shutdown and an emptied group tick with no group (rows 411, 430)
		if (!filter.hunted(player)) {
			// the search giving up fires no WantedEndEvent, and a quit wiped the description but kept the blow;
			// kept out of forget so a quit does not un-blow it
			outfit.clear(id);
			if (descriptions.latest(id) != null || spoken.containsKey(id)) {
				descriptions.clear(id);
				forget(id);
			}
			return;
		}
		if (!settings.get().enabled()) {
			return;
		}

		// Behaviours read the flag set here on the previous AI tick (cop.tick runs before the hooks).
		// ponytail: wall clock; an AI stall longer than the memory (under about 3 TPS at 3 s) reads a swap in view as
		// unseen, a tick counter is the upgrade if that ever shows
		long    now   = radio.now();
		boolean was   = descriptions.isRecognised(id);
		Long    seen  = lastSeen.get(id);
		// the memory for the stars now; a flag only counts with a clock inside it, so a stale one holds nobody
		boolean held  = was && seen != null && now - seen < filter.memoryMillis(player);
		boolean eyes  = group != null && eyesOn.test(player, group);
		boolean known = held || (eyes && filter.knows(player));
		descriptions.setRecognised(id, known);
		// only a cop who sees the player learns anything
		if (eyes && known) {
			lastSeen.put(id, now);
			descriptions.store(id, descriptions.describe(player));
			// they see him in it: it no longer fools them
			if (outfit.worn(player)) {
				outfit.blow(id);
			}
		}
		WantedMessages.Key key = known ? WantedMessages.Key.DISGUISE_RECOGNISED
		                               : filter.outfitHides(player) ? WantedMessages.Key.DISGUISE_OUTFIT
		                                                            : WantedMessages.Key.DISGUISE_UNRECOGNISED;

		SuspectDescription latest = descriptions.latest(id);
		String             text   = latest == null ? null : descriptions.format(latest);
		boolean            aired  = group != null && text != null && !text.equals(spoken.get(id))
		                            && radio.sayFromLeader(group, LINE);
		if (aired) {
			spoken.put(id, text);
		}
		// no squad to say it: the next squad says it again
		if (group == null) {
			spoken.remove(id);
		}
		// a new look while recognised is told when it is radioed, so the radio cooldown also paces the chat
		if (eyes) {
			tell(player, key, aired && known);
		}
	}

	/**
	 * Chat line for {@code key}, sent when the state changed, or {@code again} (a new look radioed while recognised);
	 * '' in the file turns a line off.
	 */
	private void tell(Player player, WantedMessages.Key key, boolean again) {
		UUID id = player.getUniqueId();
		if (key == told.put(id, key) && !again) {
			return;
		}

		SuspectDescription latest  = descriptions.latest(id);
		String             suspect = latest == null ? descriptions.unknownText() : descriptions.format(latest);
		String             line    = messages.format(key, Map.of("suspect", suspect));
		if (!line.isBlank()) {
			player.sendMessage(line);
		}
	}

	/** A cop was looking at the player: the memory starts now, and the next cop to see them learns what they wear then. */
	public void witnessed(Player player) {
		descriptions.setRecognised(player.getUniqueId(), true);
		lastSeen.put(player.getUniqueId(), radio.now());
	}

	/** An escape ends the memory; the description and the blown outfit stay for the search. The next sighting is told. */
	public void escaped(UUID id) {
		descriptions.setRecognised(id, false);
		lastSeen.remove(id);
		told.remove(id);
	}

	/** Registers a hook run with the player id whenever a chase ends ({@link #forget}). */
	public void onForget(Consumer<UUID> hook) {
		forgetHooks.add(hook);
	}

	/** Forgets the line spoken for {@code playerId}: the chase ended, so the next chase says it again. */
	public void forget(UUID playerId) {
		spoken.remove(playerId);
		told.remove(playerId);
		lastSeen.remove(playerId);
		forgetHooks.forEach(hook -> hook.accept(playerId));
	}

	/** The {@code %suspect%} placeholder for the hunted player {@code id}; the Unknown word with no description. */
	public Map<String, String> extras(@Nullable UUID id) {
		SuspectDescription latest = id == null ? null : descriptions.latest(id);

		return Map.of("suspect", latest == null ? descriptions.unknownText() : descriptions.format(latest));
	}
}
