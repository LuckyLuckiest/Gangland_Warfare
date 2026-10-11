package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.combat.DamageLedger;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.combat.region.FriendlyFireRegionSource;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** Lane W5-F beans: friendly fire, the combat tag and the damage ledger. */
@Configuration
public final class GangCombatConfig {

	@Bean
	public FriendlyFirePolicy friendlyFirePolicy(MemberManager memberManager, GangManager gangManager,
	                                             GangRules rules, GangWorldPolicy worldPolicy,
	                                             FriendlyFireRegionSource regionSource, GangMessages messages) {
		return new FriendlyFirePolicy(memberManager, gangManager, rules, worldPolicy, regionSource, messages);
	}

	@Bean
	public CombatTag combatTag(GangRules rules, MemberManager memberManager) {
		return new CombatTag(System::currentTimeMillis, rules::getCombatTagSeconds, uuid -> {
			Member member = memberManager.getMember(uuid);
			return member == null ? -1 : member.getGangId();
		});
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
