package org.luckyraven.gangland.copsncrooks.setup;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.persistence.FileManager;

import java.util.Map;

/**
 * The text and the knobs of the module's own {@code copsncrooks/setup.yml} (wand item, outline particle and interval,
 * messages). Every key has an in-code fallback equal to the shipped value, so a file that predates a key still reads.
 * Lines go out through the {@link Style} the key declares, so the core prefix is applied by kind.
 *
 * @since 0.16.0
 */
public class SetupMessages extends LocalizedModuleYaml {

	private static final String BASE_NAME = "setup";

	/** How a line is rendered: {@code COMMAND} gets the GLW prefix, {@code ERROR} the error prefix, {@code NONE} none. */
	public enum Style {
		COMMAND,
		ERROR,
		NONE
	}

	/** One line of {@code setup.yml}: its path, the text used when the file lacks it, and its style. */
	public enum Key {
		ARGUMENTS_MISSING("Arguments_Missing", "&4Missing Arguments&7: ", Style.COMMAND),
		LIST_HEADER("List_Header", "&7Setup %kind% &8(&f%count%&8)&7:", Style.COMMAND),
		NOT_PLAYER("Not_Player", "&cOnly a player can do that.", Style.ERROR),
		NO_PERMISSION("No_Permission", "&cYou may not use the setup tools.", Style.ERROR),
		WAND_NAME("Wand_Name", "&6Cops Setup Wand", Style.NONE),
		WAND_LORE("Wand_Lore", "&7Left-click = pos1, right-click = pos2.", Style.NONE),
		WAND_GIVEN("Wand_Given", "&aSetup wand given. Mode: &f%mode%&a. Left-click = pos1, right-click = pos2.",
		           Style.COMMAND),
		MODE_SET("Mode_Set", "&7Setup mode: &f%mode%", Style.COMMAND),
		MODE_UNKNOWN("Mode_Unknown", "&cUnknown mode. Use: %modes%", Style.ERROR),
		POS_SET("Pos_Set", "&7%corner% set to &f(%x%, %y%, %z%) &7in &f%world%", Style.COMMAND),
		INCOMPLETE("Incomplete", "&cSelect %needed% first (mode %mode%).", Style.ERROR),
		NAME_MISSING("Name_Missing", "&cGive it a name: /glw cop setup save <name...>", Style.ERROR),
		STATION_SAVED("Station_Saved", "&aStation &f%name% &a(#%id%) saved, %spawners% spawner(s) assigned.",
		              Style.COMMAND),
		STATION_DUPLICATE("Station_Duplicate", "&cA station named &f%name% &calready exists, nothing was saved.",
		                  Style.ERROR),
		REGION_SAVED("Region_Saved", "&aRegion &f%name% &a(#%id%, %tag%) saved.", Style.COMMAND),
		POINT_SAVED("Point_Saved", "&aPoint &f%name% &a(#%id%, %kind%) saved.", Style.COMMAND),
		LIST_EMPTY("List_Empty", "&7Nothing placed yet.", Style.COMMAND),
		REMOVED("Removed", "&aRemoved %kind% #%id%.", Style.COMMAND),
		NOT_FOUND("Not_Found", "&cNo %kind% with id %id%.", Style.ERROR),
		TELEPORTED("Teleported", "&7Teleported to %kind% #%id%.", Style.COMMAND),
		NO_LOCATION("No_Location", "&cThat place is in a world that is not loaded.", Style.ERROR),
		LINKED("Linked", "&aStation #%station% now uses jail #%jail%.", Style.COMMAND),
		UNLINKED("Unlinked", "&aStation #%station% no longer has a jail.", Style.COMMAND),
		UNKNOWN_JAIL("Unknown_Jail", "&cNo jail with id %id%.", Style.ERROR),
		UNKNOWN_KIND("Unknown_Kind", "&cKind must be station, region or point.", Style.ERROR),
		BAD_ID("Bad_Id", "&c%value% is not a number.", Style.ERROR),
		KIND_ALL("Kind_All", "all", Style.NONE),
		ROW_LABEL("Row_Label", " &b- &f#%id% &f%name% ", Style.NONE),
		ROW_WHERE("Row_Where", "&f%world% &7- &f%x%&7, &f%y%&7, &f%z%&8%tags%", Style.NONE),
		ROW_TP("Row_Tp", "&e(&btp&e)", Style.NONE);

		public final String path;
		public final String fallback;
		public final Style  style;

		Key(String path, String fallback, Style style) {
			this.path     = "Setup.Messages." + path;
			this.fallback = fallback;
			this.style    = style;
		}
	}

	public SetupMessages(FileManager fileManager) {
		super(fileManager, BASE_NAME);
	}

	/** The line for {@code key}, rendered in its style, with every {@code %name%} in {@code values} replaced. */
	public String format(Key key, Map<String, String> values) {
		String line = render(key);

		for (Map.Entry<String, String> entry : values.entrySet())
			line = line.replace("%" + entry.getKey() + "%", entry.getValue());

		return line;
	}

	/** The usage line for a missing or bad operand: {@code fullUsage} starts with {@code /glw}, so its command is coloured. */
	public String usage(String fullUsage) {
		return GanglandChatUtil.setArguments(command(Key.ARGUMENTS_MISSING.path, Key.ARGUMENTS_MISSING.fallback),
		                                     fullUsage);
	}

	private String render(Key key) {
		return switch (key.style) {
			case COMMAND -> command(key.path, key.fallback);
			case ERROR -> error(key.path, key.fallback);
			case NONE -> color(key.path, key.fallback);
		};
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
