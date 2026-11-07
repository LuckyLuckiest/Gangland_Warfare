package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.intake.JailIntakeService;
import org.luckyraven.gangland.copsncrooks.detainment.paperwork.PaperworkItemFactory;
import org.luckyraven.gangland.copsncrooks.detainment.paperwork.PaperworkView;
import org.luckyraven.gangland.copsncrooks.detainment.transit.TransitService;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.mockito.InOrder;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Characterisation of the handlers that block a restrained player outright: the restraint check, then the event is
 * cancelled, then the visuals are ticked, in that order; a player who is not restrained is left alone, and an actor
 * that is not a player is ignored without asking the service anything. The handlers with an extra precondition
 * (swap hand, held slot, command) and the ones with their own logic are not in this table.
 */
@DisplayName("DetainmentListener - the plain block-while-restrained handlers")
class DetainmentListenerRestrainedTest {

	private DetainmentService  detainment;
	private DetainmentListener listener;
	private Player             player;

	/** One handler: the event for a given actor, an event whose actor is no player (null when it takes a Player), the call. */
	private record Case(String name, Function<Player, Cancellable> event, Supplier<Cancellable> nonPlayerEvent,
	                    BiConsumer<DetainmentListener, Cancellable> fire) {

		@Override
		public String toString() {
			return name;
		}
	}

	@BeforeEach
	void setUp() {
		detainment = mock(DetainmentService.class);
		player     = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		listener = new DetainmentListener(detainment, mock(JailService.class), mock(TransitService.class),
		                                  mock(PaperworkItemFactory.class), mock(PaperworkView.class),
		                                  mock(JailIntakeService.class));
	}

	static Stream<Case> cases() {
		return Stream.of(
				new Case("onCraft", p -> {
					CraftItemEvent event = mock(CraftItemEvent.class);
					when(event.getWhoClicked()).thenReturn(p);
					return event;
				}, () -> {
					CraftItemEvent event = mock(CraftItemEvent.class);
					when(event.getWhoClicked()).thenReturn(mock(HumanEntity.class));
					return event;
				}, (l, e) -> l.onCraft((CraftItemEvent) e)),
				new Case("onInteractEntity", p -> {
					PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
					when(event.getPlayer()).thenReturn(p);
					return event;
				}, null, (l, e) -> l.onInteractEntity((PlayerInteractEntityEvent) e)),
				new Case("onInteractAtEntity", p -> {
					PlayerInteractAtEntityEvent event = mock(PlayerInteractAtEntityEvent.class);
					when(event.getPlayer()).thenReturn(p);
					return event;
				}, null, (l, e) -> l.onInteractAtEntity((PlayerInteractAtEntityEvent) e)),
				new Case("onArmorStandManipulate", p -> {
					PlayerArmorStandManipulateEvent event = mock(PlayerArmorStandManipulateEvent.class);
					when(event.getPlayer()).thenReturn(p);
					return event;
				}, null, (l, e) -> l.onArmorStandManipulate((PlayerArmorStandManipulateEvent) e)),
				new Case("onDrop", p -> {
					PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
					when(event.getPlayer()).thenReturn(p);
					return event;
				}, null, (l, e) -> l.onDrop((PlayerDropItemEvent) e)),
				new Case("onPickup", p -> {
					EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
					when(event.getEntity()).thenReturn(p);
					return event;
				}, () -> {
					EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
					when(event.getEntity()).thenReturn(mock(LivingEntity.class));
					return event;
				}, (l, e) -> l.onPickup((EntityPickupItemEvent) e)),
				new Case("onBreak", p -> {
					BlockBreakEvent event = mock(BlockBreakEvent.class);
					when(event.getPlayer()).thenReturn(p);
					return event;
				}, null, (l, e) -> l.onBreak((BlockBreakEvent) e)),
				new Case("onPlace", p -> {
					BlockPlaceEvent event = mock(BlockPlaceEvent.class);
					when(event.getPlayer()).thenReturn(p);
					return event;
				}, null, (l, e) -> l.onPlace((BlockPlaceEvent) e)),
				new Case("onVehicleEnter", p -> {
					VehicleEnterEvent event = mock(VehicleEnterEvent.class);
					when(event.getEntered()).thenReturn(p);
					return event;
				}, () -> {
					VehicleEnterEvent event = mock(VehicleEnterEvent.class);
					when(event.getEntered()).thenReturn(mock(LivingEntity.class));
					return event;
				}, (l, e) -> l.onVehicleEnter((VehicleEnterEvent) e)),
				new Case("onVehicleExit", p -> {
					VehicleExitEvent event = mock(VehicleExitEvent.class);
					when(event.getExited()).thenReturn(p);
					return event;
				}, () -> {
					VehicleExitEvent event = mock(VehicleExitEvent.class);
					when(event.getExited()).thenReturn(mock(LivingEntity.class));
					return event;
				}, (l, e) -> l.onVehicleExit((VehicleExitEvent) e)),
				new Case("onGlide", p -> {
					EntityToggleGlideEvent event = mock(EntityToggleGlideEvent.class);
					when(event.getEntity()).thenReturn(p);
					return event;
				}, () -> {
					EntityToggleGlideEvent event = mock(EntityToggleGlideEvent.class);
					when(event.getEntity()).thenReturn(mock(LivingEntity.class));
					return event;
				}, (l, e) -> l.onGlide((EntityToggleGlideEvent) e)));
	}

	static Stream<Case> guardedCases() {
		return cases().filter(c -> c.nonPlayerEvent() != null);
	}

	@ParameterizedTest(name = "{0}: a restrained player is checked, then cancelled, then the visuals tick")
	@MethodSource("cases")
	@DisplayName("a restrained player's event is cancelled and the visuals tick, in that order")
	void restrained_isCancelled_thenVisualsTick(Case handler) {
		when(detainment.isRestrained(player)).thenReturn(true);
		Cancellable event = handler.event().apply(player);

		handler.fire().accept(listener, event);

		InOrder order = inOrder(detainment, event);
		order.verify(detainment).isRestrained(player);
		order.verify(event).setCancelled(true);
		order.verify(detainment).tickVisuals(player);
	}

	@ParameterizedTest(name = "{0}: a player who is not restrained is left alone")
	@MethodSource("cases")
	@DisplayName("an unrestrained player's event is left alone")
	void notRestrained_isLeftAlone(Case handler) {
		when(detainment.isRestrained(player)).thenReturn(false);
		Cancellable event = handler.event().apply(player);

		handler.fire().accept(listener, event);

		verify(detainment).isRestrained(player);
		verify(event, never()).setCancelled(anyBoolean());
		verify(detainment, never()).tickVisuals(any());
	}

	@ParameterizedTest(name = "{0}: an actor that is no player is ignored")
	@MethodSource("guardedCases")
	@DisplayName("an actor that is not a player is ignored without asking the service")
	void notAPlayer_isIgnored(Case handler) {
		Cancellable event = handler.nonPlayerEvent().get();

		handler.fire().accept(listener, event);

		verifyNoInteractions(detainment);
		verify(event, never()).setCancelled(anyBoolean());
	}
}
