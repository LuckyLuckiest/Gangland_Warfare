package datastructure;

import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.datastructure.Tree.Node;

import java.util.Arrays;

public class TreeTester {

	public static void main(String[] args) {
		// Create the tree
		Tree<String> tree = new Tree<>();
		tree.add("cmd");
		var child1 = new Node<>("child1");
		var child2 = new Node<>("child2");
		var child3 = new Node<>("child3");
		var child4 = new Node<>("child4");
		var child5 = new Node<>("child5");
		var child6 = new Node<>("child6");

		tree.getRoot().addAll(Arrays.asList(child1, child2, child3));
		child1.add(child4);
		child2.addAll(Arrays.asList(child5, child6));

		// Define the list
		String[] list = {"cmd", "child2", "child7"};

		// Traverse the tree and find the last valid node
		String result = tree.traverseToList(list);

		// Output the result
		if (result != null) {
			System.out.println("Valid list: " + result);
		} else {
			System.out.println("Not valid list.");
		}

		String lastValid = tree.traverseLastValid(list);

		if (lastValid != null) {
			System.out.println("Last valid value: " + lastValid);
		} else {
			System.out.println("No valid value found.");
		}

		String[] lst     = {"arg"};
		String   resultT = tree.traverseLastValid(lst);

		System.out.println(resultT == null);

	}

}
