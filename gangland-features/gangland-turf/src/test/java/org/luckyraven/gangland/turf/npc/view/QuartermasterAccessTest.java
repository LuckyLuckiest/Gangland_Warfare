package org.luckyraven.gangland.turf.npc.view;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.permission.Permission;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link QuartermasterAccess#denyKey(Player, TurfPowerupFlowSession)}, the one re-check every Quartermaster
 * purchase runs before money moves (TF-19, TF-45). A null result means the viewer may buy now; any other result is
 * the {@code Quartermaster.*} line to show.
 */
@DisplayName("QuartermasterAccess - who may spend at the Quartermaster")
class QuartermasterAccessTest {

	private static final int TURF_ID  = 7;
	private static final int OWNER_ID = 1;
	private static final int ALLY_ID  = 2;

	private final TurfManager           turfs = mock(TurfManager.class);
	private final GangLookupContract    gangs = mock(GangLookupContract.class);
	private final UserLookupContract    users = mock(UserLookupContract.class);
	private final QuartermasterAccess   access = new QuartermasterAccess(turfs, gangs, users);

	private final Player viewer = mock(Player.class);
	private final UUID   viewerId = UUID.randomUUID();

	/** Head-rooted tree: recruit (root) -> officer -> boss (leaf, the gang leader). Real ranks, as GangPermissionsTest. */
	private static Rank[] rankTree() {
		Rank recruit = new Rank("Recruit", 1);
		Rank officer = new Rank("Officer", 2);
		Rank boss    = new Rank("Boss", 3);

		Tree<Rank> tree = new Tree<>();
		recruit.getNode().add(officer.getNode());
		officer.getNode().add(boss.getNode());
		tree.add(recruit.getNode());

		return new Rank[]{recruit, officer, boss};
	}

	/** Owner gang with the viewer as a member at {@code rank}; the viewer's user row points at the owner. */
	private Gang[] world(Rank rank) {
		Member member = new Member(viewerId);
		member.setGangId(OWNER_ID);
		member.setRank(rank);

		Gang owner = mock(Gang.class);
		when(owner.getId()).thenReturn(OWNER_ID);
		when(owner.getMembers()).thenReturn(List.of(member));

		Turf turf = mock(Turf.class);
		when(turf.getId()).thenReturn(TURF_ID);
		when(turf.getOwnerGangId()).thenReturn(OWNER_ID);
		when(turfs.get(TURF_ID)).thenReturn(turf);
		when(gangs.findById(OWNER_ID)).thenReturn(owner);

		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(OWNER_ID);
		when(users.findByPlayer(viewer)).thenReturn(user);
		when(viewer.getUniqueId()).thenReturn(viewerId);

		return new Gang[]{owner};
	}

	private TurfPowerupFlowSession session(Gang owner, Gang viewerGang) {
		Turf turf = turfs.get(TURF_ID);
		return new TurfPowerupFlowSession(turf, owner, viewerGang, "Quartermaster");
	}

	@Test
	@DisplayName("an owner-gang member holding the node may buy")
	void owner_withNode_mayBuy() {
		Rank officer = new Rank("Officer", 9, List.of(new Permission(1, GangPermissions.TURF_UPGRADE)));
		Gang[] gang  = world(officer);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertNull(access.denyKey(viewer, session(gang[0], gang[0])));
		}
	}

	@Test
	@DisplayName("an owner-gang member without the node is refused with No_Rank (TF-45)")
	void owner_withoutNode_noRank() {
		Gang[] gang = world(rankTree()[1]);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertEquals("Quartermaster.No_Rank", access.denyKey(viewer, session(gang[0], gang[0])));
		}
	}

	@Test
	@DisplayName("the top rank may buy even without the node (upgrading servers have no nodes yet)")
	void topRank_alwaysMay() {
		Gang[] gang = world(rankTree()[2]);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertNull(access.denyKey(viewer, session(gang[0], gang[0])));
		}
	}

	@Test
	@DisplayName("an allied gang's member sees the panel but may only look (Allies_Look)")
	void ally_looksOnly() {
		Gang[] gang = world(rankTree()[2]);
		Gang   ally = mock(Gang.class);
		when(ally.getId()).thenReturn(ALLY_ID);
		when(ally.isAlly(gang[0])).thenReturn(true);
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(ALLY_ID);
		when(users.findByPlayer(viewer)).thenReturn(user);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertEquals("Quartermaster.Allies_Look", access.denyKey(viewer, session(gang[0], ally)));
		}
	}

	@Test
	@DisplayName("a turf that no longer exists is refused with Turf_Gone")
	void turfGone() {
		Gang[] gang = world(rankTree()[2]);
		when(turfs.get(TURF_ID)).thenReturn(null);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertEquals("Quartermaster.Turf_Gone", access.denyKey(viewer, session(gang[0], gang[0])));
		}
	}

	@Test
	@DisplayName("a turf that changed hands since the panel opened is refused with Owner_Changed")
	void ownerChanged_sinceOpen() {
		Gang[] gang = world(rankTree()[2]);
		Turf   now  = mock(Turf.class);
		when(now.getOwnerGangId()).thenReturn(99);
		when(turfs.get(TURF_ID)).thenReturn(now);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertEquals("Quartermaster.Owner_Changed", access.denyKey(viewer, session(gang[0], gang[0])));
		}
	}

	@Test
	@DisplayName("a viewer who has left every gang is refused with Not_Member")
	void viewerLeftGang() {
		Gang[] gang = world(rankTree()[2]);
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(false);
		when(users.findByPlayer(viewer)).thenReturn(user);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			assertEquals("Quartermaster.Not_Member", access.denyKey(viewer, session(gang[0], gang[0])));
		}
	}
}
