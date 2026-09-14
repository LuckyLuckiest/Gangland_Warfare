package org.luckyraven.gangland.command.extension;

import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;

/**
 * Sub-arguments a runtime module attaches under one of the core's commands. The core cannot name a module type,
 * so a module that wants {@code /glw gang invite} registers a bean implementing this contract; the owning core
 * command pulls every contribution for its {@link #parent() path} out of the container while it builds its
 * argument tree and appends what {@link #create} returns.
 *
 * <p>Paths are dotted argument labels below {@code /glw}: {@code "gang"} for {@code /glw gang <x>},
 * {@code "gang.ally"} for {@code /glw gang ally <x>}. A command that supports contributions documents the paths
 * it queries; today {@code GangCommand} queries {@code gang} and {@code gang.ally}.
 */
public interface CommandContribution {

	/** The dotted path of the argument this contribution extends. */
	String parent();

	/**
	 * Build the sub-arguments to attach under {@code parent}. Called once, while the owning command constructs its
	 * tree — the same moment the core's own sub-arguments are built, so every bean already exists.
	 *
	 * @param tree   the command's argument tree, to pass to the {@code SubArgument} constructors.
	 * @param parent the argument the returned sub-arguments hang off.
	 */
	List<Argument> create(Tree<Argument> tree, Argument parent);
}
