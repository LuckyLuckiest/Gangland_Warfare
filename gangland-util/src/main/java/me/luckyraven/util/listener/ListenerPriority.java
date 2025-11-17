package me.luckyraven.util.listener;

import lombok.Getter;

/**
 * Represents a listener priority in execution.
 * <p>
 * Listeners with lower priority are registered first will listeners with higher priority are called last.
 * <p>
 * Listeners are called in following order: {@link #LOWEST} -> {@link #LOW} -> {@link #NORMAL} -> {@link #HIGH} ->
 * {@link #HIGHEST}
 */
@Getter
public enum ListenerPriority {

	LOWEST(0),
	LOW(1),
	NORMAL(2),
	HIGH(3),
	HIGHEST(4);

	private final int priority;

	ListenerPriority(int priority) {
		this.priority = priority;
	}

}
