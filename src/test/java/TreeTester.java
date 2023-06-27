import me.luckyraven.datastructure.Node;
import me.luckyraven.datastructure.Tree;

import java.util.Arrays;

public class TreeTester {

	public static void main(String[] args) {
		// Create the tree
		Tree<String> tree = new Tree<>();
		tree.add("cmd");
		Node<String> child1 = new Node<>("child1");
		Node<String> child2 = new Node<>("child2");
		Node<String> child3 = new Node<>("child3");
		Node<String> child4 = new Node<>("child4");
		Node<String> child5 = new Node<>("child5");
		Node<String> child6 = new Node<>("child6");

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
