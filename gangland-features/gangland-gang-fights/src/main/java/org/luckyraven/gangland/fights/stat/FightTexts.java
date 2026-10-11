package org.luckyraven.gangland.fights.stat;

import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.util.Map;

/**
 * The Economy, Ranked and Stats texts of {@code fight/fight_messages.yml}, read on every call so {@code /glw reload}
 * applies. A missing key falls back to the default shown here.
 */
public class FightTexts {

	private static final Map<String, String> DEFAULTS = Map.ofEntries(
			Map.entry("Economy.Unavailable", "&cBets are unavailable right now."),
			Map.entry("Economy.Cannot_Afford_A", "&cThe challenging gang can no longer afford the bet."),
			Map.entry("Economy.Cannot_Afford_B", "&cThe challenged gang cannot afford the bet."),
			Map.entry("Economy.Bet_Too_High", "&cThat bet is above the server maximum."),
			Map.entry("Economy.Bet_Over_Percent", "&cA bet may not be more than a share of either gang bank."),
			Map.entry("Economy.Same_Address", "&cThe two gangs have fighters on the same connection, so this stake is refused."),
			Map.entry("Economy.Pair_Cap", "&cThese two gangs have already moved the daily limit between them."),
			Map.entry("Economy.Gang_Cap", "&cOne of the gangs has reached its daily bet winnings limit."),
			Map.entry("Economy.Ranked_Opponent_Too_Young", "&cThe other gang is too new for a ranked fight."),
			Map.entry("Economy.Ranked_Pair_Limit", "&cThese gangs have reached the daily ranked fight limit."),
			Map.entry("Economy.Ranked_Rematch_Cooldown", "&cThese gangs must wait before another ranked fight."),
			Map.entry("Stats.Header", "&6Fight record of &e%gang%"),
			Map.entry("Stats.Line", "&7Wins &a%wins% &7Losses &c%losses% &7Draws &e%draws% &7W/L &f%wlr%"),
			Map.entry("Stats.No_Gang", "&cYou are not in a gang. Name a gang: /glw gang fight stats <gang>."),
			Map.entry("Stats.Unknown_Gang", "&cNo gang has that name."));

	private final FileHandler file;

	public FightTexts(FileManager fileManager) {
		this.file = fileManager.getFile("fight_messages");
	}

	public String get(String key) {
		String text = file == null ? null : file.getFileConfiguration().getString(key);
		return GanglandChatUtil.color(text != null ? text : DEFAULTS.getOrDefault(key, key));
	}
}
