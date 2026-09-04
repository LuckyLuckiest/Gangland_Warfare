package org.luckyraven.gangland.command.extension;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The seam runtime modules use to hang sub-arguments under a core command (0.8.2 module split, mail pilot):
 * {@link CommandContributions} collects every {@link CommandContribution} bean from the container and hands a
 * command only the ones addressed to its path. With no module installed the core gets an empty list and builds its
 * tree exactly as before.
 */
@DisplayName("CommandContributions")
class CommandContributionsTest {

	@Test
	@DisplayName("an empty container yields no contributions for any path")
	@SuppressWarnings("unchecked")
	void from_emptyContainer_isEmpty() {
		CommandContributions contributions = CommandContributions.from(new DependencyContainer());

		assertEquals(0, contributions.size());
		assertFalse(contributions.hasAny("gang"));
		assertTrue(contributions.createFor("gang", mock(Tree.class), mock(Argument.class)).isEmpty());
	}

	@Test
	@DisplayName("contributions are filtered by parent path and receive the tree and parent argument")
	@SuppressWarnings("unchecked")
	void createFor_filtersByParent() {
		DependencyContainer container = new DependencyContainer();
		Argument            gangArg   = mock(Argument.class);
		Argument            allyArg   = mock(Argument.class);
		Tree<Argument>      tree      = mock(Tree.class);
		Argument            invite    = mock(Argument.class);
		Argument            request   = mock(Argument.class);

		container.registerInstance(RecordingContribution.class, new RecordingContribution("gang", invite));
		container.registerInstance(OtherContribution.class, new OtherContribution("gang.ally", request));

		CommandContributions contributions = CommandContributions.from(container);

		assertEquals(2, contributions.size());
		assertTrue(contributions.hasAny("gang"));
		assertTrue(contributions.hasAny("gang.ally"));
		assertFalse(contributions.hasAny("turf"));

		List<Argument> forGang = contributions.createFor("gang", tree, gangArg);
		assertEquals(List.of(invite), forGang);

		List<Argument> forAlly = contributions.createFor("gang.ally", tree, allyArg);
		assertEquals(List.of(request), forAlly);

		RecordingContribution recording = container.getInstance(RecordingContribution.class);
		assertSame(tree, recording.tree);
		assertSame(gangArg, recording.parent);
	}

	/** Two distinct concrete types, the way module configs register them, both indexed under the interface. */
	private static class RecordingContribution implements CommandContribution {

		private final String   parentPath;
		private final Argument produced;
		private Tree<Argument> tree;
		private Argument       parent;

		private RecordingContribution(String parentPath, Argument produced) {
			this.parentPath = parentPath;
			this.produced   = produced;
		}

		@Override
		public String parent() {
			return parentPath;
		}

		@Override
		public List<Argument> create(Tree<Argument> tree, Argument parent) {
			this.tree   = tree;
			this.parent = parent;
			return List.of(produced);
		}
	}

	private static final class OtherContribution extends RecordingContribution {

		private OtherContribution(String parentPath, Argument produced) {
			super(parentPath, produced);
		}
	}
}
