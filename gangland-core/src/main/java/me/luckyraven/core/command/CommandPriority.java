package me.luckyraven.core.command;

import lombok.Getter;

/**
 * Represents a command registration priority.
 * <p>
 * Commands with higher priority are registered first; commands with lower priority are registered last.
 * <p>
 * Commands are registered in the following order: {@link #HIGHEST} -> {@link #HIGH} -> {@link #NORMAL} -> {@link #LOW}
 * -> {@link #LOWEST}
 */
@Getter
public enum CommandPriority {

	LOWEST(0),
	LOW(1),
	NORMAL(2),
	HIGH(3),
	HIGHEST(4);

	private final int priority;

	CommandPriority(int priority) {
		this.priority = priority;
	}

}
