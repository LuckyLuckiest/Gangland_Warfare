package org.luckyraven.gangland.gang.contract;

/**
 * A repository that can tell whether its last {@code loadAll()} failed. Keystone's {@code AbstractRepository.loadAll}
 * answers a failed read with an empty list, which callers cannot tell from an empty table (T-249).
 */
public interface LoadOutcome {

	/** True when the last load threw; false after a successful load and before any load. */
	boolean lastLoadFailed();

}
