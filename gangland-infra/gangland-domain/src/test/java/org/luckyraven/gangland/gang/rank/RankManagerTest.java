package org.luckyraven.gangland.gang.rank;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.contract.PermissionRegistryContract;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins {@link RankManager}'s permission bookkeeping and {@code initialize()}/{@code clear()} reload semantics,
 * per the gangs-ranks-mail.md Test Surface bullets: "RankManager.addPermission / removePermission /
 * permissionExists / findPermission with mocked IRepositorys ... including the 'last link removed => permission
 * row deleted' branch", "RankManager.initialize() tree construction: head-rank matching, the fallback-root branch
 * when the head name is missing (Obs. #13), and parent/child wiring from RankParent rows", and "RankManager.clear()
 * followed by a second initialize() to pin the reload semantics in Obs. #12".
 *
 * <p>Uses plain Mockito mocks for {@link RepositoryRegistry} and each {@link IRepository} — no Bukkit involved,
 * so this is a pure-logic suite despite RankManager depending on Keystone persistence types.
 */
@DisplayName("RankManager - permission bookkeeping and tree reload semantics")
class RankManagerTest {

	private RepositoryRegistry              registry;
	private IRepository<Rank>               rankRepo;
	private IRepository<RankParent>         rankParentRepo;
	private IRepository<Permission>         permissionRepo;
	private IRepository<RankPermission>     rankPermissionRepo;
	private PermissionRegistryContract      permissionRegistry;
	private RankManager                     manager;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract());

		registry           = mock(RepositoryRegistry.class);
		rankRepo           = mock(IRepository.class);
		rankParentRepo     = mock(IRepository.class);
		permissionRepo     = mock(IRepository.class);
		rankPermissionRepo = mock(IRepository.class);
		permissionRegistry = mock(PermissionRegistryContract.class);

		when(registry.getRepository(Rank.class)).thenReturn(rankRepo);
		when(registry.getRepository(RankParent.class)).thenReturn(rankParentRepo);
		when(registry.getRepository(Permission.class)).thenReturn(permissionRepo);
		when(registry.getRepository(RankPermission.class)).thenReturn(rankPermissionRepo);

		when(rankRepo.loadAll()).thenReturn(List.of());
		when(rankParentRepo.loadAll()).thenReturn(Set.of());
		when(permissionRepo.loadAll()).thenReturn(List.of());
		when(rankPermissionRepo.loadAll()).thenReturn(Set.of());

		manager = new RankManager(registry, permissionRegistry);
	}

	@AfterEach
	void resetSharedIdCounters() {
		// Rank.ID / Permission.ID are static counters shared process-wide; leave them at a known value so other
		// test classes in this module are not affected by whatever this class last set them to.
		Rank.setID(0);
		Permission.setID(0);
	}

	@Nested
	@DisplayName("permissionExists / findPermission")
	class LookupTest {

		@Test
		@DisplayName("permissionExists checks the local rank-permission map first, then falls back to the PermissionRegistryContract")
		void permissionExists_checksLocalMapThenFallsBackToRegistry() {
			manager.initialize();
			manager.addPermission(new Rank("Boss", 1), "gangland.command.gang.force_rank");

			when(permissionRegistry.contains("bukkit.only.node")).thenReturn(true);

			assertTrue(manager.permissionExists("GANGLAND.COMMAND.GANG.FORCE_RANK"), "case-insensitive local match");
			assertTrue(manager.permissionExists("bukkit.only.node"), "falls back to the registry contract");
			assertFalse(manager.permissionExists("totally.unknown.node"));
		}

		@Test
		void findPermission_isCaseInsensitive_andNullWhenMissing() {
			manager.initialize();
			manager.addPermission(new Rank("Boss", 1), "some.node");

			assertNotNull(manager.findPermission("SOME.NODE"));
			assertNull(manager.findPermission("other.node"));
		}

	}

	@Nested
	@DisplayName("addPermission / removePermission")
	class MutationTest {

		@Test
		@DisplayName("a fresh permission string mints a new Permission, links it to the rank, and persists both immediately")
		void addPermission_freshString_createsAndPersistsBoth() {
			manager.initialize();
			Rank rank = new Rank("Boss", 1);

			boolean added = manager.addPermission(rank, "gangland.command.gang.force_rank");

			assertTrue(added);
			assertTrue(rank.contains("gangland.command.gang.force_rank"));
			verify(permissionRepo).save(any(Permission.class));
			verify(rankPermissionRepo).save(any(RankPermission.class));
		}

		@Test
		@DisplayName("a permission string that already exists globally is reused (case-insensitively), not re-minted")
		void addPermission_existingString_reusesGlobalPermission() {
			Permission shared = new Permission(5, "shared.node");
			when(permissionRepo.loadAll()).thenReturn(List.of(shared));
			manager.initialize();

			Rank rank = new Rank("Recruit", 10);
			boolean added = manager.addPermission(rank, "SHARED.NODE");

			assertTrue(added);
			assertSame(shared, rank.getPermissions().get(0), "the existing Permission object is reused, not a new one");
			verify(permissionRepo, never()).save(any());
			verify(rankPermissionRepo).save(new RankPermission(10, 5));
		}

		@Test
		@DisplayName("adding a permission the rank already carries is a no-op that returns false and touches no repository")
		void addPermission_rankAlreadyHasIt_isNoOp() {
			manager.initialize();
			Rank rank = new Rank("Boss", 1, List.of(new Permission(1, "x.y")));

			boolean added = manager.addPermission(rank, "X.Y");

			assertFalse(added);
			verify(permissionRepo, never()).save(any());
			verify(permissionRepo, never()).delete(any());
			verify(rankPermissionRepo, never()).save(any());
			verify(rankPermissionRepo, never()).delete(any());
		}

		@Test
		@DisplayName("removing the last link to a permission deletes the permission row too (RankManager:212-215)")
		void removePermission_lastLinkRemoved_deletesPermissionRow() {
			manager.initialize();
			Rank rank = new Rank("Boss", 1);
			manager.addPermission(rank, "solo.node");

			manager.removePermission(rank, "solo.node");

			assertFalse(rank.contains("solo.node"));
			verify(rankPermissionRepo).delete(any(RankPermission.class));
			verify(permissionRepo).delete(any(Permission.class));
		}

		@Test
		@DisplayName("removing one rank's link leaves a permission that another rank still references untouched")
		void removePermission_otherRankStillReferences_permissionSurvives() {
			manager.initialize();
			Rank rankA = new Rank("Boss", 1);
			Rank rankB = new Rank("Deputy", 2);
			manager.addPermission(rankA, "shared.node");
			manager.addPermission(rankB, "shared.node");

			manager.removePermission(rankA, "shared.node");

			assertFalse(rankA.contains("shared.node"));
			assertTrue(rankB.contains("shared.node"), "rankB's link is untouched");
			verify(permissionRepo, never()).delete(any(Permission.class));
		}

		@Test
		void removePermission_unknownString_isNoOp() {
			manager.initialize();
			Rank rank = new Rank("Boss", 1);

			manager.removePermission(rank, "never.added");

			verify(rankPermissionRepo, never()).save(any());
			verify(rankPermissionRepo, never()).delete(any());
			verify(permissionRepo, never()).save(any());
			verify(permissionRepo, never()).delete(any());
		}

	}

	@Nested
	@DisplayName("initialize() tree construction")
	class TreeConstructionTest {

		@Test
		@DisplayName("wires the head rank as root and the tail as its child, reading the child id from RankParent.parentId() "
		             + "(Obs. #22, gangs-ranks-mail.md: the field named parentId actually stores the CHILD id)")
		void initialize_wiresHeadAsRootAndTailAsChild() {
			// Mirrors GanglandDatabase.insertInitialData: tail inserted first (id 1), head second (id 2),
			// then RankParent(headId=2, tailId=1).
			Rank tail = new Rank("owner", 1);
			Rank head = new Rank("member", 2);
			when(rankRepo.loadAll()).thenReturn(List.of(tail, head));
			when(rankParentRepo.loadAll()).thenReturn(Set.of(new RankParent(2, 1)));

			manager.initialize();

			Tree<Rank> tree = manager.getRankTree();
			assertEquals("member", tree.getRoot().getData().getName(), "the configured Gang.Rank.Head becomes root");
			assertEquals(1, tree.getRoot().getChildren().size());
			assertEquals("owner", tree.getRoot().getChildren().get(0).getData().getName());
		}

		@Test
		@DisplayName("Observation #13 (gangs-ranks-mail.md): when no persisted rank matches Gang.Rank.Head, a brand-new "
		             + "detached Rank becomes the tree root instead of failing loudly")
		void initialize_headNameMissing_installsDetachedFallbackRoot() {
			GangSettings.bind(new FakeGangSettingsContract().withRankHead("does-not-exist"));
			Rank onlyRank = new Rank("member", 2);
			when(rankRepo.loadAll()).thenReturn(List.of(onlyRank));

			manager.initialize();

			Rank root = manager.getRankTree().getRoot().getData();
			assertEquals("does-not-exist", root.getName());
			assertNotSame(onlyRank, root, "the fallback root is a fresh Rank, not the persisted one");
			assertFalse(manager.getRanks().containsValue(root),
					"the fallback root is never added to the ranks map, so it is unreachable from RankManager.get(...)");
		}

		@Test
		@DisplayName("a rank with no RankParent entries pointing at it builds a leaf with no children")
		void initialize_rankWithNoParentLinks_isLeaf() {
			Rank head = new Rank("member", 2);
			when(rankRepo.loadAll()).thenReturn(List.of(head));
			when(rankParentRepo.loadAll()).thenReturn(Set.of());

			manager.initialize();

			assertTrue(manager.getRankTree().getRoot().getChildren().isEmpty());
		}

	}

	@Nested
	@DisplayName("clear() reload semantics - Observation #12 (gangs-ranks-mail.md)")
	class ClearReloadTest {

		@Test
		@DisplayName("clear() resets ranks and rankTree but leaves permissions, ranksParent and ranksPermissions populated")
		void clear_wipesRanksAndTree_butNotThePermissionOrParentSets() {
			Rank tail = new Rank("owner", 1);
			Rank head = new Rank("member", 2);
			when(rankRepo.loadAll()).thenReturn(List.of(tail, head));
			when(rankParentRepo.loadAll()).thenReturn(Set.of(new RankParent(2, 1)));
			Permission perm = new Permission(9, "kept.node");
			when(permissionRepo.loadAll()).thenReturn(List.of(perm));
			when(rankPermissionRepo.loadAll()).thenReturn(Set.of(new RankPermission(2, 9)));

			manager.initialize();
			assertEquals(2, manager.getRanks().size());
			assertEquals(1, manager.getRanksParent().size());
			assertEquals(1, manager.getPermissions().size());
			assertEquals(1, manager.getRanksPermissions().size());

			manager.clear();

			assertTrue(manager.getRanks().isEmpty(), "clear() empties the ranks map");
			assertTrue(manager.getRankTree().isEmpty(), "clear() empties the rank tree");
			assertEquals(1, manager.getRanksParent().size(),
					"ranksParent survives clear() untouched - Observation #12");
			assertEquals(1, manager.getPermissions().size(),
					"permissions survives clear() untouched - Observation #12");
			assertEquals(1, manager.getRanksPermissions().size(),
					"ranksPermissions survives clear() untouched - Observation #12");
		}

		@Test
		@DisplayName("a reload after a rank was actually removed from the backing store crashes initialize(), because the "
		             + "stale ranksParent entry from before clear() now points at a rank id that never gets reloaded")
		void clear_thenReinitializeAfterARankWasDeletedUpstream_throwsNpeBuildingTheTree() {
			Rank tail = new Rank("owner", 1);
			Rank head = new Rank("member", 2);
			// First load: both ranks and the head-to-tail link exist.
			when(rankRepo.loadAll()).thenReturn(List.of(tail, head), List.of(head));
			when(rankParentRepo.loadAll()).thenReturn(Set.of(new RankParent(2, 1)), Set.of());

			manager.initialize();
			manager.clear();

			// Second load simulates "owner" having been deleted from the DB between reloads (e.g. via
			// /glw rank delete, which per Observation #9 does not prune RankManager.ranksParent either) - the
			// repository now reports zero rank_parent rows and only the surviving "member" rank, but the
			// in-memory ranksParent set from before clear() still references tail's id (1).
			assertThrows(NullPointerException.class, manager::initialize,
					"initialize() looks up ranks.get(rp.parentId()) unguarded while building the child-name list; "
					+ "a stale ranksParent entry pointing at a now-unloaded rank id NPEs instead of being skipped");
		}

	}

}
