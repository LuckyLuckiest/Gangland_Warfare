package org.luckyraven.gangland.fights;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.fights.fight.BukkitFightClock;
import org.luckyraven.gangland.fights.fight.BukkitFightWorld;
import org.luckyraven.gangland.fights.fight.FightClock;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.fights.fight.FightMessages;
import org.luckyraven.gangland.fights.fight.FightSettings;
import org.luckyraven.gangland.fights.fight.FightSettlement;
import org.luckyraven.gangland.fights.fight.FightWorld;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.persistence.FileManager;

/** Beans of the fight-flow lane (W6-S). Only that lane edits this class. */
@Configuration
public class FightsFlowConfig {

	@Bean
	public FightClock fightClock(JavaPlugin plugin) {
		return new BukkitFightClock(plugin);
	}

	@Bean
	public FightMessages fightMessages(FightsModuleFiles files, FileManager fileManager) {
		FightMessages messages = new FightMessages(fileManager);
		fileManager.registerInitializer(messages);
		return messages;
	}

	@Bean
	public FightWorld fightWorld(GangManager gangs, MemberManager members, @Qualifier("online") UserManager<Player> users,
	                             FightMessages messages) {
		return new BukkitFightWorld(gangs, members, users, messages);
	}

	@Bean
	public FightManager fightManager(FightClock clock, FightSettlement settlement, CombatTag combatTag,
	                                 ArenaRegistry arenas, FightWorld world, FightRules rules) {
		// read the rules on every use so /glw reload is picked up
		return new FightManager(clock, settlement, combatTag, arenas, world, () -> settings(rules));
	}

	private static FightSettings settings(FightRules r) {
		return new FightSettings(r.getChallengeExpirySeconds(), r.getJoinWindowSeconds(), r.getCountdownSeconds(),
		                         r.getMaxDurationMinutes(), r.getResultSeconds(), r.getMinTeamSize(),
		                         r.getMaxTeamSize(), r.isRequireEqualTeams(), r.getDeclineCooldownSeconds(),
		                         r.getTimeoutResult(), r.getMinMemberAgeHours(), r.getMaxBet(),
		                         r.getMaxBetPercentOfBank(), r.getRankedMinBet(), r.getRankedMinMemberAgeHours(),
		                         r.getRankedMinOpponentGangAgeDays());
	}

}
