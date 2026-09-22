package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;

import java.util.Optional;
import java.util.UUID;

/**
 * Wires the module's live {@link GangManager}/{@link MemberManager} into the core-owned {@link GangMembership}
 * holder (WS5 G1 step 9c, R9). Runs in {@link #install()} rather than a constructor because
 * {@code GangMembership} is a core bean the module only ever writes to once, after its own
 * {@code GangManager}/{@code MemberManager} beans exist.
 */
@Configuration
public final class GangMembershipInstaller {

	private final GangMembership gangMembership;
	private final MemberManager  memberManager;
	private final GangManager    gangManager;

	public GangMembershipInstaller(GangMembership gangMembership, MemberManager memberManager,
	                               GangManager gangManager) {
		this.gangMembership = gangMembership;
		this.memberManager  = memberManager;
		this.gangManager    = gangManager;
	}

	@PostConstruct
	public void install() {
		gangMembership.install(new GangMembershipView() {
			@Override
			public int gangIdOf(UUID uuid) {
				if (uuid == null) return -1;
				Member member = memberManager.getMember(uuid);
				return member == null ? -1 : member.getGangId();
			}

			@Override
			public boolean gangsAllied(int gangIdA, int gangIdB) {
				Gang a = gangManager.getGang(gangIdA);
				Gang b = gangManager.getGang(gangIdB);
				return a != null && b != null && a.isAlly(b);
			}

			@Override
			public Optional<String> nameOf(int gangId) {
				Gang gang = gangManager.getGang(gangId);
				return gang == null ? Optional.empty() : Optional.of(gang.getDisplayNameString());
			}
		});
	}
}
