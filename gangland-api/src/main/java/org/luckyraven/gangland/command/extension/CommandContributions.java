package org.luckyraven.gangland.command.extension;

import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link CommandContribution} beans a command can draw on, looked up once from the root container. Commands
 * are constructed in the COMMAND phase, after every module bean exists, so the snapshot is complete.
 */
public final class CommandContributions {

	private final List<CommandContribution> contributions;

	public CommandContributions(List<CommandContribution> contributions) {
		this.contributions = List.copyOf(contributions);
	}

	/** Every contribution registered in {@code container}; empty when no module contributed anything. */
	public static CommandContributions from(DependencyContainer container) {
		List<CommandContribution> found = container.getAllInstances(CommandContribution.class);
		return new CommandContributions(found == null ? List.of() : found);
	}

	public static CommandContributions none() {
		return new CommandContributions(List.of());
	}

	/** Build the sub-arguments every contribution for {@code parent} provides, in registration order. */
	public List<Argument> createFor(String parent, Tree<Argument> tree, Argument parentArgument) {
		List<Argument> arguments = new ArrayList<>();
		for (CommandContribution contribution : contributions) {
			if (parent.equals(contribution.parent())) {
				arguments.addAll(contribution.create(tree, parentArgument));
			}
		}
		return arguments;
	}

	public boolean hasAny(String parent) {
		return contributions.stream().anyMatch(contribution -> parent.equals(contribution.parent()));
	}

	public int size() {
		return contributions.size();
	}
}
