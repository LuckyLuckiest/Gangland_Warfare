package org.luckyraven.gangland.copsncrooks.setup;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.keystone.persistence.FileManager;

import java.util.Map;

/**
 * The text and the knobs of the module's own {@code copsncrooks/setup.yml} (wand item, outline particle and interval,
 * messages). Every key has an in-code fallback equal to the shipped value, so a file that predates a key still reads.
 *
 * @since 0.16.0
 */
public class SetupMessages extends LocalizedModuleYaml {

	private static final String BASE_NAME = "setup";

	/** One line of {@code setup.yml}: its path and the text used when the file lacks it. */
	public enum Key {
		USAGE("Usage", "&cUsage: /glw cop setup <wand|mode|save|list|remove|tp|link>"),
		NOT_PLAYER("Not_Player", "&cOnly a player can do that."),
		NO_PERMISSION("No_Permission", "&cYou may not use the setup tools."),
		WAND_NAME("Wand_Name", "&6Cops Setup Wand"),
		WAND_LORE("Wand_Lore", "&7Left-click = pos1, right-click = pos2."),
		WAND_GIVEN("Wand_Given", "&aSetup wand given. Mode: &e%mode%&a. Left-click = pos1, right-click = pos2."),
		MODE_SET("Mode_Set", "&7Setup mode: &e%mode%"),
		MODE_UNKNOWN("Mode_Unknown", "&cUnknown mode. Use: %modes%"),
		POS_SET("Pos_Set", "&7%corner% set to &f(%x%, %y%, %z%) &7in &f%world%"),
		INCOMPLETE("Incomplete", "&cSelect %needed% first (mode %mode%)."),
		NAME_MISSING("Name_Missing", "&cGive it a name: /glw cop setup save <name>"),
		STATION_SAVED("Station_Saved", "&aStation &f%name% &a(#%id%) saved, %spawners% spawner(s) assigned."),
		STATION_DUPLICATE("Station_Duplicate", "&cA station named &f%name% &calready exists, nothing was saved."),
		REGION_SAVED("Region_Saved", "&aRegion &f%name% &a(#%id%, %tag%) saved."),
		POINT_SAVED("Point_Saved", "&aPoint &f%name% &a(#%id%, %kind%) saved."),
		LIST_EMPTY("List_Empty", "&7Nothing placed yet."),
		REMOVED("Removed", "&aRemoved %kind% #%id%."),
		NOT_FOUND("Not_Found", "&cNo %kind% with id %id%."),
		TELEPORTED("Teleported", "&7Teleported to %kind% #%id%."),
		NO_LOCATION("No_Location", "&cThat place is in a world that is not loaded."),
		LINKED("Linked", "&aStation #%station% now uses jail #%jail%."),
		UNLINKED("Unlinked", "&aStation #%station% no longer has a jail."),
		UNKNOWN_JAIL("Unknown_Jail", "&cNo jail with id %id%."),
		UNKNOWN_KIND("Unknown_Kind", "&cKind must be station, region or point."),
		BAD_ID("Bad_Id", "&c%value% is not a number.");

		public final String path;
		public final String fallback;

		Key(String path, String fallback) {
			this.path     = "Setup.Messages." + path;
			this.fallback = fallback;
		}
	}

	public SetupMessages(FileManager fileManager) {
		super(fileManager, BASE_NAME);
	}

	/** The line for {@code key}, coloured, with every {@code %name%} in {@code values} replaced. */
	public String format(Key key, Map<String, String> values) {
		String line = color(key.path, key.fallback);

		for (Map.Entry<String, String> entry : values.entrySet())
			line = line.replace("%" + entry.getKey() + "%", entry.getValue());

		return line;
	}

	/** {@code Setup.Wand.Item}: a material name, XSeries-resolved by the caller. */
	public String wandItem() {
		return raw("Setup.Wand.Item", "BLAZE_ROD");
	}

	/** {@code Setup.Outline.Particle}: a particle name. */
	public String particle() {
		return raw("Setup.Outline.Particle", "DUST");
	}

	/** {@code Setup.Outline.Interval_Ticks}, at least 1. */
	public int intervalTicks() {
		return Math.max(1, getFileHandler().getFileConfiguration().getInt("Setup.Outline.Interval_Ticks", 10));
	}
}
