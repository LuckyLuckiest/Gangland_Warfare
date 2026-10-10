package org.luckyraven.gangland.menu;

import org.jetbrains.annotations.Nullable;

/**
 * How a menu opens: a command or an event, with an optional permission. {@code condition} (Open.Condition, T-226) is a
 * ConditionEvaluator expression that must hold for a command to open the menu; when it does not, the command is left
 * to its own executor.
 */
public record OpenInventory(State state, String output, String permission, @Nullable String condition) { }
