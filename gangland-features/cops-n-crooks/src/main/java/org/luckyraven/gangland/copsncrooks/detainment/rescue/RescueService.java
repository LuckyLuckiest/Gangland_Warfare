package org.luckyraven.gangland.copsncrooks.detainment.rescue;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.settings.RescueSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.data.gang.GangMembership;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 0.21.0 rescues: a crew mate holding right-click on the cop guarding a handcuffed player frees him (P-RESC-1..5).
 * TDD skeleton: behaviour arrives with the green chunks.
 *
 * @since 0.21.0
 */
public class RescueService {

	public RescueService(Supplier<RescueSettings> settings, CopManager copManager, DetainmentService detainmentService,
	                     CuffLockRegistry cuffLockRegistry, ReleasePipeline releasePipeline, GangMembership gangs,
	                     WantedStars wantedStars, UserManager<Player> users, WantedMessages messages, JavaPlugin plugin,
	                     Function<UUID, Player> onlinePlayers, LongSupplier clock) {
	}

	/** One right-click by {@code rescuer} on {@code clicked}; main thread. */
	public void onClick(Player rescuer, Entity clicked) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
