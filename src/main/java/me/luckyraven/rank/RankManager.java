package me.luckyraven.rank;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.RankDatabase;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.SettingAddon;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RankManager {

	private final Map<Integer, Rank> ranks;
	private final Gangland           gangland;

	private final @Getter Tree<Rank> rankTree;

	public RankManager(Gangland gangland) {
		this.gangland = gangland;
		this.ranks = new HashMap<>();
		this.rankTree = new Tree<>();
	}

	public void initialize(RankDatabase rankDatabase) {
		DatabaseHelper helper = new DatabaseHelper(gangland, rankDatabase);

		helper.runQueries(database -> {
			Map<Tree.Node<Rank>, List<String>> nodeMap  = new HashMap<>();
			List<Object[]>                     rowsData = database.table("data").selectAll();

			// data information
			for (Object[] result : rowsData) {
				int          id          = (int) result[0];
				String       name        = String.valueOf(result[1]);
				List<String> permissions = database.getList(String.valueOf(result[2]));
				List<String> child       = database.getList(String.valueOf(result[3]));

				Rank rank = new Rank(name, permissions);

				nodeMap.put(rank.getNode(), child);

				ranks.put(id, rank);
			}

			rankTree.add(nodeMap.keySet()
			                    .stream()
			                    .filter(node -> node.getData()
			                                        .getName()
			                                        .equalsIgnoreCase(SettingAddon.getGangRankHead()))
			                    .findFirst()
			                    .orElse(null));

			// map information
			// the map saves the node and the child of that node
			// when data collected, the loop will iterate over each entry, and adds the node
			// to the parent entry key

			// member, parent = owner -> null (head)
			// owner, parent = null -> member (tail)

			// reasons for calling head is because it is the initial rank the user will have
			// reasons for calling tail is because it is the final rank the user will have
			// because of the structure of the tree, there can be multiple parents (will call it children because of the inverse nature)
			// the children of that node makes the tree diverse so the user can choose 1 node for the specified tree, this creates
			// tree diversity.
			// From this concept, each node can have unique perks and diverse to their specified node OR
			// return back to a single unique node which continues the list.
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

	private Tree.Node<Rank> findChildNode(Map<Tree.Node<Rank>, List<String>> nodeMap, String child) {
		return nodeMap.keySet()
		              .stream()
		              .filter(node -> node.getData().getName().equalsIgnoreCase(child))
		              .findFirst()
		              .orElse(null);
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

	public Rank get(int id) {
		return ranks.get(id);
	}

	public Rank get(String name) {
		return ranks.values().stream().filter(rank -> rank.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	public void refactorIds(RankDatabase rankDatabase) {
		DatabaseHelper helper = new DatabaseHelper(gangland, rankDatabase);

		helper.runQueries(database -> {
			Database config = database.table("data");

			List<Object[]> rowsData = config.selectAll();

			// remove all the data from the table
			config.delete("", "");

			int tempId = 1;
			for (Object[] result : rowsData) {
				int id = (int) result[0];

				Rank rank = ranks.get(id);
				ranks.remove(rank.getUsedId());

				rank.setUsedId(tempId);
				ranks.put(tempId, rank);

				String permissions = database.createList(rank.getPermissions());
				String children = database.createList(rank.getNode()
				                                          .getChildren()
				                                          .stream()
				                                          .map(Tree.Node::getData)
				                                          .map(Rank::getName)
				                                          .toList());

				config.insert(config.getColumns().toArray(String[]::new),
				              new Object[]{rank.getUsedId(), rank.getName(), permissions, children},
				              new int[]{Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR});

				tempId++;
			}

			Rank.setID(tempId - 1);
		});
	}

	public Map<Integer, Rank> getRanks() {
		return Collections.unmodifiableMap(ranks);
	}

	@Override
	public String toString() {
		return String.format("ranks=%s", ranks);
	}

}
