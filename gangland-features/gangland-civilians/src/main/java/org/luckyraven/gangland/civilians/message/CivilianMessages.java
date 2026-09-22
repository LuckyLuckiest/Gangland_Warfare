package org.luckyraven.gangland.civilians.message;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * Civilian-scoped user-facing strings, backed by the module's own {@code npc/civilian_messages.yml} (English,
 * always shipped) / {@code npc/civilian_messages_es.yml} (Spanish, optional — picked by
 * {@code Settings.getLanguagePicked()} via {@link LocalizedModuleYaml}). WS6 G3's worked example of the
 * module-owned {@code Messages} migration mechanism (plan step 11): replaces the 12 {@code Messages.CIVILIAN_*}
 * constants deleted from {@code gangland-api} by this same gate. Every accessor keeps the exact
 * {@code Type}/formatting the deleted enum entry used (all {@code Type.COMMAND} except
 * {@link #spawnerListHeader()}, which was {@code Type.PREFIX}) so player-visible output is unchanged.
 */
public class CivilianMessages extends LocalizedModuleYaml {

	private static final String BASE_NAME = "civilian_messages";

	public CivilianMessages(FileManager fileManager) {
		super(fileManager, BASE_NAME);
	}

	public String listEmpty() {
		return command("List_Empty", "No civilians are currently active.");
	}

	public String groupsEmpty() {
		return command("Groups_Empty", "No civilian groups are currently active.");
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
