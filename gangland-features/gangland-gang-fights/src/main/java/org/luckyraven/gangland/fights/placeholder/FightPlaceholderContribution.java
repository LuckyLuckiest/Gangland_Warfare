package org.luckyraven.gangland.fights.placeholder;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.data.placeholder.extension.PlaceholderContribution;
import org.luckyraven.gangland.fights.database.FightStats;
import org.luckyraven.gangland.fights.stat.FightHistory;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;

import java.util.Locale;

/** {@code gang_fight-wins}, {@code gang_fight-losses}, {@code gang_fight-wlr} for the player's own gang. */
public final class FightPlaceholderContribution implements PlaceholderContribution {

	private final FightHistory  history;
	private final MemberManager members;

	public FightPlaceholderContribution(FightHistory history, MemberManager members) {
		this.history = history;
		this.members = members;
	}

	@Override
	@Nullable
	public String resolve(OfflinePlayer player, String parameter) {
		if (!parameter.startsWith("gang_fight-")) return null;

		Member member = members.getMember(player.getUniqueId());
		if (member == null || !member.hasGang()) return null;

		FightStats stats = history.stats(member.getGangId());
		return switch (parameter) {
			case "gang_fight-wins" -> String.valueOf(stats.wins());
			case "gang_fight-losses" -> String.valueOf(stats.losses());
			case "gang_fight-wlr" -> wlr(stats);
			default -> null;
		};
	}

	/** Wins per loss, two decimals; with no loss it is the win count. */
	public static String wlr(FightStats stats) {
		double ratio = stats.losses() == 0 ? stats.wins() : (double) stats.wins() / stats.losses();
		return String.format(Locale.ROOT, "%.2f", ratio);
	}
}
