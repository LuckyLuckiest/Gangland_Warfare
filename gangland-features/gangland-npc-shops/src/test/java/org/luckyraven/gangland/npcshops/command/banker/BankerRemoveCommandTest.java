package org.luckyraven.gangland.npcshops.command.banker;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.npcshops.banker.BankerData;
import org.luckyraven.gangland.npcshops.banker.BankerManager;
import org.luckyraven.gangland.npcshops.banker.BankerNpc;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.datastructure.Tree;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Characterises the ray trace {@link BankerRemoveCommand} uses to find the banker the player is looking at. */
@DisplayName("BankerRemoveCommand - targeted banker")
class BankerRemoveCommandTest {

	private final World          world         = mock(World.class);
	private final Player         player        = mock(Player.class);
	private final Location       eye           = new Location(world, 1, 65, 2, 45f, -5f);
	private final BankerManager  bankerManager = mock(BankerManager.class);

	private BukkitStatics bukkit;

	@BeforeEach
	void installBukkit() {
		bukkit = BukkitStatics.install();
	}

	@AfterEach
	void removeBukkit() {
		bukkit.close();
	}

	@BeforeAll
	static void primeMessages() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		field.set(null, "$");
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return path + " %range%";
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
	}

	@SuppressWarnings("unchecked")
	private BankerRemoveCommand command() {
		return new BankerRemoveCommand(mock(JavaPlugin.class), mock(Tree.class), mock(Argument.class), bankerManager);
	}

	private void run() {
		when(player.getEyeLocation()).thenReturn(eye);
		when(player.getWorld()).thenReturn(world);
		command().action().accept(mock(Argument.class), player, new String[0]);
	}

	private void traceHits(Entity entity) {
		RayTraceResult result = mock(RayTraceResult.class);
		when(result.getHitEntity()).thenReturn(entity);
		when(world.rayTraceEntities(any(Location.class), any(Vector.class), anyDouble(), any())).thenReturn(result);
	}

	@Test
	@DisplayName("it traces 5 blocks from the eye along its direction and never targets the player")
	void trace_usesEyeDirectionRangeAndSelfFilter() {
		traceHits(null);

		run();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Predicate<Entity>> filter = ArgumentCaptor.forClass(Predicate.class);
		verify(world).rayTraceEntities(eq(eye), eq(eye.getDirection()), eq(5D), filter.capture());
		assertFalse(filter.getValue().test(player));
		assertTrue(filter.getValue().test(mock(Entity.class)));
	}

	@Test
	@DisplayName("no result and a result without an entity both tell the player to look at a banker")
	void noTarget_sendsLookAt() {
		run();
		verify(player).sendMessage(contains(Messages.BANKER_LOOK_AT.getPath()));

		traceHits(null);
		run();
		verify(bankerManager, never()).getByEntity(any());
	}

	@Test
	@DisplayName("a target that is not a banker is reported and nothing is removed")
	void notABanker() {
		Entity entity = mock(Entity.class);
		traceHits(entity);
		when(bankerManager.getByEntity(entity)).thenReturn(null);

		run();

		verify(player).sendMessage(contains(Messages.BANKER_NOT_A_BANKER.getPath()));
		verify(bankerManager, never()).remove(any());
	}

	@Test
	@DisplayName("a targeted banker is removed by its id")
	void removesTheBanker() {
		Entity     entity = mock(Entity.class);
		BankerData data   = new BankerData(UUID.randomUUID(), new Location(world, 0, 64, 0), null);
		BankerNpc  banker = new BankerNpc(data, null);
		traceHits(entity);
		when(bankerManager.getByEntity(entity)).thenReturn(banker);

		run();

		verify(bankerManager).remove(data.getId());
		verify(player).sendMessage(contains(Messages.BANKER_REMOVED.getPath()));
	}
}
