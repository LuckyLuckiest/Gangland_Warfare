package org.luckyraven.gangland.civilians.message;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.gangland.npc.radio.RadioLines;
import org.luckyraven.keystone.persistence.FileManager;

import java.util.List;
import java.util.Map;

/**
 * Civilian-scoped user-facing strings, backed by the module's own {@code npc/civilian_messages.yml} (English,
 * always shipped) / {@code npc/civilian_messages_es.yml} (Spanish, optional — picked by
 * {@code Settings.getLanguagePicked()} via {@link LocalizedModuleYaml}). WS6 G3's worked example of the
 * module-owned {@code Messages} migration mechanism (plan step 11): replaces the 12 {@code Messages.CIVILIAN_*}
 * constants deleted from {@code gangland-api} by this same gate. Every accessor keeps the exact
 * {@code Type}/formatting the deleted enum entry used (all {@code Type.COMMAND} except
 * {@link #spawnerListHeader()}, which was {@code Type.PREFIX}) so player-visible output is unchanged.
 * <p>
 * Also implements {@link RadioLines} (phase H12) over the file's {@code Shouts} block: {@code Shouts.Format},
 * {@code Shouts.Compass} and {@code Shouts.Sides} live at that block's root; every other key is a shout signal's
 * line pool, under {@code Shouts.Lines.<key>}.
 */
public class CivilianMessages extends LocalizedModuleYaml implements RadioLines {

	private static final String BASE_NAME = "civilian_messages";

	private static final List<String> DEFAULT_FORMAT  = List.of("&c[%faction%] &f%unit%&7: &f%line%");
	private static final List<String> DEFAULT_COMPASS = List.of("north", "north-east", "east", "south-east", "south",
	                                                             "south-west", "west", "north-west");
	private static final List<String> DEFAULT_SIDES   = List.of("front", "left", "right", "back");

	private static final Map<String, List<String>> DEFAULT_LINES = Map.ofEntries(
			Map.entry("Contact", List.of("There he is! %direction%!", "Over here!")),
			Map.entry("Contact_Lost", List.of("Where'd he go?")),
			Map.entry("Engage", List.of("Light him up!")),
			Map.entry("Push", List.of("%member%, get in there!")),
			Map.entry("Flank_Left", List.of("%member%, go round his %side%!")),
			Map.entry("Flank_Right", List.of("%member%, go round his %side%!")),
			Map.entry("Reposition", List.of()),
			Map.entry("Search", List.of("Find him!")),
			Map.entry("Route", List.of()),
			Map.entry("No_Route", List.of("He's up there! Wait for him!")),
			Map.entry("Check_Fire", List.of("Watch it, you almost hit me!")),
			Map.entry("Reloading", List.of("Reloading!")),
			Map.entry("Man_Down", List.of("They got %member%!")),
			Map.entry("Leader_Down", List.of("Boss is down! %unit%, you lead!")),
			Map.entry("Ack", List.of()),
			Map.entry("Responding", List.of()),
			Map.entry("Rally", List.of("%faction%! They're hitting us, %count% on the way!")));

	public CivilianMessages(FileManager fileManager) {
		super(fileManager, BASE_NAME);
	}

	/**
	 * {@code Format}/{@code Compass}/{@code Sides} are read at {@code Shouts}' own root; every other key is a shout
	 * signal's line pool, under {@code Shouts.Lines.<key>}. An unrecognised key falls back to the empty list
	 * (silent), same as an explicit {@code key: []} in YAML.
	 */
	@Override
	public List<String> lines(String key) {
		return switch (key) {
			case "Format" -> list("Shouts.Format", DEFAULT_FORMAT);
			case "Compass" -> list("Shouts.Compass", DEFAULT_COMPASS);
			case "Sides" -> list("Shouts.Sides", DEFAULT_SIDES);
			default -> list("Shouts.Lines." + key, DEFAULT_LINES.getOrDefault(key, List.of()));
		};
	}

	public String listEmpty() {
		return command("List_Empty", "No civilians are currently active.");
	}

	public String groupsEmpty() {
		return command("Groups_Empty", "No civilian groups are currently active.");
	}

	public String spawnerListEmpty() {
		return command("Spawner_List_Empty", "No civilian spawners are currently set.");
	}

	public String spawned(String type) {
		return command("Spawned", "&aCivilian &e%type%&a spawned near you.").replace("%type%", type);
	}

	public String groupSpawned(String group) {
		return command("Group_Spawned", "&aCivilian group &e%group%&a spawned at your location.")
				.replace("%group%", group);
	}

	public String groupUnknown(String group) {
		return command("Group_Unknown", "&cUnknown group &e%group%&c.").replace("%group%", group);
	}

	public String typeUnknown(String type) {
		return command("Type_Unknown", "&cUnknown type &e%type%&c.").replace("%type%", type);
	}

	public String spawnerRemoved(String id) {
		return command("Spawner_Removed", "&aCivilian spawner &e%id%&a removed.").replace("%id%", id);
	}

	public String spawnerTeleported(String id) {
		return command("Spawner_Teleported", "Teleported to civilian spawner &e(&b%id%&e)&7.").replace("%id%", id);
	}

	public String spawnFailed(String type) {
		return command("Spawn_Failed",
		               "&cFailed to spawn civilian. Check that type &e%type%&c is valid and a location was found.")
				.replace("%type%", type);
	}

	public String spawnerTypeSet(String type) {
		return command("Spawner_Type_Set", "&aCivilian spawner &e%type%&a set at your location.")
				.replace("%type%", type);
	}

	public String spawnerGroupSet(String group) {
		return command("Spawner_Group_Set", "&aGroup spawner &e%group%&a set at your location.")
				.replace("%group%", group);
	}

	public String spawnerListHeader() {
		return prefix("Spawner_List_Header", "Civilian spawners:");
	}

}
