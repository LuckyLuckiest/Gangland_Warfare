package org.luckyraven.gangland.gadget.jetpack;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.gadget.grapple.GrappleService;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.gadget.jetpack.packet.JetpackInputInterceptor;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.keystone.nms.input.PlayerInputInterceptor;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterises the teardown shared by {@code JetpackService.deactivate} and {@code deactivateAll} (cancel the task
 * unless it is null or already cancelled; for an online player clear flying, restore the pre-activation allowFlight
 * and remove the input interceptor when one is installed) before the two copies become one method.
 */
@DisplayName("JetpackService — deactivate / deactivateAll teardown")
class JetpackServiceTeardownCharacterisationTest {

	private BukkitStatics                    bukkit;
	private MockedStatic<PlayerInputInterceptor> interceptor;

	@AfterEach
	void tearDown() {
		if (interceptor != null) interceptor.close();
		if (bukkit != null) bukkit.close();
	}

	private JetpackService service() {
		bukkit = BukkitStatics.install();
		when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));
		interceptor = Mockito.mockStatic(PlayerInputInterceptor.class);

		return new JetpackService(mock(FuelService.class), mock(JavaPlugin.class), mock(GadgetPhysicsConfig.class),
		                          mock(JetpackAddon.class), mock(GrappleService.class));
	}

	private static Player player(Jetpack jetpack, boolean online, boolean allowFlight) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(jetpack.getPermission())).thenReturn(true);
		when(player.isOnline()).thenReturn(online);
		when(player.getAllowFlight()).thenReturn(allowFlight);
		return player;
	}

	private static Jetpack jetpack() {
		return Jetpack.builder().jetpackId("jp").build();
	}

	private void channelFor(Player player, Channel channel) {
		interceptor.when(() -> PlayerInputInterceptor.getChannel(player)).thenReturn(channel);
	}

	private static Channel channelWithHandler(ChannelPipeline pipeline, boolean hasHandler) {
		Channel channel = mock(Channel.class);
		when(channel.pipeline()).thenReturn(pipeline);
		when(pipeline.get(JetpackInputInterceptor.HANDLER_NAME)).thenReturn(hasHandler ? mock(
				io.netty.channel.ChannelHandler.class) : null);
		return channel;
	}

	@Test
	@DisplayName("deactivate: online player — task cancelled, flying cleared, allowFlight restored, interceptor removed")
	void deactivate_online_fullTeardownInOrder() {
		JetpackService  service  = service();
		Jetpack         jetpack  = jetpack();
		Player          player   = player(jetpack, true, true);
		ChannelPipeline pipeline = mock(ChannelPipeline.class);
		channelFor(player, channelWithHandler(pipeline, true));

		service.activate(player, jetpack);
		verify(player).setAllowFlight(true);
		service.deactivate(player);

		verify(bukkit.scheduler(), times(1)).cancelTask(anyInt());
		InOrder order = inOrder(player, pipeline);
		order.verify(player).setFlying(false);
		order.verify(player).setAllowFlight(true);
		order.verify(pipeline).remove(JetpackInputInterceptor.HANDLER_NAME);
		assertFalse(service.isActive(player));
	}

	@Test
	@DisplayName("deactivateAll: online player — same teardown, and the session map is emptied")
	void deactivateAll_online_fullTeardownInOrder() {
		JetpackService  service  = service();
		Jetpack         jetpack  = jetpack();
		Player          player   = player(jetpack, true, true);
		ChannelPipeline pipeline = mock(ChannelPipeline.class);
		channelFor(player, channelWithHandler(pipeline, true));

		service.activate(player, jetpack);
		service.deactivateAll();

		verify(bukkit.scheduler(), times(1)).cancelTask(anyInt());
		InOrder order = inOrder(player, pipeline);
		order.verify(player).setFlying(false);
		order.verify(player).setAllowFlight(true);
		order.verify(pipeline).remove(JetpackInputInterceptor.HANDLER_NAME);
		assertFalse(service.isActive(player));
	}

	@Test
	@DisplayName("deactivate / deactivateAll: allowFlight goes back to the captured false, not a hardcoded true")
	void restoresCapturedAllowFlightFalse() {
		JetpackService service = service();
		Jetpack        jetpack = jetpack();
		Player         first   = player(jetpack, true, false);
		Player         second  = player(jetpack, true, false);

		service.activate(first, jetpack);
		service.activate(second, jetpack);
		service.deactivate(first);
		service.deactivateAll();

		InOrder order = inOrder(first);
		order.verify(first).setAllowFlight(true);
		order.verify(first).setFlying(false);
		order.verify(first).setAllowFlight(false);
		verify(second).setFlying(false);
		verify(second).setAllowFlight(false);
		assertFalse(service.isActive(second));
	}

	@Test
	@DisplayName("deactivate / deactivateAll: an offline player gets the task cancelled and nothing else")
	void offlinePlayer_onlyTaskCancelled() {
		JetpackService service = service();
		Jetpack        jetpack = jetpack();
		Player         left    = player(jetpack, false, true);
		Player         left2   = player(jetpack, false, true);

		service.activate(left, jetpack);
		service.activate(left2, jetpack);
		service.deactivate(left);
		service.deactivateAll();

		verify(left, never()).setFlying(false);
		verify(left2, never()).setFlying(false);
		verify(left, times(1)).setAllowFlight(true);
		verify(left2, times(1)).setAllowFlight(true);
		verify(bukkit.scheduler(), times(2)).cancelTask(anyInt());
		assertFalse(service.isActive(left));
		assertFalse(service.isActive(left2));
	}

	@Test
	@DisplayName("deactivate / deactivateAll: no channel, or a channel without the handler, removes nothing and does not throw")
	void noChannelOrNoHandler_noRemove() {
		JetpackService  service  = service();
		Jetpack         jetpack  = jetpack();
		Player          noChan   = player(jetpack, true, true);
		Player          noHandle = player(jetpack, true, true);
		ChannelPipeline pipeline = mock(ChannelPipeline.class);
		channelFor(noChan, null);
		channelFor(noHandle, channelWithHandler(pipeline, false));

		service.activate(noChan, jetpack);
		service.activate(noHandle, jetpack);
		service.deactivate(noChan);
		service.deactivateAll();

		verify(noChan).setFlying(false);
		verify(noHandle).setFlying(false);
		verify(pipeline, never()).remove(any(String.class));
	}

	@Test
	@DisplayName("deactivate / deactivateAll: a null or already-cancelled task is not cancelled again")
	void nullOrCancelledTask_notCancelled() {
		JetpackService service = service();
		Jetpack        jetpack = jetpack();
		Player         nullTask      = player(jetpack, true, true);
		Player         cancelledTask = player(jetpack, true, true);

		service.activate(nullTask, jetpack);
		service.activate(cancelledTask, jetpack);
		service.getSession(nullTask).setTask(null);
		JetpackTask done = mock(JetpackTask.class);
		when(done.isCancelled()).thenReturn(true);
		service.getSession(cancelledTask).setTask(done);

		service.deactivate(nullTask);
		service.deactivateAll();

		verify(done, never()).cancel();
		verify(bukkit.scheduler(), never()).cancelTask(anyInt());
		verify(nullTask).setFlying(false);
		verify(cancelledTask).setFlying(false);
		assertNull(service.getSession(nullTask));
	}

	@Test
	@DisplayName("deactivate: an unknown player touches nothing")
	void deactivate_unknownPlayer_noop() {
		JetpackService service = service();
		Player         stranger = mock(Player.class);
		when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());

		service.deactivate(stranger);

		verify(stranger, never()).setFlying(false);
		verify(stranger, never()).setAllowFlight(false);
	}

	private static int anyInt() {
		return org.mockito.ArgumentMatchers.anyInt();
	}
}
