package org.luckyraven.gangland.copsncrooks.wanted.mostwanted;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.util.Collection;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The city's Most Wanted: the one online player with the most crime-earned stars (four or more, held for a while). Main
 * thread only.
 *
 * @since 0.21.0
 */
@CustomLog
public class MostWanted implements BeanLifecycle {

	private final JavaPlugin                                  plugin;
	private final ChaseArcs                                   arcs;
	private final ChaseConfigLoader                           config;
	private final WantedMessages                              messages;
	private final Supplier<Collection<? extends Player>>      online;
	private final Consumer<String>                            broadcast;
	private final BiConsumer<Player, Integer>                 radio;

	public MostWanted(JavaPlugin plugin, ChaseArcs arcs, ChaseConfigLoader config, WantedMessages messages,
	                  Supplier<Collection<? extends Player>> online, Consumer<String> broadcast,
	                  BiConsumer<Player, Integer> radio) {
		this.plugin    = plugin;
		this.arcs      = arcs;
		this.config    = config;
		this.messages  = messages;
		this.online    = online;
		this.broadcast = broadcast;
		this.radio     = radio;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void onClear() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The production beat: {@code tick(arcs.now())}. */
	public void tick() {
		throw new UnsupportedOperationException("TDD stub");
	}

	void tick(long now) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** A player left: his qualifying clock goes, and the title with it. */
	public void quit(UUID id) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public @Nullable String holderName() {
		return null;
	}

	/** The holder's earned stars; 0 when the title is vacant. */
	public int holderStars() {
		return 0;
	}
}
