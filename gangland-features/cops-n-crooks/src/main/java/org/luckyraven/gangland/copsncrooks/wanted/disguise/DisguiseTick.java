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
 * The AI tick hook: a cop of the chase sees him now and knows him (or was already watching him) - recognised; then the
 * description follows what he wears, and it is radioed once per change (0.20.0 lane disguise, 0.21.5 live sight).
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
		if (group == null) {
			spoken.remove(id);
			told.remove(id);
			descriptions.setRecognised(id, false);
			return;
		}
		if (!settings.get().enabled()) {
			return;
		}

		// Behaviours read the flag set here on the previous AI tick (cop.tick runs before the hooks).
		// ponytail: zero grace - one AI tick (0.5 s) with no group cop seeing him drops recognition;
		// a recognisedUntil = now + grace is the upgrade if a corner should need longer
		boolean eyes  = eyesOn.test(player, group);
		boolean known = eyes && (descriptions.isRecognised(id) || filter.knows(player));
		descriptions.setRecognised(id, known);
		if (known) {
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
		boolean            aired  = text != null && !text.equals(spoken.get(id)) && radio.sayFromLeader(group, LINE);
		if (aired) {
			spoken.put(id, text);
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

	/** Registers a hook run with the player id whenever a chase ends ({@link #forget}). */
	public void onForget(Consumer<UUID> hook) {
		forgetHooks.add(hook);
	}

	/** Forgets the line spoken for {@code playerId}: the chase ended, so the next chase says it again. */
	public void forget(UUID playerId) {
		spoken.remove(playerId);
		told.remove(playerId);
		forgetHooks.forEach(hook -> hook.accept(playerId));
	}

	/** The {@code %suspect%} placeholder for the hunted player {@code id}; the Unknown word with no description. */
	public Map<String, String> extras(@Nullable UUID id) {
		SuspectDescription latest = id == null ? null : descriptions.latest(id);

		return Map.of("suspect", latest == null ? descriptions.unknownText() : descriptions.format(latest));
	}
}
