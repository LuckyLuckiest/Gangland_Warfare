package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.gang.membership.GangDisbandService;
import org.luckyraven.gangland.gang.membership.GangLeadership;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.membership.MembershipQueries;
import org.luckyraven.gangland.gang.purge.GangPurgeTask;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** Lane W5-M beans: joining, leaving, kicking, disbanding, leadership and the purge. Bodies arrive with that lane. */
@Configuration
public final class GangMembershipConfig {

	@Bean
	public MembershipQueries membershipQueries(GangManager gangManager) {
		return new MembershipQueries(gangManager);
	}

	@Bean
	public GangMembershipService gangMembershipService() {
		return new GangMembershipService();
	}

	@Bean
	public GangDisbandService gangDisbandService() {
		return new GangDisbandService();
	}

	@Bean
	public GangLeadership gangLeadership() {
		return new GangLeadership();
	}

	@Bean
	public GangPurgeTask gangPurgeTask() {
		return new GangPurgeTask();
	}
}
