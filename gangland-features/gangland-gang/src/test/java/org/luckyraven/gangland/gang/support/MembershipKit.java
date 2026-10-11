package org.luckyraven.gangland.gang.support;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangDisbandService;
import org.luckyraven.gangland.gang.membership.GangLeadership;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Builds a real {@link GangMembershipService} around the mocks a command test already has. */
public final class MembershipKit {

	/** The member cap the fake {@link GangLimits} answers; {@code 0} is unlimited. */
	public int cap;
	/** How long the fake {@link CombatTag} keeps every player tagged; zero is not tagged. */
	public Duration taggedFor = Duration.ZERO;

	public final GangLimits    limits  = new GangLimits() {
		@Override
		public int maxMembers(int gangId) {
			return cap;
		}
	};
	public final CombatTag     tag     = new CombatTag() {
		@Override
		public boolean isTagged(UUID player) {
			return !taggedFor.isZero();
		}

		@Override
		public Duration remaining(UUID player) {
			return taggedFor;
		}
	};
	public final GangRules     rules   = mock(GangRules.class);
	public final List<String>  console = new ArrayList<>();
	public final GangMembershipService service;

	public MembershipKit(GangManager gangManager, MemberManager memberManager, RankManager rankManager,
	                     UserManager<Player> userManager, UserManager<OfflinePlayer> offlineUserManager,
	                     RepositoryRegistry repositoryRegistry, GangMessages messages) {
		when(rules.getJoinCommands()).thenReturn(List.of());
		when(rules.getLeaveCommands()).thenReturn(List.of());
		when(rules.getKickCommands()).thenReturn(List.of());

		GangDisbandService disband = new GangDisbandService(userManager, offlineUserManager, memberManager,
		                                                    gangManager, repositoryRegistry, messages);
		GangLeadership leadership = new GangLeadership(memberManager, rankManager, userManager);
		this.service = new GangMembershipService(gangManager, memberManager, rankManager, userManager,
		                                         offlineUserManager, limits, tag, rules, messages, disband,
		                                         leadership, console::add);
	}
}
