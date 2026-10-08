package org.luckyraven.gangland.turf.command;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins TF-04: {@code /glw turf setowner} during a capture cancels it (boss bars and guards clean up), and an admin owner
 * change is a clean slate (cooldown wiped). Drives {@link TurfSetOwnerCommand#apply} directly because the gang
 * argument's action lives on a nested {@code OptionalArgument}, which a test cannot reach through the root command.
 */
@DisplayName("TurfSetOwnerCommand - admin owner change cancels a capture and wipes the cooldown (TF-04)")
class TurfSetOwnerCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private TurfRepositoryContract repository;
	private TurfManager            turfs;
	private WandSelectionManager   selections;
	private TurfMessageContract    messages;
	private FakeGangLookup         gangs;
	private TurfSetOwnerCommand    command;
	private Turf                   turf;
	private Player                 admin;

	@BeforeEach
	void setUp() {
		repository = mock(TurfRepositoryContract.class);
		turfs      = new TurfManager(repository);
		selections = new WandSelectionManager();
		messages   = mock(TurfMessageContract.class);
		gangs      = new FakeGangLookup();

		JavaPlugin     plugin = PluginMocks.plugin(tempDir);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			command = new TurfSetOwnerCommand(plugin, tree, parent, turfs, gangs, selections, messages,
			                                  new TurfOwnership(turfs));
		}

		turf = new Turf(5, "Dup", new CuboidRegion("world", 0, 0, 10, 10), 1, BigDecimal.TEN, 0L, 0L);
		turfs.create(turf);

		admin = mock(Player.class);
		when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
		selections.get(admin).setActiveTurfId(5);
	}

	@Test
	@DisplayName("setowner none during a contest fires TurfCaptureFailedEvent(CANCELLED) then TurfOwnerChangedEvent(ADMIN)")
	void setOwnerNone_duringContest_firesCancelledThenOwnerChanged() {
		TurfRuntimeState state = turfs.getRuntimeState(5);
		state.setState(TurfState.CONTESTING);
		state.setChallengerGangId(2);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			command.apply(admin, "none");

			assertNull(turf.getOwnerGangId());
			assertEquals(TurfState.IDLE, state.getState());
			assertEquals(0L, turf.getLastCaptureTimestamp());
			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), times(2)).callEvent(captor.capture());
			List<Event> events = captor.getAllValues();
			TurfCaptureFailedEvent failed = (TurfCaptureFailedEvent) events.get(0);
			assertEquals(TurfCaptureFailedEvent.Reason.CANCELLED, failed.getReason());
			TurfOwnerChangedEvent changed = (TurfOwnerChangedEvent) events.get(1);
			assertEquals(TurfOwnerChangedEvent.Cause.ADMIN, changed.getCause());
			assertNull(changed.getNewOwnerGangId());
		}
	}

	@Test
	@DisplayName("setowner <gang> writes the owner, persists it, and clears the cooldown anchor and runtime state")
	void setOwnerGang_writesOwnerAndClearsCooldown() {
		Gang target = mock(Gang.class);
		when(target.getId()).thenReturn(7);
		when(target.getName()).thenReturn("Foo");
		gangs.register(target);
		turf.setOwnerGangId(null);
		turf.setLastCaptureTimestamp(System.currentTimeMillis());

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			command.apply(admin, "foo");

			assertEquals(7, turf.getOwnerGangId());
			assertEquals(0L, turf.getLastCaptureTimestamp());
			assertEquals(TurfState.IDLE, turfs.getRuntimeState(5).getState());
			verify(repository, atLeastOnce()).save(turf);
			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), times(1)).callEvent(captor.capture());
			TurfOwnerChangedEvent changed = (TurfOwnerChangedEvent) captor.getValue();
			assertEquals(7, changed.getNewOwnerGangId());
			assertEquals(TurfOwnerChangedEvent.Cause.ADMIN, changed.getCause());
		}
	}
}
