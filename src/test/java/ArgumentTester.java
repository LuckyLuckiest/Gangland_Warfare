import me.luckyraven.command.argument.Argument;
import me.luckyraven.datastructure.Tree;

public class ArgumentTester {

	public static void main(String[] args) {
		Tree<Argument> tree = new Tree<>();
		Argument       main = new Argument("main", tree);

		Argument one = new Argument("one", tree);
		Argument two = new Argument("two", tree);
		one.addSubArgument(two);

		Argument three = new Argument("three", tree);
		Argument four  = new Argument("four", tree);
		Argument five  = new Argument("five", tree);
		three.addSubArgument(four);
		three.addSubArgument(five);

		main.addSubArgument(one);
		main.addSubArgument(three);

		tree.add(main.getNode());

		Argument[] arguments = new Argument[]{main, one, two};

		Argument arg = tree.traverseToList(arguments);

		System.out.println(arg);
	}

}
