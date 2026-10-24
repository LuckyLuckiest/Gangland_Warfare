package org.luckyraven.gangland.copsncrooks.detainment.surrender;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.detainment.settings.SurrenderSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 0.21.0 Surrender: a crouching, empty-handed suspect who is seen at 1 to 3 stars makes the squad hold fire; standing
 * up ends it, a hit inside the resist window is Resisting_Arrest.
 *
 * @since 0.21.0
 */
@CustomLog
public class SurrenderService {

	private enum Reason {
		GONE, CUFFED, STOOD_UP, RESISTED
	}

	private final Supplier<SurrenderSettings> settings;
	private final CopRadio                    copRadio;
	private final WantedMessages              messages;
	private final CrimeService                crimes;
	private final LongSupplier                clock;
	private final Predicate<Player>           restrained;

	private final Map<UUID, Long> surrenderedAt = new HashMap<>();
	private final Map<UUID, CopGroup> surrenderedTo = new HashMap<>();
	private final Set<UUID>           resisted      = new HashSet<>();
	private final Set<UUID>           toldTooHot    = new HashSet<>();

	public SurrenderService(Supplier<SurrenderSettings> settings, CopRadio copRadio, WantedMessages messages,
	                        CrimeService crimes, LongSupplier clock, Predicate<Player> restrained) {
		this.settings   = settings;
		this.copRadio   = copRadio;
		this.messages   = messages;
		this.crimes     = crimes;
		this.clock      = clock;
		this.restrained = restrained;
	}

	/** AI tick hook: starts, keeps or ends the surrender of {@code player}. */
	public void tick(Player player, @Nullable CopGroup group) {
		UUID id = player.getUniqueId();
		SurrenderSettings config = settings.get();
		if (!config.enabled()) {
			endSurrender(player, group, Reason.GONE);
			return;
		}
		if (restrained.test(player)) {
			endSurrender(player, group, Reason.CUFFED);
			return;
		}
		if (group == null) {
			endSurrender(player, null, Reason.GONE);
			resisted.remove(id);
			toldTooHot.remove(id);
			return;
		}
		int level = group.getLevel();
		if (level <= 0) {
			endSurrender(player, group, Reason.GONE);
			resisted.remove(id);
			toldTooHot.remove(id);
			return;
		}
		if (!poseHeld(player)) {
			endSurrender(player, group, Reason.STOOD_UP);
			resisted.remove(id);
			return;
		}
		if (level > config.maxStars()) {
			if (surrenderedAt.remove(id) != null) {
				group.setCeaseFire(false);
				log.debug("SURRENDER {} end reason={}", player.getName(), Reason.GONE);
			}
			if (toldTooHot.add(id)) player.sendMessage(messages.format(WantedMessages.Key.SURRENDER_TOO_HOT, Map.of()));
			return;
		}
		if (resisted.contains(id) || surrenderedAt.containsKey(id) || !group.getSquad().hasFreshSighting()) return;

		surrenderedAt.put(id, clock.getAsLong());
		surrenderedTo.put(id, group);
		if (id.equals(group.getTargetPlayerId())) group.setCeaseFire(true);
		copRadio.sayFromLeader(group, "Surrender_Accepted");
		player.sendMessage(messages.format(WantedMessages.Key.SURRENDER_ACCEPTED, Map.of()));
		log.debug("SURRENDER {} start level={}", player.getName(), level);
	}

	/** Cop-attacked hook: a hit by a surrendered suspect; runs before the manager escalates the group. */
	public void onCopAttacked(CopNpc cop, Player attacker) {
		UUID id = attacker.getUniqueId();
		Long since = surrenderedAt.get(id);
		if (since == null) return;
		if (clock.getAsLong() - since <= settings.get().resistWindowSeconds() * 1000L) {
			crimes.commit(attacker, Crimes.RESISTING_ARREST, attacker.getLocation());
			attacker.sendMessage(messages.format(WantedMessages.Key.SURRENDER_RESISTED, Map.of()));
		}
		resisted.add(id);
		endSurrender(attacker, surrenderedTo.get(id), Reason.RESISTED);
	}

	/** Quit hook: drops everything remembered about {@code id}. */
	public void forget(UUID id) {
		surrenderedAt.remove(id);
		surrenderedTo.remove(id);
		resisted.remove(id);
		toldTooHot.remove(id);
	}

	private void endSurrender(Player player, @Nullable CopGroup group, Reason reason) {
		UUID id = player.getUniqueId();
		surrenderedTo.remove(id);
		if (surrenderedAt.remove(id) == null) return;
		if (group != null && id.equals(group.getTargetPlayerId())) group.setCeaseFire(false);
		log.debug("SURRENDER {} end reason={}", player.getName(), reason);
	}

	public static boolean poseHeld(Player player) {
		if (!player.isSneaking()) return false;
		ItemStack main = player.getInventory().getItemInMainHand();
		ItemStack off = player.getInventory().getItemInOffHand();
		return (empty(main) || main.getType() == XMaterial.WHITE_BANNER.get()) && empty(off);
	}

	private static boolean empty(@Nullable ItemStack stack) {
		return stack == null || stack.getType().isAir();
	}
}
