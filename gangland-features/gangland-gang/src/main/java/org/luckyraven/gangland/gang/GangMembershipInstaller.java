package org.luckyraven.gangland.gang;

import org.bukkit.Location;
import org.luckyraven.gangland.data.gang.GangChatChannel;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;
import org.luckyraven.gangland.gang.chat.ChatChannels;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.MembershipQueries;
import org.luckyraven.keystone.bean.PostConstruct;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Wires the module's live {@link GangManager}/{@link MemberManager} into the core-owned {@link GangMembership}
 * holder (WS5 G1 step 9c, R9). Runs in {@link #install()} rather than a constructor because
 * {@code GangMembership} is a core bean the module only ever writes to once, after its own
 * {@code GangManager}/{@code MemberManager} beans exist. The view delegates the 2.6 members to {@link MembershipQueries}, {@link FriendlyFirePolicy} and {@link ChatChannels}; nobody edits the anonymous view after this.
 *
 * <p><b>Not a {@code @Configuration} class (T-53, W55).</b> {@code BeanFactory.instantiate()} instantiates
 * every registered {@code @Configuration} class via its own constructor in one up-front pass, entirely
 * <em>before</em> any {@code @Bean} method runs in any phase — so a bare {@code @Configuration} class can only
 * ever resolve constructor parameters that are themselves other configuration-class instances, never a
 * {@code @Bean}-produced value like {@link GangMembership}/{@link GangManager}/{@link MemberManager}, no matter
 * what order the configuration classes are registered in (house rule
 * {@code feedback_bean_ordering_via_params.md}: only {@code @Bean} method parameters are real ordering edges).
 * This class is now produced by a {@code @Bean} factory method on {@link GangConfig} instead — its 3 parameters
 * are the ordering edges that guarantee {@code IdentityContractConfig.gangMembership()} (core) and
 * {@code GangConfig}'s own {@code gangManager}/{@code memberManager} beans run first. {@code @PostConstruct}
 * still fires correctly on a factory-produced bean (confirmed against {@code BeanFactory.runPostConstruct},
 * which walks every bean in {@code allRegisteredBeans}, not just direct configuration-class instances), so
 * {@link #install()} needed no change.
 */
public final class GangMembershipInstaller {

	private final GangMembership gangMembership;
	private final MemberManager  memberManager;
	private final GangManager    gangManager;
	private final MembershipQueries  queries;
	private final FriendlyFirePolicy friendlyFire;
	private final ChatChannels       chatChannels;

	public GangMembershipInstaller(GangMembership gangMembership, MemberManager memberManager,
	                               GangManager gangManager, MembershipQueries queries,
	                               FriendlyFirePolicy friendlyFire, ChatChannels chatChannels) {
		this.gangMembership = gangMembership;
		this.memberManager  = memberManager;
		this.gangManager    = gangManager;
		this.queries        = queries;
		this.friendlyFire   = friendlyFire;
		this.chatChannels   = chatChannels;
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

			@Override
			public boolean pvpBlocked(UUID attacker, UUID victim, Location at) {
				return friendlyFire.pvpBlocked(attacker, victim, at);
			}

			@Override
			public Set<Integer> gangIds() {
				return queries.gangIds();
			}

			@Override
			public int gangIdByName(String name) {
				return queries.gangIdByName(name);
			}

			@Override
			public List<UUID> membersOf(int gangId) {
				return queries.membersOf(gangId);
			}

			@Override
			public Optional<UUID> ownerOf(int gangId) {
				return queries.ownerOf(gangId);
			}

			@Override
			public GangChatChannel chatChannelOf(UUID player) {
				return chatChannels.chatChannelOf(player);
			}
		});
	}
}
