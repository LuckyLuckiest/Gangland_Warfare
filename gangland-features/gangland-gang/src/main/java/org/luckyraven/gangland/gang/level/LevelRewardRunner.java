package org.luckyraven.gangland.gang.level;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.luckyraven.gangland.events.gangs.GangLevelReachedEvent;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.Member;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Runs a level's reward commands when a gang reaches it, then fires the api {@link GangLevelReachedEvent} once. The
 * commands of {@code Levels.Level_<n>.Commands} run from the console, once per member (online or not) who joined at
 * least {@code Levels.Reward_Min_Member_Age_Hours} ago, with these tokens: {@code %gang%} (the gang name, only when it
 * matches {@code [A-Za-z0-9_]+}, else the gang id), {@code %gang_id%}, {@code %player%}, {@code %level%}. A member whose
 * name is unknown (or not a plain name) is skipped with a log line, so nothing foreign is spliced into a console command.
 */
@CustomLog
public class LevelRewardRunner {

	private static final Pattern GANG_NAME   = Pattern.compile("[A-Za-z0-9_]+");
	private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_.]+");
	private static final long    HOUR_MS     = 3_600_000L;

	private final GangLevelTable         table;
	private final IntSupplier            minMemberAgeHours;
	private final Consumer<String>       console;
	private final Function<UUID, String> names;
	private final LongSupplier           clock;

	public LevelRewardRunner(GangLevelTable table, GangRules rules) {
		this(table, rules::getRewardMinMemberAgeHours,
		     command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command),
		     uuid -> Bukkit.getOfflinePlayer(uuid).getName(), System::currentTimeMillis);
	}

	public LevelRewardRunner(GangLevelTable table, IntSupplier minMemberAgeHours, Consumer<String> console,
	                         Function<UUID, String> names, LongSupplier clock) {
		this.table             = table;
		this.minMemberAgeHours = minMemberAgeHours;
		this.console           = console;
		this.names             = names;
		this.clock             = clock;
	}

	public void onLevelReached(Gang gang, int level) {
		if (gang == null) return;

		List<String> commands = table.row(level).commands();
		if (!commands.isEmpty()) {
			runFor(gang, level, commands);
		}

		Bukkit.getPluginManager().callEvent(new GangLevelReachedEvent(gang.getId(), level));
	}

	private void runFor(Gang gang, int level, List<String> commands) {
		long   oldEnough = clock.getAsLong() - minMemberAgeHours.getAsInt() * HOUR_MS;
		String gangToken = gang.getName() != null && GANG_NAME.matcher(gang.getName()).matches() ?
		                   gang.getName() : String.valueOf(gang.getId());

		for (Member member : gang.getMembers()) {
			if (member.getGangJoinDateLong() > oldEnough) continue;

			String name = names.apply(member.getUuid());
			if (name == null || !PLAYER_NAME.matcher(name).matches()) {
				log.info("Level {} reward skipped for member {}: no usable player name", level, member.getUuid());
				continue;
			}

			for (String command : commands) {
				String line = command.replace("%gang%", gangToken).replace("%gang_id%", String.valueOf(gang.getId()))
				                     .replace("%player%", name).replace("%level%", String.valueOf(level)).trim();
				if (line.startsWith("/")) line = line.substring(1);
				if (!line.isEmpty()) console.accept(line);
			}
		}
	}
}
