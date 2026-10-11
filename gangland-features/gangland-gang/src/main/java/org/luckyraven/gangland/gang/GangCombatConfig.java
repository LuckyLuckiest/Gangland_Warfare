package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.combat.DamageLedger;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.combat.region.FriendlyFireRegionSource;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** Lane W5-F beans: friendly fire, the combat tag and the damage ledger. Bodies arrive with that lane. */
@Configuration
public final class GangCombatConfig {

	@Bean
	public FriendlyFirePolicy friendlyFirePolicy(MemberManager memberManager, GangManager gangManager) {
		return new FriendlyFirePolicy(memberManager, gangManager);
	}

	@Bean
	public CombatTag combatTag() {
		return new CombatTag();
	}

	@Bean
	public DamageLedger damageLedger() {
		return new DamageLedger();
	}

	@Bean
	public FriendlyFireRegionSource friendlyFireRegionSource() {
		return FriendlyFireRegionSource.NONE;
	}
}
