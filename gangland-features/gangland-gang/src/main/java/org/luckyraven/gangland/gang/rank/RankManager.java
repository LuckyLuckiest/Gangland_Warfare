package org.luckyraven.gangland.gang.rank;

import lombok.CustomLog;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.core.permission.Permission;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.database.repositories.rank.RankParentRepository;
import org.luckyraven.gangland.gang.database.repositories.rank.RankRepository;
import org.luckyraven.gangland.core.permission.PermissionRegistryContract;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@CustomLog
public class RankManager implements BeanLifecycle {

	private final         RepositoryRegistry          repositoryRegistry;
	private final         PermissionRegistryContract  permissionRegistry;
	private final         GangRules                   rules;
	private final         Map<Integer, Rank>          ranks;
	private final         Set<RankParent>             ranksParent;
	private final         Map<Integer, Permission>    permissions;
	private final         Set<RankPermission>         ranksPermissions;
	private final @Getter Tree<Rank>                  rankTree;
	private               IRepository<Permission>     permissionRepo;
	private               IRepository<RankPermission> rankPermissionRepo;
	private               IRepository<RankParent>     rankParentRepo;

	public RankManager(RepositoryRegistry repositoryRegistry, PermissionRegistryContract permissionRegistry,
	                   GangRules rules) {
		this.repositoryRegistry = repositoryRegistry;
		this.permissionRegistry = permissionRegistry;
		this.rules              = rules;
		this.ranks              = new HashMap<>();
		this.ranksParent        = new HashSet<>();
		this.rankTree           = new Tree<>();
		this.permissions        = new HashMap<>();
		this.ranksPermissions   = new HashSet<>();
	}

	public void initialize() {
		IRepository<Rank>       rankRepo       = repositoryRegistry.getRepository(Rank.class);
		this.rankParentRepo     = repositoryRegistry.getRepository(RankParent.class);
		this.permissionRepo     = repositoryRegistry.getRepository(Permission.class);
		this.rankPermissionRepo = repositoryRegistry.getRepository(RankPermission.class);

		boolean seeded = seedIfEmpty(rankRepo, rankParentRepo);

		Collection<Rank>           loadedRanks           = rankRepo.loadAll();
		Collection<Permission>     loadedPermissions     = permissionRepo.loadAll();
		Collection<RankParent>     loadedRankParents     = rankParentRepo.loadAll();
		Collection<RankPermission> loadedRankPermissions = rankPermissionRepo.loadAll();

		ranksParent.addAll(loadedRankParents);
		ranksPermissions.addAll(loadedRankPermissions);

		// Set up the permissions map and ID counter
		int lastPermissionId = -1;

		for (Permission perm : loadedPermissions) {
			permissions.put(perm.getUsedId(), perm);
			if (perm.getUsedId() > lastPermissionId) lastPermissionId = perm.getUsedId();
		}

		Permission.setID(lastPermissionId + 1);

		// Set up ranks with their linked permissions
		Map<Tree.Node<Rank>, List<String>> nodeMap    = new HashMap<>();
		int                                lastRankId = -1;

		for (Rank rank : loadedRanks) {
			int id = rank.getUsedId();

			List<Integer> permIds = ranksPermissions.stream()
					.filter(rp -> rp.rankId() == id)
					.map(RankPermission::permissionId)
					.toList();

			permissions.keySet()
					.stream()
					.filter(permIds::contains)
					.map(permissions::get)
					.forEach(rank::addPermission);

			if (id > lastRankId) lastRankId = id;

			ranks.put(id, rank);
		}

		Rank.setID(lastRankId + 1);

		// Build the tree node map: rank node → names of its direct children
		for (int rankId : ranks.keySet()) {
			List<String> children = ranksParent.stream()
					.filter(rp -> rp.rankId() == rankId)
					.map(rp -> ranks.get(rp.childId()).getName())
					.toList();

			nodeMap.put(ranks.get(rankId).getNode(), children);
		}

		// Add the rank head to the tree (GR-13: a missing head keeps the persisted root, it never invents one)
		Tree.Node<Rank> root = rootNode();
		if (root != null) rankTree.add(root);

		// Wire each node's children
		for (Map.Entry<Tree.Node<Rank>, List<String>> entry : nodeMap.entrySet()) {
			Tree.Node<Rank> parent   = entry.getKey();
			List<String>    children = entry.getValue();

			if (!children.isEmpty()) for (String child : children) {
				Tree.Node<Rank> childNode = findChildNode(nodeMap, child);
				if (childNode != null) parent.add(childNode);
			}
		}

		warnIfTailMissing();

		// The fresh-install preset nodes go on after the ranks and the permission counter are loaded (FK-safe ids)
		if (seeded) applyPresetPermissions();

		// Set data suppliers so repositoryRegistry.saveAll() can persist each collection
		rankRepo.setDataSupplier(ranks::values);
		permissionRepo.setDataSupplier(permissions::values);
		rankParentRepo.setDataSupplier(() -> ranksParent);
		rankPermissionRepo.setDataSupplier(() -> ranksPermissions);
	}

	/**
	 * Writes the preset ladder before {@code loadAll()} reads it, only while the rank table is empty (a fresh install,
	 * D13 A). An existing rank tree is never touched. The every-boot head/tail insert is gone.
	 *
	 * @return {@code true} when the preset was seeded
	 */
	private boolean seedIfEmpty(IRepository<Rank> rankRepo, IRepository<RankParent> rankParentRepo) {
		if (!(rankRepo instanceof RankRepository repo)) return false;
		if (!(rankParentRepo instanceof RankParentRepository parentRepo)) return false;

		try {
			return RankSeed.seedIfEmpty(repo, parentRepo, rules, GangSettings.getGangRankHead(),
			                            GangSettings.getGangRankTail());
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to seed the gang rank preset", exception);
		}
	}

	/**
	 * Grants each preset rank its {@code Ranks.Preset_Permissions} nodes on a fresh install.
	 */
	private void applyPresetPermissions() {
		for (Rank rank : ranks.values()) {
			for (String node : rules.getPresetPermissions(rank.getName())) {
				addPermission(rank, node);
			}
		}
	}

	/**
	 * A renamed {@code Rank.Tail} is not recreated (D13 A): the persisted ranks stay as they are, and one warning names
	 * the configured tail and the persisted leaf ranks so the owner can fix the config.
	 */
	private void warnIfTailMissing() {
		String tail = GangSettings.getGangRankTail();
		if (ranks.isEmpty() || get(tail) != null) return;

		Set<Integer> parentIds = ranksParent.stream().map(RankParent::rankId).collect(Collectors.toSet());
		String leaves = ranks.values()
				.stream()
				.filter(rank -> !parentIds.contains(rank.getUsedId()))
				.map(Rank::getName)
				.collect(Collectors.joining(", "));

		log.warn("Rank.Tail '{}' matches no persisted rank; no rank is created. The persisted leaf rank(s): [{}]",
		         tail, leaves);
	}

	/**
	 * The tree root is the rank named {@code Rank.Head}. When no persisted rank has that name (the head was renamed), the
	 * persisted root is kept (the rank nobody sits above; lowest id first) and one warning names both. Nothing is created.
	 */
	@Nullable
	private Tree.Node<Rank> rootNode() {
		String head = GangSettings.getGangRankHead();

		Rank named = ranks.values()
				.stream().filter(rank -> rank.getName().equalsIgnoreCase(head)).findFirst().orElse(null);
		if (named != null) return named.getNode();

		Set<Integer> childIds = ranksParent.stream().map(RankParent::childId).collect(Collectors.toSet());

		Rank persistedRoot = ranks.values()
				.stream()
				.filter(rank -> !childIds.contains(rank.getUsedId()))
				.min(Comparator.comparingInt(Rank::getUsedId))
				.orElse(null);
		if (persistedRoot == null) return null;

		log.warn("Rank.Head '{}' matches no persisted rank; keeping '{}' (id {}) as the rank tree root",
		         head, persistedRoot.getName(), persistedRoot.getUsedId());
		return persistedRoot.getNode();
	}

	public void add(Rank rank) {
		ranks.put(rank.getUsedId(), rank);
	}

	/**
	 * Checks if a permission string already exists in the global permissions map.
	 *
	 * @param permissionString the permission string to check
	 *
	 * @return true if the permission exists, false otherwise
	 */
	public boolean permissionExists(String permissionString) {
		boolean rankPermissions = permissions.values()
				.stream().anyMatch(p -> p.getPermission().equalsIgnoreCase(permissionString));

		if (!rankPermissions) rankPermissions = GangPermissions.ALL.stream().anyMatch(permissionString::equalsIgnoreCase);
		if (!rankPermissions) rankPermissions = permissionRegistry.contains(permissionString);

		return rankPermissions;
	}

	/**
	 * Finds an existing permission by its string value.
	 *
	 * @param permissionString the permission string to find
	 *
	 * @return the Permission object if found, null otherwise
	 */
	@Nullable
	public Permission findPermission(String permissionString) {
		return permissions.values()
				.stream().filter(p -> p.getPermission().equalsIgnoreCase(permissionString)).findFirst().orElse(null);
	}

	/**
	 * Adds a permission to a rank. If the permission already exists globally, it reuses the existing permission. If the
	 * rank already has this permission, no action is taken.
	 *
	 * @param rank the rank to add the permission to
	 * @param permissionString the permission string to add
	 *
	 * @return true if the permission was added, false if it already existed on the rank
	 */
	public boolean addPermission(Rank rank, String permissionString) {
		// Check if the rank already has this permission
		if (rank.contains(permissionString)) {
			return false;
		}

		// Check if the permission already exists globally
		Permission permission = findPermission(permissionString);
		boolean    freshPerm  = permission == null;

		if (freshPerm) {
			// Create a new permission
			permission = new Permission(Permission.getNewId(), permissionString);
			permissions.put(permission.getUsedId(), permission);
		}

		RankPermission link = new RankPermission(rank.getUsedId(), permission.getUsedId());

		// Add the rank-permission relationship
		ranksPermissions.add(link);
		// Add permission to the rank itself
		rank.addPermission(permission);

		// Persist immediately so a server restart before autosave doesn't lose the change
		if (freshPerm && permissionRepo != null) permissionRepo.save(permission);
		if (rankPermissionRepo != null) rankPermissionRepo.save(link);

		return true;
	}

	public void removePermission(Rank rank, String permission) {
		Permission perm = rank.getPermissions()
				.stream()
				.filter(currentPerm -> currentPerm.getPermission().equalsIgnoreCase(permission))
				.findFirst()
				.orElse(null);

		if (perm == null) return;

		RankPermission link = new RankPermission(rank.getUsedId(), perm.getUsedId());

		ranksPermissions.removeIf(
				rp -> rp.rankId() == rank.getUsedId() && rp.permissionId() == perm.getUsedId());
		rank.removePermission(perm);

		if (rankPermissionRepo != null) rankPermissionRepo.delete(link);

		if (ranksPermissions.stream().anyMatch(rp -> rp.permissionId() == perm.getUsedId())) return;

		permissions.remove(perm.getUsedId());
		if (permissionRepo != null) permissionRepo.delete(perm);
	}

	/**
	 * Places {@code parent} directly above {@code rank} and persists the link right away.
	 *
	 * @return {@code false} when {@code parent} already has a place above another rank (a rank sits above one rank
	 *         only) or already sits below {@code rank}, which would close a loop that every tree walk recurses into
	 */
	public boolean addParent(Rank rank, Rank parent) {
		Tree.Node<Rank> parentNode = parent.getNode();

		if (parentNode.getParent() != null || rankTree.isDescendant(parentNode, rank.getNode())) return false;

		rank.getNode().add(parentNode);

		RankParent link = new RankParent(rank.getUsedId(), parent.getUsedId());
		ranksParent.add(link);
		if (rankParentRepo != null) rankParentRepo.save(link);

		return true;
	}

	/**
	 * Removes the link placing {@code parent} directly above {@code rank} and deletes it right away.
	 */
	public void removeParent(Rank rank, Rank parent) {
		if (!rank.getNode().remove(parent.getNode())) return;

		parent.getNode().setParent(null);

		RankParent link = new RankParent(rank.getUsedId(), parent.getUsedId());
		ranksParent.remove(link);
		if (rankParentRepo != null) rankParentRepo.delete(link);
	}

	/**
	 * Drops the rank, detaches it from the tree and prunes every parent/permission link naming it, so the next
	 * autosave cannot write those rows back. The caller deletes the rows themselves.
	 */
	public boolean remove(Rank rank) {
		int id = rank.getUsedId();

		Tree.Node<Rank> parent = rank.getNode().getParent();
		if (parent != null) parent.remove(rank.getNode());

		ranksParent.removeIf(link -> link.rankId() == id || link.childId() == id);
		ranksPermissions.removeIf(link -> link.rankId() == id);

		return ranks.remove(id) != null;
	}

	public void clear() {
		Rank.setID(0);
		Permission.setID(0);
		ranks.clear();
		rankTree.clear();
		ranksParent.clear();
		permissions.clear();
		ranksPermissions.clear();
	}

	@Override
	public void onClear() {
		clear();
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		initialize();
	}

	public Permission getPermission(int id) {
		return permissions.get(id);
	}

	public Rank get(int id) {
		return ranks.get(id);
	}

	@Nullable
	public Rank get(String name) {
		return ranks.values()
				.stream().filter(rank -> rank.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	public Map<Integer, Rank> getRanks() {
		return Collections.unmodifiableMap(ranks);
	}

	public Map<Integer, Permission> getPermissions() {
		return Collections.unmodifiableMap(permissions);
	}

	public Set<RankParent> getRanksParent() {
		return Collections.unmodifiableSet(ranksParent);
	}

	public Set<RankPermission> getRanksPermissions() {
		return Collections.unmodifiableSet(ranksPermissions);
	}

	public int size() {
		return ranks.size();
	}

	@Override
	public String toString() {
		return String.format("ranks=%s", ranks);
	}

	@Nullable
	private Tree.Node<Rank> findChildNode(Map<Tree.Node<Rank>, List<String>> nodeMap, String child) {
		return nodeMap.keySet()
				.stream().filter(node -> node.getData().getName().equalsIgnoreCase(child)).findFirst().orElse(null);
	}

}
