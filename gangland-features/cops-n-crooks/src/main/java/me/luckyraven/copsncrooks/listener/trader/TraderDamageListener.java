package me.luckyraven.copsncrooks.listener.trader;

import com.cryptomorin.xseries.particles.XParticle;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.TraderData;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitProfile;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ListenerHandler
@RequiredArgsConstructor
public class TraderDamageListener implements Listener {

	private static final SoundConfiguration SOUND_HURT    = vanilla("ENTITY_VILLAGER_HURT", 3.8f, 1.0f);
	private static final SoundConfiguration SOUND_ANGERED = vanilla("ENTITY_VILLAGER_NO", 3.0f, 0.8f);

	private static final Particle ANGER_PARTICLE = resolveAngerParticle();

	private final TraderManager         traderManager;
	private final MoodService           moodService;
	private final TraderMessageContract traderMessages;

	// Per (traderId -> attackerId) hit count. Angered cue fires every N hits, where N is the
	// trait's Anger_Hit_Threshold. Bounded by active traders × attackers so left to grow.
	private final Map<UUID, Map<UUID, Integer>> hitCounts = new HashMap<>();

	private static SoundConfiguration vanilla(String name, float volume, float pitch) {
		return new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, name, volume, pitch);
	}

	private static Particle resolveAngerParticle() {
		Particle particle = XParticle.ANGRY_VILLAGER.get();
		if (particle != null) return particle;
		// Pre-1.20.5 Bukkit name.
		try {
			return Particle.valueOf("VILLAGER_ANGRY");
		} catch (IllegalArgumentException ignored) {
			return Particle.valueOf("ANGRY_VILLAGER");
		}
	}

	// ignoreCancelled intentionally false: invulnerable traders produce a cancelled damage event
	// but we still want the anger cue (sound + particles + chat) so the attacker has feedback.
	@EventHandler(priority = EventPriority.MONITOR)
	public void onTraderDamaged(EntityDamageByEntityEvent event) {
		TraderNpc trader = traderManager.getByEntity(event.getEntity());
		if (trader == null) return;

		Entity damager = event.getDamager();
		if (!(damager instanceof Player player)) return;

		TraderTraitDefinition trait = traderManager.resolveTrait(trader.getData());
		if (trait == null) return;

		TraderData         data    = trader.getData();
		TraderTraitProfile profile = trait.profile();

		moodService.recordHit(data.getId(), player.getUniqueId(), profile);

		int count = hitCounts.computeIfAbsent(data.getId(), k -> new HashMap<>())
		                     .merge(player.getUniqueId(), 1, Integer::sum);

		playHitFeedback(trader);

		int threshold = Math.max(1, profile.angerHitThreshold());
		if (count % threshold == 0) {
			playAngeredFeedback(trader, player);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onTraderDeath(EntityDeathEvent event) {
		TraderNpc trader = traderManager.getByEntity(event.getEntity());
		if (trader == null) return;

		UUID traderId = trader.getData().getId();
		hitCounts.remove(traderId);
		traderManager.onTraderKilled(traderId);
	}

	private void playHitFeedback(TraderNpc trader) {
		Entity entity = trader.getNpc().getEntity();
		if (entity == null) return;

		Location loc   = entity.getLocation();
		World    world = loc.getWorld();
		if (world == null) return;

		world.spawnParticle(ANGER_PARTICLE, loc.clone().add(0, 2.0, 0), 6, 0.3, 0.2, 0.3, 0);
		SOUND_HURT.playAtLocation(loc);
	}

	private void playAngeredFeedback(TraderNpc trader, Player attacker) {
		Entity entity = trader.getNpc().getEntity();
		if (entity != null) {
			SOUND_ANGERED.playAtLocation(entity.getLocation());
		}

		String name = trader.getData().getDisplayName();
		attacker.sendMessage(traderMessages.angered(name != null ? name : "Trader"));
	}

}
