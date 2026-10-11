package org.luckyraven.gangland.fights;

import org.luckyraven.gangland.fights.fight.FightSettlement;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** Beans of the bets and settlement lane (W6-E). Only that lane edits this class. */
@Configuration
public class FightsEconomyConfig {

	@Bean
	public FightSettlement fightSettlement(FightRules rules) {
		return new FightSettlement();
	}

}
