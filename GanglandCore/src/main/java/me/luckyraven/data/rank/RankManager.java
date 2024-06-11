package me.luckyraven.data.rank;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.PermissionTable;
import me.luckyraven.database.tables.RankParentTable;
import me.luckyraven.database.tables.RankPermissionTable;
import me.luckyraven.database.tables.RankTable;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RankManager {

	private final Gangland                    gangland;
	private final Map<Integer, Rank>          ranks;
	private final Set<Pair<Integer, Integer>> ranksParent;
	private final Map<Integer, Permission>    permissions;
	private final Set<Pair<Integer, Integer>> ranksPermissions;

	private final @Getter Tree<Rank> rankTree;

	public RankManager(Gangland gangland) {
		this.gangland         = gangland;
		this.ranks            = new HashMap<>();
		this.ranksParent      = new HashSet<>();
		this.rankTree         = new Tree<>();
		this.permissions      = new HashMap<>();
		this.ranksPermissions = new HashSet<>();
	}

	public void initialize(RankTable rankTable, RankParentTable rankParentTable, PermissionTable permissionTable,
						   RankPermissionTable rankPermissionTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			Map<Tree.Node<Rank>, List<String>> nodeMap = new HashMap<>();

			List<Object[]> rowsRank           = database.table(rankTable.getName()).selectAll();
			List<Object[]> rowsRankParent     = database.table(rankParentTable.getName()).selectAll();
			List<Object[]> rowsPermission     = database.table(permissionTable.getName()).selectAll();
			List<Object[]> rowsRankPermission = database.table(rankPermissionTable.getName()).selectAll();

			// set up the rank parent relation
			for (Object[] result : rowsRankParent) {
				int id       = (int) result[0];
				int parentId = (int) result[1];

				Pair<Integer, Integer> parent = new Pair<>(id, parentId);

				ranksParent.add(parent);
			}

			// set up the permissions
			int lastPermissionId = -1;

			for (Object[] result : rowsPermission) {
				int    id         = (int) result[0];
				String permission = String.valueOf(result[1]);

				Permission perm = new Permission(id, permission);
				lastPermissionId = id;

				permissions.put(id, perm);
			}

			Permission.setID(lastPermissionId + 1);

			// set up the rank permissions relation
			for (Object[] result : rowsRankPermission) {
				int rankId = (int) result[0];
				int permId = (int) result[1];

				Pair<Integer, Integer> rankPerm = new Pair<>(rankId, permId);

				ranksPermissions.add(rankPerm);
			}

			// data information
			int lastRankId = -1;

			for (Object[] result : rowsRank) {
				int    id   = (int) result[0];
				String name = String.valueOf(result[1]);

				// set up the permissions
				// get the permissions which have this rank id
				List<Integer> permIds = ranksPermissions.stream()
														.filter(pair -> pair.first() == id)
														.map(Pair::second)
														.toList();
				// group them together and add them as a permissions list
				List<Permission> perms = this.permissions.keySet()
														 .stream()
														 .filter(permIds::contains)
														 .map(this.permissions::get)
														 .toList();
				List<Permission> permissions = new ArrayList<>(perms);

				Rank rank = new Rank(name, id, permissions);
				lastRankId = id;

				ranks.put(id, rank);
			}

			Rank.setID(lastRankId + 1);

			// set up the children of the rank
			for (int rankId : ranks.keySet()) {
				// get the rank parents of the specified id
				List<String> children = this.ranksParent.stream()
														// need only the ranks which are under this rank id
														.filter(pair -> pair.first() == rankId)
														// get the name of the ranks under this id
														.map(pair -> this.ranks.get(pair.second()).getName()).toList();

				nodeMap.put(this.ranks.get(rankId).getNode(), children);
			}

			// add the rank head
			rankTree.add(nodeMap.keySet()
								.stream()
								.filter(node -> node.getData()
													.getName()
													.equalsIgnoreCase(SettingAddon.getGangRankHead()))
								.findFirst()
								// what if there was a node that doesn't have this specific head!
								// need to find the node that would be attached to this default rank
								.orElse(new Rank(SettingAddon.getGangRankHead(), Rank.getNewId()).getNode()));

			// map information
			// the map saves the node and the child of that node
			// when data collected, the loop will iterate over each entry, and adds the node to the parent entry key

			// member, parent = owner -> null (head)
			// owner, parent = null -> member (tail)

			// reasons for calling head is because it is the initial rank the user will have
			// reasons for calling tail is because it is the final rank the user will have
			// because of the structure of the tree, there can be multiple parents (will call it children because of the
			// inverse nature)
			// the children of that node makes the tree diverse so the user can choose 1 node for the specified tree,
			// this creates tree diversity.
			// From this concept, each node can have unique perks and diverse to their specified node
			// OR return to a single unique node which continues the list.
			// Example:        user
			//                /   \
			//          peasant  member
			//            /  \     /
			//       farmer   chief
			//                 ...

			// the tree is built in reverse
			for (Map.Entry<Tree.Node<Rank>, List<String>> entry : nodeMap.entrySet()) {
				Tree.Node<Rank> parent   = entry.getKey();
				List<String>    children = entry.getValue();

				if (!children.isEmpty()) for (String child : children) {
					Tree.Node<Rank> childNode = findChildNode(nodeMap, child);
					if (childNode != null) parent.add(childNode);
				}
			}
		});
	}

	public void add(Rank rank) {
		ranks.put(rank.getUsedId(), rank);
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
		return ranks.values().stream().filter(rank -> rank.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	public Map<Integer, Rank> getRanks() {
		return Collections.unmodifiableMap(ranks);
	}

	public Map<Integer, Permission> getPermissions() {
		return Collections.unmodifiableMap(permissions);
	}

	public Set<Pair<Integer, Integer>> getRanksParent() {
		return Collections.unmodifiableSet(ranksParent);
	}

	public Set<Pair<Integer, Integer>> getRanksPermissions() {
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
					  .stream()
					  .filter(node -> node.getData().getName().equalsIgnoreCase(child))
					  .findFirst()
					  .orElse(null);
	}

}
