package org.luckyraven.gangland.copsncrooks.npc.police.perception;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.SightSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;

import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Darkness and crowds hide you (0.20.0, {@code Cops.Sight}): one {@link CopPerception.SightFactor} scaling every
 * cop's alert range by light level, civilian crowd and sneaking; gunfire, fire and glowing reset it to 1.
 * TDD skeleton: behaviour arrives with the green chunks.
 */
public class SightModel implements CopPerception.SightFactor {

	private final Supplier<SightSettings> settings;
	private final CivilianNpcRegistry     civilians;
	private final CopManager              copManager;
	private final LongSupplier            clock;
	private       Consumer<String>        debugSink = line -> { };

	public SightModel(Supplier<SightSettings> settings, CivilianNpcRegistry civilians, CopManager copManager,
	                  LongSupplier clock) {
		this.settings   = settings;
		this.civilians  = civilians;
		this.copManager = copManager;
		this.clock      = clock;
	}

	/** Where the throttled {@code SIGHT} debug lines go (default: nowhere). */
	public void setDebugSink(Consumer<String> sink) {
		this.debugSink = sink;
	}

	@Override
	public double factor(CopNpc cop, Player target) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Records a gunshot by {@code player}: for {@code Shot_Reset_Seconds} his factor is 1. */
	public void shot(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
