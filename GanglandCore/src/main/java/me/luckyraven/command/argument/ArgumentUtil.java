package me.luckyraven.command.argument;

import me.luckyraven.util.datastructure.Tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArgumentUtil {

	/**
	 * Gets the argument sequence starting from the command "glw" till the command specified as a parameter.
	 *
	 * @param argument the argument to fetch the root of
	 *
	 * @return the string representation of the argument
	 */
	public static String getArgumentSequence(Argument argument) {
		List<String> sequence = new ArrayList<>();

		getArgumentSequence(sequence, argument.getNode());

		Collections.reverse(sequence);

		return "glw " + String.join(" ", sequence) + " " + argument.getArguments()[0];
	}

	private static void getArgumentSequence(List<String> list, Tree.Node<Argument> node) {
		if (node == null || node.getParent() == null) return;

		list.add(node.getParent().getData().getArguments()[0]);
		getArgumentSequence(list, node.getParent());
	}

}
