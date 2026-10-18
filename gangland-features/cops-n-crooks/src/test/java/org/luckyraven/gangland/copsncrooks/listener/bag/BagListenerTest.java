package org.luckyraven.gangland.copsncrooks.listener.bag;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.bag.BagEntry;
import org.luckyraven.gangland.copsncrooks.bag.BagFixture;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.events.user.UserDataInitEvent;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.luckyraven.gangland.copsncrooks.bag.BagFixture.bd;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lane bag: {@link BagListener}. Endings, quit, the join settle after the async star restore (D-W2-31) and the hot
 * bill star (P-BAG-7, 8, 9).
 */
@DisplayName("BagListener - endings, quit, join settle, hot pickup")
class BagListenerTest {

	@TempDir
	Path tempDir;

	private BagFixture  fx;
	private Player      runner;
	private BagListener listener;

	@BeforeEach
	void setUp() {
		fx       = new BagFixture(tempDir);
		runner   = fx.player("Runner");
		listener = new BagListener(fx.service, fx);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private UserDataInitEvent userData(boolean async) {
		User user = mock(User.class);
		when(user.getUser()).thenReturn(runner);
		return new UserDataInitEvent(async, user);
	}

	private void seedSaved(String amount) {
		fx.bags.put(new BagEntry(runner.getUniqueId(), bd(amount), bd(amount), 0L, BigDecimal.ZERO, BigDecimal.ZERO));
	}

	private void settleNow() {
		listener.onUserData(userData(true));
		fx.runTasks();
	}

	private static List<Method> handlersTaking(Class<?> type) {
		return Arrays.stream(BagListener.class.getDeclaredMethods())
		             .filter(m -> m.isAnnotationPresent(EventHandler.class))
		             .filter(m -> m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == type).toList();
	}

	@Test
	@DisplayName("D-W2-28: the WantedEndEvent handler runs at HIGH")
	void wantedEndHandlerIsHighPriority() {
		List<Method> handlers = handlersTaking(WantedEndEvent.class);

		assertEquals(1, handlers.size());
		assertEquals(EventPriority.HIGH, handlers.get(0).getAnnotation(EventHandler.class).priority());
	}

	@Test
	@DisplayName("D-W2-25: a downed reset (cause DEATH, player alive and free) spills the bag once where he stands")
	void downedResetSpills() {
		fx.level(runner, 2);
		fx.service.take(runner, bd("1000"), "pickup");

		listener.onWantedEnd(new WantedEndEvent(runner, mock(Wanted.class), WantedCause.DEATH));

		assertEquals(1, fx.spills.size());
		assertEquals(0, bd("1000").compareTo(fx.spills.get(0).amount()));
		assertEquals(0, bd("0").compareTo(fx.bag(runner)));
	}

	@Test
	@DisplayName("P-BAG-4: an EVASION end banks the bag through the listener")
	void evasionEndBanks() {
		fx.level(runner, 2);
		fx.service.take(runner, bd("1000"), "pickup");

		listener.onWantedEnd(new WantedEndEvent(runner, mock(Wanted.class), WantedCause.EVASION));

		assertEquals(0, bd("1000").compareTo(fx.walletOf(runner)));
	}

	@Test
	@DisplayName("P-BAG-7, 9: a settled player who quits with a bag spills it; during shutdown nothing spills and the row keeps it")
	void quitSpillsUnlessShuttingDown() {
		settleNow();
		fx.level(runner, 2);
		fx.service.take(runner, bd("500"), "pickup");

		listener.onQuit(new PlayerQuitEvent(runner, "bye"));

		assertEquals(1, fx.spills.size());
		assertTrue(fx.debug.contains("BAG Runner quit outcome=spilled bag=500"), fx.debug.toString());

		Player second = fx.player("Second");
		fx.level(second, 2);
		fx.service.take(second, bd("500"), "pickup");
		fx.service.settle(second);
		fx.service.shutdown();

		listener.onQuit(new PlayerQuitEvent(second, "bye"));

		assertEquals(1, fx.spills.size(), "no new spill while the server stops");
		assertEquals(0, bd("500").compareTo(fx.bag(second)));
	}

	@Test
	@DisplayName("D-W2-08: a restrained player who quits has the bag seized, not spilled")
	void quitWhileRestrainedSeizes() {
		settleNow();
		fx.level(runner, 2);
		fx.service.take(runner, bd("500"), "pickup");
		fx.restrained.add(runner.getUniqueId());

		listener.onQuit(new PlayerQuitEvent(runner, "bye"));

		assertTrue(fx.spills.isEmpty());
		assertEquals(0, bd("500").compareTo(fx.bags.seized(runner.getUniqueId())));
		assertTrue(fx.debug.contains("BAG Runner quit outcome=seized bag=500"), fx.debug.toString());
	}

	@Test
	@DisplayName("P-BAG-9, D-W2-09: the settle restores a saved bag when he is wanted again, converts it when he is not")
	void joinRestoresOrConverts() {
		seedSaved("500");
		fx.level(runner, 2);

		settleNow();

		assertEquals(0, bd("500").compareTo(fx.bag(runner)));
		assertTrue(fx.debug.contains("BAG Runner restored bag=500"), fx.debug.toString());
		assertEquals(0, bd("500").compareTo(fx.huds.get(fx.huds.size() - 1)));

		Player other = fx.player("Other");
		fx.bags.put(new BagEntry(other.getUniqueId(), bd("500"), bd("500"), 0L, BigDecimal.ZERO, BigDecimal.ZERO));
		fx.level(other, 0);
		fx.service.settle(other);

		assertEquals(0, bd("325").compareTo(fx.walletOf(other)));
		assertEquals(0, bd("0").compareTo(fx.bags.amount(other.getUniqueId())));
	}

	@Test
	@DisplayName("D-W2-31: the star restore is queued first; the bag settles on the hop behind it and sees the restored stars")
	void asyncRestoreOrderKeepsBag() {
		seedSaved("500");
		fx.tasks.add(() -> fx.level(runner, 2)); // UserDataLoader's WantedStars.restore runTask

		listener.onUserData(userData(true));

		assertEquals(0, bd("500").compareTo(fx.bag(runner)), "nothing settles on the event thread");
		assertTrue(fx.debug.isEmpty());
		assertEquals(0, bd("0").compareTo(fx.walletOf(runner)));

		fx.runTasks();

		assertTrue(fx.debug.contains("BAG Runner restored bag=500"), fx.debug.toString());
		assertEquals(0, bd("0").compareTo(fx.walletOf(runner)), "a restored bag is not converted");
		assertEquals(0, bd("500").compareTo(fx.bag(runner)));
	}

	@Test
	@DisplayName("D-W2-31: the settle is driven by UserDataInitEvent only - no PlayerJoinEvent handler; the sync reload event hops too")
	void noPlayerJoinEventHandler() {
		assertTrue(handlersTaking(PlayerJoinEvent.class).isEmpty());

		listener.onUserData(userData(false));

		assertEquals(1, fx.tasks.size());
		assertTrue(fx.debug.isEmpty());
		assertTrue(fx.chat.isEmpty());
	}

	@Test
	@DisplayName("a cancelled UserDataInitEvent queues nothing")
	void cancelledUserDataIsIgnoredByTheHandlerAnnotation() throws NoSuchMethodException {
		EventHandler handler = BagListener.class.getMethod("onUserData", UserDataInitEvent.class)
		                                        .getAnnotation(EventHandler.class);

		assertNotNull(handler);
		assertTrue(handler.ignoreCancelled());
	}

	@Test
	@DisplayName("a player who went offline before the hop is not settled")
	void offlinePlayerIsNotSettled() {
		seedSaved("500");
		fx.level(runner, 0);
		listener.onUserData(userData(true));
		when(runner.isOnline()).thenReturn(false);

		fx.runTasks();

		assertEquals(0, bd("500").compareTo(fx.bags.amount(runner.getUniqueId())));
		assertEquals(0, bd("0").compareTo(fx.walletOf(runner)));
	}

	@Test
	@DisplayName("D-W2-31: a quit before the settle task ran keeps the row - no spill, no seize")
	void quitBeforeSettleKeepsRow() {
		seedSaved("500");
		fx.level(runner, 2);
		listener.onUserData(userData(true));

		listener.onQuit(new PlayerQuitEvent(runner, "bye"));

		assertTrue(fx.spills.isEmpty());
		assertEquals(0, bd("0").compareTo(fx.bags.seized(runner.getUniqueId())));
		assertEquals(0, bd("500").compareTo(fx.bags.amount(runner.getUniqueId())));
		assertTrue(fx.debug.contains("BAG Runner quit outcome=kept bag=500"), fx.debug.toString());
	}

	@Test
	@DisplayName("quit clears the settled mark: after a quit and a rejoin the bag is unsettled again until the next hop")
	void quitClearsSettled() {
		settleNow();
		fx.level(runner, 2);
		fx.service.take(runner, bd("500"), "pickup");
		listener.onQuit(new PlayerQuitEvent(runner, "bye"));
		fx.spills.clear();
		fx.bags.put(new BagEntry(runner.getUniqueId(), bd("500"), bd("500"), 0L, BigDecimal.ZERO, BigDecimal.ZERO));

		listener.onQuit(new PlayerQuitEvent(runner, "again"));

		assertTrue(fx.spills.isEmpty(), "unsettled again: kept");
	}

	private EntityPickupItemEvent pickup(org.bukkit.entity.LivingEntity picker) {
		Item item = mock(Item.class);
		when(item.getItemStack()).thenReturn(mock(ItemStack.class));
		return new EntityPickupItemEvent(picker, item, 0);
	}

	@Test
	@DisplayName("P-BAG-8: a hot bill picked up by an unwanted player raises one star and the event is not cancelled")
	void hotPickupRaisesOneStarForUnwanted() {
		fx.level(runner, 0);
		EntityPickupItemEvent event = pickup(runner);

		listener.onPickup(event);

		assertEquals(List.of(runner), fx.stars);
		assertFalse(event.isCancelled());
		assertTrue(fx.debug.contains("BAG Runner hot star"), fx.debug.toString());
		assertTrue(fx.chat.contains(fx.line(org.luckyraven.gangland.copsncrooks.wanted.WantedMessages.Key.BAG_HOT)),
		           fx.chat.toString());
	}

	@Test
	@DisplayName("P-BAG-8: a wanted picker earns no star (the bag takes the bill)")
	void hotPickupWhileWantedNoStar() {
		fx.level(runner, 2);

		listener.onPickup(pickup(runner));

		assertTrue(fx.stars.isEmpty());
	}

	@Test
	@DisplayName("a plain money item, or a non-player picker, raises nothing")
	void plainMoneyOrMobPickupRaisesNothing() {
		fx.level(runner, 0);
		fx.hot = item -> false;
		listener.onPickup(pickup(runner));

		fx.hot = item -> true;
		listener.onPickup(pickup(mock(Zombie.class)));

		assertTrue(fx.stars.isEmpty());
	}

	@Test
	@DisplayName("the pickup handler is NORMAL and ignores cancelled events")
	void pickupHandlerShape() {
		EventHandler handler = handlersTaking(EntityPickupItemEvent.class).get(0).getAnnotation(EventHandler.class);

		assertEquals(EventPriority.NORMAL, handler.priority());
		assertTrue(handler.ignoreCancelled());
	}
}
