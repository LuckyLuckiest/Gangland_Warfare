package me.luckyraven.data.rank;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.util.datastructure.Tree;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RankManager {

	private final Gangland                 gangland;
	private final PermissionManager        permissionManager;
	private final Map<Integer, Rank>       ranks;
	private final Set<RankParent>          ranksParent;
	private final Map<Integer, Permission> permissions;
	private final Set<RankPermission>      ranksPermissions;

	private final @Getter Tree<Rank> rankTree;

	public RankManager(Gangland gangland) {
		this.gangland          = gangland;
		this.permissionManager = gangland.getInitializer().getPermissionManager();
		this.ranks             = new HashMap<>();
		this.ranksParent       = new HashSet<>();
		this.rankTree          = new Tree<>();
		this.permissions       = new HashMap<>();
		this.ranksPermissions  = new HashSet<>();
	}

	public void initialize() {
		GanglandDatabase   database = gangland.getInitializer().getGanglandDatabase();
		RepositoryRegistry registry = database.getRepositoryRegistry();

		IRepository<Rank>           rankRepo           = registry.getRepository(Rank.class);
		IRepository<RankParent>     rankParentRepo     = registry.getRepository(RankParent.class);
		IRepository<Permission>     permissionRepo     = registry.getRepository(Permission.class);
		IRepository<RankPermission> rankPermissionRepo = registry.getRepository(RankPermission.class);

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
					.map(rp -> ranks.get(rp.parentId()).getName())
					.toList();

			nodeMap.put(ranks.get(rankId).getNode(), children);
		}

		// Add the rank head to the tree
		rankTree.add(nodeMap.keySet()
							 .stream()
							 .filter(node -> node.getData()
												 .getName()
												 .equalsIgnoreCase(Settings.getGangRankHead()))
							 .findFirst()
							 .orElse(new Rank(Settings.getGangRankHead(), Rank.getNewId()).getNode()));

		// Wire each node's children
		for (Map.Entry<Tree.Node<Rank>, List<String>> entry : nodeMap.entrySet()) {
			Tree.Node<Rank> parent   = entry.getKey();
			List<String>    children = entry.getValue();

			if (!children.isEmpty()) for (String child : children) {
				Tree.Node<Rank> childNode = findChildNode(nodeMap, child);
				if (childNode != null) parent.add(childNode);
			}
		}

		// Set data suppliers so repositoryRegistry.saveAll() can persist each collection
		rankRepo.setDataSupplier(() -> ranks.values());
		permissionRepo.setDataSupplier(() -> permissions.values());
		rankParentRepo.setDataSupplier(() -> ranksParent);
		rankPermissionRepo.setDataSupplier(() -> ranksPermissions);
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

		if (!rankPermissions) rankPermissions = permissionManager.contains(permissionString);

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

		if (permission == null) {
			// Create a new permission
			permission = new Permission(Permission.getNewId(), permissionString);
			permissions.put(permission.getUsedId(), permission);
		}

		// Add the rank-permission relationship
		ranksPermissions.add(new RankPermission(rank.getUsedId(), permission.getUsedId()));
		// Add permission to the rank itself
		rank.addPermission(permission);

		return true;
	}

	public void removePermission(Rank rank, String permission) {
		Permission perm = rank.getPermissions()
				.stream()
				.filter(currentPerm -> currentPerm.getPermission().equalsIgnoreCase(permission))
				.findFirst()
				.orElse(null);

		if (perm == null) return;

		ranksPermissions.removeIf(
				rp -> rp.rankId() == rank.getUsedId() && rp.permissionId() == perm.getUsedId());
		rank.removePermission(perm);

		if (ranksPermissions.stream().anyMatch(rp -> rp.permissionId() == perm.getUsedId())) return;

		permissions.remove(perm.getUsedId());
	}

	public boolean remove(Rank rank) {
		Rank r = ranks.remove(rank.getUsedId());
		return r != null;
	}

	public void clear() {
		Rank.setID(0);
		ranks.clear();
		rankTree.clear();
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
