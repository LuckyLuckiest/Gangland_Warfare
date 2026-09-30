package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.gangland.npc.radio.RadioLines;
import org.luckyraven.keystone.persistence.FileManager;

import java.util.List;
import java.util.Map;

/**
 * Police-radio lines, backed by the module's own {@code npc/cop_radio_messages.yml} (English, always shipped) /
 * {@code npc/cop_radio_messages_es.yml} (Spanish, optional — picked by {@code Settings.getLanguagePicked()} via
 * {@link LocalizedModuleYaml}). {@code Lines.<key>} holds the candidate lines for one radio signal; {@code Format},
 * {@code Dispatch_Format}, {@code Compass} and {@code Sides} live at the document root, not under {@code Lines}, and
 * are read straight from there.
 *
 * @since 1.13.0
 */
public class CopRadioMessages extends LocalizedModuleYaml implements RadioLines {

	private static final String BASE_NAME = "cop_radio_messages";

	private static final List<String> DEFAULT_FORMAT          = List.of("&9&l[RADIO] &b%unit%&8: &7%line%");
	private static final List<String> DEFAULT_DISPATCH_FORMAT = List.of("&9&l[DISPATCH] &7%line%");
	private static final List<String> DEFAULT_COMPASS         = List.of("north", "north-east", "east", "south-east",
	                                                                    "south", "south-west", "west", "north-west");
	private static final List<String> DEFAULT_SIDES           = List.of("front", "left", "right", "back");

	private static final Map<String, List<String>> DEFAULT_LINES = Map.ofEntries(
			Map.entry("Contact", List.of("Contact! Suspect %distance% blocks %direction%!",
			                             "Eyes on %target%, %direction%!")),
			Map.entry("Contact_Lost", List.of("Lost visual! Check his last known.", "Where'd he go?!")),
			Map.entry("Engage", List.of("Suspect in sight, engaging!", "Shots fired, engaging!")),
			Map.entry("Push", List.of("%member%, move in on him!", "%member%, close the distance!")),
			Map.entry("Flank_Left", List.of("%member%, take his %side%!",
			                                "%member%, swing round and cover his %side%!")),
			Map.entry("Flank_Right", List.of("%member%, take his %side%!",
			                                 "%member%, cut him off on his %side%!")),
			Map.entry("Reposition", List.of("Moving, cover me!", "Changing position!")),
			Map.entry("Search", List.of("Spread out, find him!", "Sweep the area!")),
			Map.entry("Route", List.of("Going round, finding a way to him.", "Cutting round to him!")),
			Map.entry("Route_High", List.of("He's up high, finding a way up.")),
			Map.entry("Climb", List.of("Taking the ladder!", "Going up!")),
			Map.entry("No_Route", List.of("Can't reach him, surround the building!", "Hold the exits!")),
			Map.entry("Check_Fire", List.of("Check your fire, friendly in the line!")),
			Map.entry("Reloading", List.of("Reloading, cover me!", "Changing mags!")),
			Map.entry("Man_Down", List.of("Officer down! %member% is down!", "Man down!")),
			Map.entry("Leader_Down", List.of("%member% is down! %unit% taking point!")),
			Map.entry("Commander_Down", List.of("Commander down! Fall back, fall back!",
			                                    "We lost %member%! Pull back to cover!")),
			Map.entry("Ack", List.of("Copy.", "Moving.", "On it.")),
			Map.entry("Responding", List.of("%unit% responding, en route!", "Copy, moving to assist!")),
			Map.entry("Fall_Back", List.of("Taking fire, falling back to cover!", "I'm hit! Pulling back!")),
			Map.entry("In_Cover", List.of("In cover!", "I'm behind cover, keep him busy!")),
			Map.entry("Resisting", List.of("Suspect is resisting! Take him down!")),
			Map.entry("Backup", List.of("Requesting backup at my location!", "Need more units here, now!")),
			Map.entry("Dispatch_Wanted", List.of("All units, be advised: %target% is wanted, level %level%.")),
			Map.entry("Escalate", List.of("%tier% units en route to %target%.")),
			Map.entry("Stand_Down", List.of("Suspect cleared. Returning to patrol.")));

	public CopRadioMessages(FileManager fileManager) {
		super(fileManager, BASE_NAME);
	}

	/**
	 * {@code Format}/{@code Dispatch_Format}/{@code Compass}/{@code Sides} are read at the document root; every other
	 * key is a signal's line pool, read under {@code Lines.<key>}. An unrecognised key falls back to the empty list
	 * (silent), same as an explicit {@code key: []} in YAML.
	 */
	@Override
	public List<String> lines(String key) {
		return switch (key) {
			case "Format" -> list("Format", DEFAULT_FORMAT);
			case "Dispatch_Format" -> list("Dispatch_Format", DEFAULT_DISPATCH_FORMAT);
			case "Compass" -> list("Compass", DEFAULT_COMPASS);
			case "Sides" -> list("Sides", DEFAULT_SIDES);
			default -> list("Lines." + key, DEFAULT_LINES.getOrDefault(key, List.of()));
		};
	}
}
