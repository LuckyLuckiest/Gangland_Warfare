package org.luckyraven.gangland.gang.rank;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.permission.Permission;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.core.permission.PermissionRegistryContract;
import org.luckyraven.gangland.gang.database.repositories.rank.RankParentRepository;
import org.luckyraven.gangland.gang.database.repositories.rank.RankRepository;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

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
	private GangRules                       rules;
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
		rules              = mock(GangRules.class);
		when(rules.getRankPreset()).thenReturn(List.of("member", "officer", "owner"));

		when(registry.getRepository(Rank.class)).thenReturn(rankRepo);
		when(registry.getRepository(RankParent.class)).thenReturn(rankParentRepo);
		when(registry.getRepository(Permission.class)).thenReturn(permissionRepo);
		when(registry.getRepository(RankPermission.class)).thenReturn(rankPermissionRepo);

		when(rankRepo.loadAll()).thenReturn(List.of());
		when(rankParentRepo.loadAll()).thenReturn(Set.of());
		when(permissionRepo.loadAll()).thenReturn(List.of());
		when(rankPermissionRepo.loadAll()).thenReturn(Set.of());

		manager = new RankManager(registry, permissionRegistry, rules);
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
		@DisplayName("wires the head rank as root and the tail as its child, reading the child id from RankParent.childId() "
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
		@DisplayName("GR-13: when no persisted rank matches Gang.Rank.Head, the persisted root is kept and nothing is created")
		void initialize_headNameMissing_keepsPersistedRootAndWarns() {
			GangSettings.bind(new FakeGangSettingsContract().withRankHead("does-not-exist"));
			Rank onlyRank = new Rank("member", 2);
			when(rankRepo.loadAll()).thenReturn(List.of(onlyRank));

			manager.initialize();

			assertSame(onlyRank, manager.getRankTree().getRoot().getData(), "the persisted root stays the root");
			assertFalse(manager.getRanks().values().stream().anyMatch(rank -> rank.getName().equals("does-not-exist")),
					"no rank named after the renamed head is invented on a non-empty table");
			verify(rankRepo, never()).save(any());
		}

		@Test
		@DisplayName("GR-13: several persisted roots with a missing head keep the lowest id as the root")
		void initialize_headNameMissing_severalRoots_keepsLowestId() {
			GangSettings.bind(new FakeGangSettingsContract().withRankHead("does-not-exist"));
			Rank higher = new Rank("alpha", 5);
			Rank lower  = new Rank("beta", 2);
			when(rankRepo.loadAll()).thenReturn(List.of(higher, lower));

			manager.initialize();

			assertSame(lower, manager.getRankTree().getRoot().getData(), "the lowest-id root is kept");
		}

		@Test
		@DisplayName("a renamed Rank.Tail on a non-empty table creates nothing; the persisted ranks and links stay as they are")
		void initialize_tailNameMissing_warnsAndCreatesNothing() {
			GangSettings.bind(new FakeGangSettingsContract().withRankTail("does-not-exist"));
			Rank member  = new Rank("member", 1);
			Rank officer = new Rank("officer", 2);
			Rank owner   = new Rank("owner-old", 3);
			when(rankRepo.loadAll()).thenReturn(List.of(member, officer, owner));
			when(rankParentRepo.loadAll()).thenReturn(Set.of(new RankParent(1, 2), new RankParent(2, 3)));

			manager.initialize();

			assertEquals(3, manager.getRanks().size(), "no rank is created for the renamed tail");
			assertNull(manager.get("does-not-exist"));
			assertEquals(Set.of(new RankParent(1, 2), new RankParent(2, 3)), manager.getRanksParent());
			verify(rankRepo, never()).save(any());
			verify(rankParentRepo, never()).save(any());
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
	@DisplayName("initialize() seeds the preset ladder on an empty rank table only (D13 A)")
	class SeedInitialRanksTest {

		/**
		 * D13 = A: the preset ladder is written only when the rank table is empty (a fresh install). Concrete-class
		 * mocks so {@code instanceof RankRepository}/{@code RankParentRepository} - the guard RankManager branches on -
		 * passes.
		 */
		@Test
		@DisplayName("GR-13 / D13 A: an empty rank table seeds the preset ladder member -> officer -> owner, then loads it")
		void initialize_emptyTable_seedsPreset() throws Exception {
			RepositoryRegistry   seedRegistry = mock(RepositoryRegistry.class);
			RankRepository       realRankRepo = mock(RankRepository.class);
			RankParentRepository realParentRepo = mock(RankParentRepository.class);

			when(seedRegistry.getRepository(Rank.class)).thenReturn(realRankRepo);
			when(seedRegistry.getRepository(RankParent.class)).thenReturn(realParentRepo);
			when(seedRegistry.getRepository(Permission.class)).thenReturn(permissionRepo);
			when(seedRegistry.getRepository(RankPermission.class)).thenReturn(rankPermissionRepo);

			Rank member  = new Rank("member", 1);
			Rank officer = new Rank("officer", 2);
			Rank owner   = new Rank("owner", 3);
			when(realRankRepo.totalRanks()).thenReturn(0);
			when(realRankRepo.loadAll()).thenReturn(List.of(member, officer, owner));
			when(realParentRepo.loadAll()).thenReturn(Set.of(new RankParent(1, 2), new RankParent(2, 3)));
			when(rules.getPresetPermissions("member")).thenReturn(List.of("gangland.gang.deposit"));
			when(rules.getPresetPermissions("officer")).thenReturn(List.of("gangland.gang.deposit",
			                                                              "gangland.gang.invite"));

			RankManager seedingManager = new RankManager(seedRegistry, permissionRegistry, rules);
			seedingManager.initialize();

			verify(realRankRepo, times(3)).insertNow(any(Rank.class));
			verify(realParentRepo).insertInitialRelation(1, 2);
			verify(realParentRepo).insertInitialRelation(2, 3);
			assertEquals("member", seedingManager.getRankTree().getRoot().getData().getName());
			assertTrue(member.contains("gangland.gang.deposit"), "the member rank gets its preset node");
			assertTrue(officer.contains("gangland.gang.invite"), "the officer rank gets its preset node");
			assertFalse(owner.contains("gangland.gang.invite"), "the owner rank gets only the nodes listed for it");
		}

		@Test
		@DisplayName("GR-13 / D13 A: a non-empty rank table is never touched - no rank or link is inserted on boot")
		void initialize_nonEmptyTable_seedsNothing() throws Exception {
			RepositoryRegistry   seedRegistry = mock(RepositoryRegistry.class);
			RankRepository       realRankRepo = mock(RankRepository.class);
			RankParentRepository realParentRepo = mock(RankParentRepository.class);

			when(seedRegistry.getRepository(Rank.class)).thenReturn(realRankRepo);
			when(seedRegistry.getRepository(RankParent.class)).thenReturn(realParentRepo);
			when(seedRegistry.getRepository(Permission.class)).thenReturn(permissionRepo);
			when(seedRegistry.getRepository(RankPermission.class)).thenReturn(rankPermissionRepo);

			when(realRankRepo.totalRanks()).thenReturn(2);
			when(realRankRepo.loadAll()).thenReturn(List.of(new Rank("member", 1), new Rank("owner", 2)));
			when(realParentRepo.loadAll()).thenReturn(Set.of(new RankParent(1, 2)));

			new RankManager(seedRegistry, permissionRegistry, rules).initialize();

			verify(realRankRepo, never()).insertNow(any(Rank.class));
			verify(realParentRepo, never()).insertInitialRelation(anyInt(), anyInt());
			verify(rules, never()).getRankPreset();
		}

		@Test
		@DisplayName("mocked (non-concrete) repositories are left alone - no false seeding attempt against a fake type")
		void initialize_mockedRepositoryTypes_skipsSeeding() {
			// The outer setUp()'s rankRepo/rankParentRepo are plain IRepository mocks, not RankRepository/
			// RankParentRepository instances - proves the instanceof guard fails closed rather than throwing.
			assertDoesNotThrow(() -> manager.initialize());
		}

	}

	@Nested
	@DisplayName("addParent / removeParent / remove - the rank hierarchy edits")
	class HierarchyEditTest {

		private Rank owner;
		private Rank member;

		/** Default seeded hierarchy: member (head, root) -> owner (tail). */
		@BeforeEach
		void loadDefaultTree() {
			owner  = new Rank("owner", 1);
			member = new Rank("member", 2);
			when(rankRepo.loadAll()).thenReturn(List.of(owner, member));
			when(rankParentRepo.loadAll()).thenReturn(Set.of(new RankParent(2, 1)));
			when(rankPermissionRepo.loadAll()).thenReturn(Set.of(new RankPermission(1, 9), new RankPermission(2, 9)));
			when(permissionRepo.loadAll()).thenReturn(List.of(new Permission(9, "shared.node")));
			manager.initialize();
		}

		@Test
		@DisplayName("GR-04: a new parent link is kept in ranksParent and persisted, so it survives a restart")
		void addParent_persistsTheLink() {
			Rank officer = new Rank("officer", 3);
			manager.add(officer);

			assertTrue(manager.addParent(member, officer));

			assertTrue(member.getNode().getChildren().contains(officer.getNode()));
			assertTrue(manager.getRanksParent().contains(new RankParent(2, 3)));
			verify(rankParentRepo).save(new RankParent(2, 3));
		}

		@Test
		@DisplayName("a link that would close a loop is refused instead of overflowing the stack on every traversal")
		void addParent_cycle_isRefused() {
			assertFalse(manager.addParent(owner, member), "member already sits below owner");

			assertTrue(owner.getNode().getChildren().isEmpty());
			assertEquals(2, assertDoesNotThrow(() -> manager.getRankTree().getAllNodes()).size());
			verify(rankParentRepo, never()).save(any());
		}

		@Test
		@DisplayName("a rank that already sits above another rank cannot be placed above a second one")
		void addParent_alreadyPlaced_isRefused() {
			Rank officer = new Rank("officer", 3);
			manager.add(officer);

			assertFalse(manager.addParent(officer, owner), "owner already sits above member");

			assertSame(member.getNode(), owner.getNode().getParent());
			verify(rankParentRepo, never()).save(any());
		}

		@Test
		@DisplayName("GR-04: removing a link drops it from ranksParent and deletes that one row")
		void removeParent_deletesTheLink() {
			manager.removeParent(member, owner);

			assertTrue(member.getNode().getChildren().isEmpty());
			assertNull(owner.getNode().getParent(), "owner is free to be placed elsewhere");
			assertFalse(manager.getRanksParent().contains(new RankParent(2, 1)));
			verify(rankParentRepo).delete(new RankParent(2, 1));
		}

		@Test
		@DisplayName("remove(rank) prunes its parent and permission links so the next autosave cannot resurrect them")
		void remove_prunesLinksAndDetachesTheNode() {
			assertTrue(manager.remove(owner));

			assertTrue(manager.getRanksParent().isEmpty(), "the member -> owner link names the removed rank");
			assertEquals(Set.of(new RankPermission(2, 9)), manager.getRanksPermissions());
			assertTrue(member.getNode().getChildren().isEmpty(), "the removed rank is no longer promotable");
		}

	}

	@Nested
	@DisplayName("clear() reload semantics - Observation #12 (gangs-ranks-mail.md)")
	class ClearReloadTest {

		@Test
		@DisplayName("GR-12: clear() empties ranks, rankTree, permissions, ranksParent and ranksPermissions")
		void clear_wipesEverything() {
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
			assertTrue(manager.getRanksParent().isEmpty(), "clear() empties ranksParent (GR-12)");
			assertTrue(manager.getPermissions().isEmpty(), "clear() empties permissions (GR-12)");
			assertTrue(manager.getRanksPermissions().isEmpty(), "clear() empties ranksPermissions (GR-12)");
		}

		@Test
		@DisplayName("GR-12: a reload after clear() does not NPE on a stale ranksParent entry (the set is now empty)")
		void clear_thenReinitializeAfterARankWasDeletedUpstream_doesNotThrow() {
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
			assertDoesNotThrow(manager::initialize,
					"clear() dropped the stale ranksParent entry, so the reload has nothing to resolve");
		}

	}

}
