package org.luckyraven.gangland.gang.member;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.MemberRepositoryContract;
import org.luckyraven.gangland.gang.contract.RankLookupContract;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WS5 G0 (B4, plan §3 row 6b) proof: {@code DataConfig.userManager()}/{@code offlineUserManager()} used to declare
 * a {@code MemberManager orderingDep} parameter purely to force {@code BeanGraph} to construct {@code UserManager}
 * after {@code MemberManager} — the constructor never read the parameter. That parameter is deleted in this gate
 * (it also could not survive the module boundary once {@code MemberManager} becomes a module type).
 *
 * <p>This test pins the mechanism the deletion relies on: {@link MemberManager#getMember(UUID)} only returns
 * populated data once {@link MemberManager#onInitialize(boolean)} (a {@code BeanLifecycle} phase) has run — and
 * that population has <b>zero</b> dependency on any {@code UserManager} instance existing, being constructed, or
 * being initialized alongside it. The two real consumers of member data at boot time,
 * {@code UserDataLoader.loadUserData} and {@code PlayerBootstrapService.loadOnlinePlayers} (both in gangland-impl),
 * take {@link MemberManager} as a <b>direct</b>, consumed constructor parameter — never routed through
 * {@code UserManager} — and {@code PlayerBootstrapService} only runs as a {@code BeanPostInitialize} bean, which
 * Keystone guarantees runs after every {@code BeanLifecycle.onInitialize(...)} call (confirmed javadoc,
 * {@code PlayerBootstrapService}) has completed, {@code UserManager}'s included. So the gangId-attach invariant
 * the deleted parameter protected was never actually about construction order between the two beans — it was
 * always this phase boundary, which this test exercises directly.
 */
@DisplayName("MemberManager - cache population order (WS5 G0 step 1c proof)")
class MemberCachePopulationOrderTest {

	@Test
	@DisplayName("getMember returns nothing before onInitialize has populated the cache from the repository")
	void getMember_beforeOnInitialize_returnsNull() {
		UUID uuid = UUID.randomUUID();
		Member member = new Member(uuid);

		MemberRepositoryContract repository = mock(MemberRepositoryContract.class);
		when(repository.loadAll()).thenReturn(List.of(member));

		MemberManager manager = new MemberManager(mock(JavaPlugin.class), mock(DatabaseHandler.class), repository,
				mock(GangLookupContract.class), mock(RankLookupContract.class));

		assertNull(manager.getMember(uuid), "the cache must be empty until onInitialize runs - nothing has "
				+ "constructed or initialized a UserManager here, and none is needed for this to hold");
	}

	@Test
	@DisplayName("onInitialize(BeanLifecycle phase) populates the cache from the repository with no UserManager "
			+ "involved at all - proves member-cache readiness is independent of UserManager construction order")
	void onInitialize_populatesCache_withNoUserManagerInvolved() {
		UUID uuid = UUID.randomUUID();
		Member member = new Member(uuid);

		MemberRepositoryContract repository = mock(MemberRepositoryContract.class);
		when(repository.loadAll()).thenReturn(List.of(member));

		MemberManager manager = new MemberManager(mock(JavaPlugin.class), mock(DatabaseHandler.class), repository,
				mock(GangLookupContract.class), mock(RankLookupContract.class));

		// BeanLifecycle.onInitialize(...) is the phase every module-and-core bean runs through before
		// BeanPostInitialize beans (PlayerBootstrapService) ever read from them - see the class javadoc.
		manager.onInitialize(true);

		assertNotNull(manager.getMember(uuid), "after the BeanLifecycle phase, the member set at boot is already "
				+ "readable by any BeanPostInitialize consumer (UserDataLoader / PlayerBootstrapService), which "
				+ "both take MemberManager as a direct constructor parameter rather than reaching it through "
				+ "UserManager - so UserManager's own construction order relative to MemberManager was never "
				+ "load-bearing for this invariant");
	}
}
