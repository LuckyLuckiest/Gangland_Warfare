package org.luckyraven.gangland.copsncrooks.command;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.persistence.FileManager;

import java.util.Map;

/**
 * The reply text of the module's {@code /glw cop}, {@code /glw jail} and {@code /glw cuff} commands, from its own
 * {@code copsncrooks/commands.yml}. Every key has an in-code fallback equal to the shipped value. Lines go out through
 * the {@link Style} the key declares, so the core prefix is applied by kind.
 *
 * @since 0.16.1
 */
public class CommandMessages extends LocalizedModuleYaml {

	private static final String BASE_NAME = "commands";

	/** How a line is rendered: {@code COMMAND} gets the GLW prefix, {@code ERROR} the error prefix, {@code NONE} none. */
	public enum Style {
		COMMAND,
		ERROR,
		NONE
	}

	/** One line of {@code commands.yml}: its path (dotted = nested block), its fallback, and its style. */
	public enum Key {
		ARGUMENTS_MISSING("Arguments_Missing", "&4Missing Arguments&7: ", Style.COMMAND),
		BAD_ID("Bad_Id", "&c%value% is not a number.", Style.ERROR),
		PLAYER_NOT_FOUND("Player_Not_Found", "&c%player% &7is not found!", Style.ERROR),
		NOT_PLAYER("Not_Player", "&cYou need to be a player to use this!", Style.ERROR),
		COP_LIST_HEADER("Cop.List.Header", "&7Players being chased by cops &8(&f%count%&8)&7:", Style.COMMAND),
		COP_LIST_EMPTY("Cop.List.Empty", "&7No player is being chased by cops.", Style.COMMAND),
		COP_LIST_ROW("Cop.List.Row", " &b- &f%player%", Style.NONE),
		COP_TARGET_HEADER("Cop.Target.Header", "&7Player &f%player% &7is being chased by cops:", Style.COMMAND),
		COP_TARGET_ROW("Cop.Target.Row", " &b- &f%callsign%&7 (%uuid%)", Style.NONE),
		COP_TARGET_NOT_CHASED("Cop.Target.Not_Chased", "&cPlayer &f%target%&c is not being chased by cops.",
		                      Style.ERROR),
		SPAWNER_SET("Spawner.Set", "&aCop spawner set at your location.", Style.COMMAND),
		SPAWNER_REMOVED("Spawner.Removed", "&aCop spawner &f%id%&a removed.", Style.COMMAND),
		SPAWNER_TELEPORTED("Spawner.Teleported", "&7Teleported to cop spawner &f%id%&7.", Style.COMMAND),
		SPAWNER_LIST_HEADER("Spawner.List.Header", "&7Cop spawners &8(&f%count%&8)&7:", Style.COMMAND),
		SPAWNER_LIST_EMPTY("Spawner.List.Empty", "&7No cop spawners are placed yet.", Style.COMMAND),
		SPAWNER_LIST_ROW("Spawner.List.Row", " &b- &f#%id% &e(&btp&e)", Style.NONE),
		SPAWNER_LIST_HOVER("Spawner.List.Hover", "%world% - %x%, %y%, %z%", Style.NONE),
		SPAWNER_INFO_HEADER("Spawner.Info.Header", "&7Cop spawner &f#%id%&7: ", Style.COMMAND),
		SPAWNER_INFO_TP("Spawner.Info.Tp", "&e(&btp&e)", Style.NONE),
		SPAWNER_INFO_LINE("Spawner.Info.Line", " &bWorld: &f%world% &bX: &f%x% &bY: &f%y% &bZ: &f%z%", Style.NONE),
		SPAWNER_UNKNOWN("Spawner.Unknown", "&cNo cop spawner with id %id%.", Style.ERROR),
		JAIL_CREATED("Jail.Created", "&aJail &f%id%&a created at your location.", Style.COMMAND),
		JAIL_EXISTS_NEARBY("Jail.Exists_Nearby",
		                   "&cA jail already exists within %blocks% blocks of this location!", Style.ERROR),
		JAIL_REMOVED("Jail.Removed", "&aJail &f%id%&a removed.", Style.COMMAND),
		JAIL_RELEASED("Jail.Released", "&aReleased &f%target%&a from jail.", Style.COMMAND),
		JAIL_NOT_JAILED("Jail.Not_Jailed", "&cPlayer &f%target%&c is not jailed.", Style.ERROR),
		JAIL_EXIT_SET("Jail.Exit_Set", "&aExit location set for jail &f%id%&a.", Style.COMMAND),
		JAIL_EXIT_SET_GLOBAL("Jail.Exit_Set_Global", "&aGlobal jail exit set to your current location.",
		                     Style.COMMAND),
		JAIL_TELEPORTED("Jail.Teleported", "&7Teleported to jail &f%id%&7.", Style.COMMAND),
		JAIL_THROWN("Jail.Thrown", "&aThrown &f%target%&a to jail.", Style.COMMAND),
		JAIL_NO_EMPTY("Jail.No_Empty", "&cNo empty jail found!", Style.ERROR),
		JAIL_ALREADY_JAILED("Jail.Already_Jailed", "&cPlayer is already jailed!", Style.ERROR),
		JAIL_LIST_HEADER("Jail.List.Header", "&7Jails &8(&f%count%&8)&7:", Style.COMMAND),
		JAIL_LIST_EMPTY("Jail.List.Empty", "&7No jails are placed yet.", Style.COMMAND),
		JAIL_LIST_ROW("Jail.List.Row", " &b- &f#%id% &e(&btp&e)", Style.NONE),
		JAIL_LIST_HOVER("Jail.List.Hover", "%world% - %x%, %y%, %z%", Style.NONE),
		JAIL_INFO_HEADER("Jail.Info.Header", "&7Jail &f#%id%&7: ", Style.COMMAND),
		JAIL_INFO_TP("Jail.Info.Tp", "&e(&btp&e)", Style.NONE),
		JAIL_INFO_LINE("Jail.Info.Line",
		               " &bWorld: &f%world% &bX: &f%x% &bY: &f%y% &bZ: &f%z% &bCapacity: &f%capacity%", Style.NONE),
		JAIL_UNKNOWN("Jail.Unknown", "&cNo jail with id %id%.", Style.ERROR),
		CUFF_HANDCUFFED("Cuff.Handcuffed", "&aHandcuffed &f%target%&a.", Style.COMMAND),
		CUFF_RELEASED("Cuff.Released", "&aReleased &f%target%&a from handcuffs.", Style.COMMAND),
		CUFF_ALREADY_CUFFED("Cuff.Already_Cuffed", "&cThis player is already cuffed!", Style.ERROR),
		CUFF_NOT_CUFFED("Cuff.Not_Cuffed", "&cThis player is not handcuffed!", Style.ERROR);

		public final String path;
		public final String fallback;
		public final Style  style;

		Key(String path, String fallback, Style style) {
			this.path     = path;
			this.fallback = fallback;
			this.style    = style;
		}
	}

	public CommandMessages(FileManager fileManager) {
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
}
