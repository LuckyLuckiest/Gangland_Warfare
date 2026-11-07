package org.luckyraven.gangland.copsncrooks.detainment.release;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentState;
import org.luckyraven.gangland.copsncrooks.detainment.inventory.SeizedInventoryService;
import org.luckyraven.gangland.copsncrooks.detainment.paperwork.PaperworkItemFactory;
import org.luckyraven.gangland.copsncrooks.detainment.transit.TransitService;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.mockito.InOrder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Characterisation of {@link ReleasePipeline#release}: the one funnel every exit from detainment runs through. The
 * seized inventory is restored for a JAILED player without asking whether one was taken, and for any other state only
 * when the service says one was; the sequence is cancel transit, restore, strip paperwork, release, reset the kill
 * combo, teleport to the jail's exit.
 */
@DisplayName("ReleasePipeline - the single release funnel")
class ReleasePipelineTest {

	private final UUID id = UUID.randomUUID();

	private DetainmentService      detainment;
	private DetainmentRegistry     registry;
	private SeizedInventoryService seized;
	private TransitService         transit;
	private PaperworkItemFactory   paperwork;
	private ReleaseExitContract    exits;
	private KillCombo              killCombo;
	private Player                 player;
	private PlayerInventory        inventory;
	private ReleasePipeline        pipeline;

	@BeforeEach
	void setUp() {
		detainment = mock(DetainmentService.class);
		registry   = new DetainmentRegistry(mock(IRepository.class), new JailRegistry());
		seized     = mock(SeizedInventoryService.class);
		transit    = mock(TransitService.class);
		paperwork  = mock(PaperworkItemFactory.class);
		exits      = mock(ReleaseExitContract.class);
		killCombo  = mock(KillCombo.class);
		player     = mock(Player.class);
		inventory  = mock(PlayerInventory.class);
		when(player.getUniqueId()).thenReturn(id);
		when(player.getName()).thenReturn("Inmate");
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getContents()).thenReturn(new ItemStack[0]);
		pipeline = new ReleasePipeline(detainment, registry, seized, transit, paperwork, exits, killCombo);
	}

	@Test
	@DisplayName("a JAILED player gets his items back without the service being asked whether any were seized")
	void jailed_restoresWithoutAsking() {
		when(detainment.getState(player)).thenReturn(DetainmentState.JAILED);

		pipeline.release(player, ReleaseReason.BAIL);

		verify(seized).restore(player);
		verify(seized, never()).has(any());
	}

	@Test
	@DisplayName("a handcuffed player is restored only when something was seized from him")
	void handcuffed_restoresOnlyWhenSeized() {
		when(detainment.getState(player)).thenReturn(DetainmentState.HANDCUFFED);
		when(seized.has(id)).thenReturn(true);

		pipeline.release(player, ReleaseReason.HANDCUFF_BRIBE);

		verify(seized).has(id);
		verify(seized).restore(player);
	}

	@Test
	@DisplayName("a handcuffed player with nothing seized is not restored")
	void handcuffed_nothingSeized_noRestore() {
		when(detainment.getState(player)).thenReturn(DetainmentState.HANDCUFFED);
		when(seized.has(id)).thenReturn(false);

		pipeline.release(player, ReleaseReason.HANDCUFF_BRIBE);

		verify(seized).has(id);
		verify(seized, never()).restore(any());
		verify(detainment).release(player);
	}

	@Test
	@DisplayName("a player in no state is treated like a handcuffed one: restored only when something was seized")
	void noState_restoresOnlyWhenSeized() {
		when(detainment.getState(player)).thenReturn(null);
		when(seized.has(id)).thenReturn(true);

		pipeline.release(player, ReleaseReason.BREAK_FREE);

		verify(seized).has(id);
		verify(seized).restore(player);
	}

	@Test
	@DisplayName("the steps run in order and the player is teleported to his jail's exit")
	void sequence_endsAtTheJailExit() {
		Location exit = mock(Location.class);
		registry.getDetainedPlayers().put(id, new DetainedPlayer(id, 7, DetainmentState.JAILED));
		when(detainment.getState(player)).thenReturn(DetainmentState.JAILED);
		when(exits.resolveExit(7)).thenReturn(exit);

		pipeline.release(player, ReleaseReason.SENTENCE_COMPLETE);

		InOrder order = inOrder(transit, seized, detainment, killCombo, exits, player);
		order.verify(transit).cancel(player);
		order.verify(seized).restore(player);
		order.verify(detainment).release(player);
		order.verify(killCombo).resetCombo(id);
		order.verify(exits).resolveExit(7);
		order.verify(player).teleport(exit);
	}

	@Test
	@DisplayName("a player with no detained row is not teleported")
	void noRow_noTeleport() {
		when(detainment.getState(player)).thenReturn(DetainmentState.NORMAL);

		pipeline.release(player, ReleaseReason.BAIL);

		verify(detainment).release(player);
		verifyNoInteractions(exits);
		verify(player, never()).teleport(any(Location.class));
	}

	@Test
	@DisplayName("a null player does nothing")
	void nullPlayer_doesNothing() {
		pipeline.release(null, ReleaseReason.BAIL);

		verifyNoInteractions(detainment, seized, transit, killCombo, exits);
	}
}
