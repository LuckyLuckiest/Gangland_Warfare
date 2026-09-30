package org.luckyraven.gangland.copsncrooks.npc.police;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio.RadioCall;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.TargetingManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.npc.spi.NpcSquadListener;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A {@link CopManager} over mocks: a scripted spawner and config, a mock {@link CopRadio} whose listener records the
 * calls it would queue, a controllable clock, and stateful cop mocks (state, group and targets are remembered).
 */
final class CopManagerFixture implements AutoCloseable {

	final BukkitStatics     bukkit;
	final World             world    = mock(World.class);
	final CopConfigProvider provider = mock(CopConfigProvider.class);
	final CopSpawnManager   spawner  = mock(CopSpawnManager.class);
	final TargetingManager  targeting = mock(TargetingManager.class);
	final DetainmentService detainment = mock(DetainmentService.class);
	final CopRadio          radio    = mock(CopRadio.class);
	final CopManager        manager;
	final long[]            clock    = {1_000L};
	final int[]             tier     = {3};

	/** The call queue each group's radio listener feeds, by group. */
	final Map<CopGroup, Consumer<RadioCall>> callSinks = new HashMap<>();
	final Map<CopGroup, NpcSquadListener>    listeners = new HashMap<>();

	CopManagerFixture() {
		bukkit = BukkitStatics.install();
		when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));

		when(provider.getMaxCopsPerPlayer()).thenReturn(6);
		when(provider.getPursuitMaxDistance()).thenReturn(80.0);
		when(provider.getBackupSettings()).thenReturn(new BackupSettings(true, 1, 30_000, 60_000));
		when(provider.getRadioSettings()).thenReturn(CopConfigProvider.COP_RADIO_DEFAULTS);
		when(provider.getFieldCareSettings()).thenReturn(org.luckyraven.gangland.npc.FieldCareSettings.DEFAULT);
		when(provider.getAiTickRate()).thenReturn(10);
		CopTierConfig tierConfig = mock(CopTierConfig.class);
		when(tierConfig.displayName()).thenReturn("SWAT");
		when(tierConfig.tactics()).thenReturn(new TacticsConfig(TacticsConfig.DEFAULT.engagement(), 270.0));
		when(provider.getTierConfig(anyInt())).thenReturn(tierConfig);

		when(spawner.getTargetCopCount(anyInt())).thenReturn(2);
		when(spawner.getTierForWantedLevel(anyInt())).thenAnswer(inv -> tier[0]);
		when(spawner.spawnNearPlayer(any(), anyInt(), any())).thenAnswer(inv -> cop(CopState.IDLE, 0, 0));

		when(radio.now()).thenAnswer(inv -> clock[0]);
		when(radio.listenerFor(any(), any())).thenAnswer(inv -> {
			CopGroup         group    = inv.getArgument(0);
			NpcSquadListener listener = mock(NpcSquadListener.class);
			callSinks.put(group, inv.getArgument(1));
			listeners.put(group, listener);
			return listener;
		});

		CopLoader loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(provider);
		manager = new CopManager(mock(JavaPlugin.class), spawner, targeting, loader, mock(NpcMarkManager.class),
		                         detainment, mock(CivilianNpcRegistry.class), radio);
	}

	/** An online player at (x, 64, z), wanted as far as the targeting manager is concerned. */
	Player player(double x, double z) {
		UUID   id     = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(player.isOnline()).thenReturn(true);
		when(player.isValid()).thenReturn(true);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, x, 64, z));
		when(targeting.isWanted(id)).thenReturn(true);
		bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(player);
		return player;
	}

	static Wanted wanted(int level) {
		Wanted wanted = mock(Wanted.class);
		when(wanted.isWanted()).thenReturn(true);
		when(wanted.getLevel()).thenReturn(level);
		return wanted;
	}

	/** A valid cop at (x, 64, z) whose state, group, targets and combat flag behave like the real fields. */
	CopNpc cop(CopState initial, double x, double z) {
		CopNpc                      cop          = mock(CopNpc.class);
		AtomicReference<CopState>     state        = new AtomicReference<>(initial);
		AtomicReference<CopGroup>     group        = new AtomicReference<>();
		AtomicReference<UUID>         targetPlayer = new AtomicReference<>();
		AtomicReference<LivingEntity> targetEntity = new AtomicReference<>();
		AtomicBoolean                 forced       = new AtomicBoolean();

		when(cop.getCurrentState()).thenAnswer(inv -> state.get());
		doAnswer(inv -> {
			state.set(inv.getArgument(0));
			return null;
		}).when(cop).transitionTo(any());
		when(cop.getGroup()).thenAnswer(inv -> group.get());
		doAnswer(inv -> {
			group.set(inv.getArgument(0));
			return null;
		}).when(cop).setGroup(any());
		when(cop.getTargetPlayerId()).thenAnswer(inv -> targetPlayer.get());
		doAnswer(inv -> {
			targetPlayer.set(inv.getArgument(0));
			return null;
		}).when(cop).setTargetPlayerId(any());
		when(cop.getTargetEntity()).thenAnswer(inv -> targetEntity.get());
		doAnswer(inv -> {
			targetEntity.set(inv.getArgument(0));
			return null;
		}).when(cop).setTargetEntity(any());
		when(cop.isCombatForced()).thenAnswer(inv -> forced.get());
		doAnswer(inv -> {
			forced.set(inv.getArgument(0));
			return null;
		}).when(cop).setCombatForced(anyBoolean());

		LivingEntity body = mock(LivingEntity.class);
		when(body.getUniqueId()).thenReturn(UUID.randomUUID());
		when(body.getWorld()).thenReturn(world);
		when(body.getLocation()).thenReturn(new Location(world, x, 64, z));
		NPC npc = mock(NPC.class);
		when(npc.getEntity()).thenReturn(body);
		when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
		when(cop.getNpc()).thenReturn(npc);
		when(cop.getEntity()).thenReturn(body);
		when(cop.isValid()).thenReturn(true);
		return cop;
	}

	@Override
	public void close() {
		bukkit.close();
	}
}
