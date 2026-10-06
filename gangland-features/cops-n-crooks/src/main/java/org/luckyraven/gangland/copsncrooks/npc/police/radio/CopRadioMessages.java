package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.gangland.npc.radio.RadioLines;
import org.luckyraven.keystone.persistence.FileManager;

import java.util.List;
import java.util.Map;

/**
 * Police-radio lines, backed by the module's own {@code copsncrooks/cop_radio_messages.yml} (English, always shipped) /
 * {@code copsncrooks/cop_radio_messages_es.yml} (Spanish, optional — picked by {@code Settings.getLanguagePicked()} via
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
			Map.entry("Fall_Back", List.of("Taking fire, falling back to cover!", "Pulling back, cover me!")),
			Map.entry("In_Cover", List.of("In cover!", "I'm behind cover, keep him busy!")),
			Map.entry("Hit", List.of("I'm hit! I'm at %health%%, need a medic!", "Taking hits, I'm bleeding - %health%% left, need a medic!", "Hit, I'm at %health%%, somebody cover me!")),
			Map.entry("Medic_Moving", List.of("Medic en route to %member%, %distance% blocks out, ETA %eta% seconds!", "%member%, hold on, %distance% blocks out, I'm coming to you!", "Moving up to %member%, %distance% blocks, stay down!")),
			Map.entry("Covering_Fire", List.of("Cover the medic, lay it down!", "Covering fire!")),
			Map.entry("Medic_Pinned", List.of("Pinned down, can't reach %member%!")),
			Map.entry("Patched_Up", List.of("%member% is stable at %health%%, back in it!", "Patient stable, %health%% health. You're good, get back in there!", "%member% patched up - %health%%. Back in the fight!")),
			Map.entry("Overwatch_Set", List.of("Overwatch in position, %distance% blocks %direction%, I have eyes on the suspect.", "%role% set, %distance% blocks %direction%. Eyes on %target%.", "In the nest, %distance% blocks %direction%. Clear line on the suspect.")),
			Map.entry("Marksman_Spotted", List.of("Target spotted, %distance% blocks %direction%! Holding for the shot.", "I have eyes on %target%, %distance% blocks %direction%!", "Visual on the suspect, %direction%, range %distance%.")),
			Map.entry("Marksman_Lost", List.of("Lost the target! Scanning %direction%.", "Target's out of my scope, last seen %direction%.", "No visual from overwatch, check %direction%!")),
			Map.entry("Marksman_Reloading", List.of("Overwatch reloading, I'm blind for a few seconds!", "Changing mags, someone watch my sector!", "Reloading the rifle, keep his head down!")),
			Map.entry("Shield_Up", List.of("Shield up, holding the line!", "%role% up front, nobody gets past me!", "Covering the squad, shields up!")),
			Map.entry("Flanking", List.of("Moving to flank, %direction%!", "Going wide %direction%, I'll get behind him!", "Flanking %direction%, keep him busy!")),
			Map.entry("Commander_Orders", List.of("Defender, hold the line! Assault, flank %direction%! Marksman, overwatch!", "All units, engage! Defender up front, Assault go wide %direction%!", "Squad, positions! Defender hold, Assault flank, Marksman take the high ground!")),
			Map.entry("Commander_Orders_Undirected", List.of("All units, engage! Stay together and cover each other!", "Squad, positions! Watch your sectors!", "Contact ahead! Spread out and keep him pinned!")),
			Map.entry("Flanking_Undirected", List.of("Moving to flank!", "Going wide, I'll get behind him!", "Flanking, keep him busy!")),
			Map.entry("Commander_Orders_Basic", List.of("All units, engage! Stay together and cover each other!", "Squad, positions! Watch your sectors, %direction% first!", "Contact ahead, %direction%! Spread out and keep him pinned!")),
			Map.entry("Status_Check", List.of("Lost him! All units, sound off - report your status.", "Contact broken, check in, who's hurt?", "Regroup on me and report your status.")),
			Map.entry("Pull_Back", List.of("%member% is down! Everyone fall back to cover and regroup on me!", "Man down, %member%! Pull back to cover, medic on him!", "We lost %member%! Don't bunch up, fall back and cover each other!")),
			Map.entry("Medic_Treating", List.of("Applying field dressing, %member%, stay with me.", "Hold still %member%, patching you up. You're at %health%%.", "%role% here, applying pressure, hold still, %member%!")),
			Map.entry("Resisting", List.of("Suspect is resisting! Take him down!")),
			Map.entry("Backup", List.of("Requesting backup at my location!", "Need more units here, now!")),
			Map.entry("Dispatch_Wanted", List.of("All units, be advised: %target% is wanted, level %level%.")),
			Map.entry("Escalate", List.of("%tier% units en route to %target%.")),
			Map.entry("Stand_Down", List.of("Suspect cleared. Returning to patrol.")),
			Map.entry("Regroup", List.of("Two down! Pull back to cover, backup is coming!", "We're losing men, fall back and wait for backup!")),
			Map.entry("Regroup_Push", List.of("Backup's here! All units, push together!", "Everyone move in, now!")),
			Map.entry("Shots_Fired", List.of("Shots fired! Converge on the sound!", "Gunfire, he's close! Move in!")));

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
