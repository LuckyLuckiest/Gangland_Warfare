package org.luckyraven.gangland.fights;

import org.bukkit.Bukkit;
import org.luckyraven.gangland.events.gangs.GangFightEndedEvent;
import org.luckyraven.gangland.fights.command.fight.FightStatsCommand;
import org.luckyraven.gangland.fights.database.FightPlayerStats;
import org.luckyraven.gangland.fights.database.FightRecord;
import org.luckyraven.gangland.fights.database.FightStats;
import org.luckyraven.gangland.fights.fight.FightBetCaps;
import org.luckyraven.gangland.fights.fight.FightSettlement;
import org.luckyraven.gangland.fights.placeholder.FightPlaceholderContribution;
import org.luckyraven.gangland.fights.stat.FightHistory;
import org.luckyraven.gangland.fights.stat.FightTexts;
import org.luckyraven.gangland.fights.stat.FightWinsStatContribution;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/** Beans of the bets, settlement and records lane (W6-E). Only that lane edits this class. */
@Configuration
public class FightsEconomyConfig {

	@Bean
	public FightTexts fightTexts(FightsModuleFiles files, FileManager fileManager) {
		return new FightTexts(fileManager);
	}

	@Bean
	public FightHistory fightHistory(RepositoryRegistry registry) {
		return new FightHistory(registry.getRepository(FightStats.class),
		                        registry.getRepository(FightPlayerStats.class),
		                        registry.getRepository(FightRecord.class));
	}

	@Bean
	public FightBetCaps fightBetCaps(FightRules rules) {
		return new FightBetCaps(rules);
	}

	@Bean
	public FightSettlement fightSettlement(FightRules rules, FightHistory history, GangManager gangs) {
		return new FightSettlement(gangId -> {
			Gang gang = gangs.findById(gangId);
			return gang == null ? null : gang.getEconomy();
		}, GangSettings::getGangMaxBalance, rules::getHouseCutPercent, result -> {
			history.record(result.fight(), result.winnerGangId(), result.endedAt());
			Bukkit.getPluginManager()
			      .callEvent(new GangFightEndedEvent(result.winnerGangId(), result.loserGangId(), result.fight().bet(),
			                                         result.fight().ranked()));
		}, System::currentTimeMillis);
	}

	@Bean
	public FightWinsStatContribution fightWinsStat(FightHistory history, FightRules rules) {
		return new FightWinsStatContribution(history, rules);
	}

	@Bean
	public FightPlaceholderContribution fightPlaceholders(FightHistory history, MemberManager members) {
		return new FightPlaceholderContribution(history, members);
	}

	@Bean
	public FightStatsCommand.Services fightStatsServices(FightHistory history, FightTexts texts, GangManager gangs,
	                                                     MemberManager members) {
		FightStatsCommand.Services services = new FightStatsCommand.Services(history, texts, gangs, members);
		FightStatsCommand.bind(services);
		return services;
	}

}
