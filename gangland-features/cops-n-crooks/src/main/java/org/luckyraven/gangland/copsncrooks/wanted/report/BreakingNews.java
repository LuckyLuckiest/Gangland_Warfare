package org.luckyraven.gangland.copsncrooks.wanted.report;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArc;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;

import java.util.function.Consumer;

/**
 * Server-wide breaking news for big chases: the live headline once per chase (4 stars or 120 s) under a global cooldown, and
 * the closing headline for a chase that made the news. Holds the only cooldown stamp. Main thread only.
 *
 * @since 0.21.0
 */
public final class BreakingNews {

	private final ChaseArcs         arcs;
	private final ChaseConfigLoader config;
	private final WantedMessages    messages;
	private final Consumer<String>  broadcast;

	/** {@code broadcast} sends one coloured line to every online player. */
	public BreakingNews(ChaseArcs arcs, ChaseConfigLoader config, WantedMessages messages, Consumer<String> broadcast) {
		this.arcs      = arcs;
		this.config    = config;
		this.messages  = messages;
		this.broadcast = broadcast;
	}

	/** Sends the live headline when every condition holds; true when it went out. */
	public boolean check(Player player, int level, String reason) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The AI-tick hook: the time trigger. */
	public void onAiTick(Player player, @Nullable CopGroup group) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The closing headline for a chase that made the news; no cooldown. True when it went out. */
	public boolean closing(Player player, ChaseArc arc, String ending) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
