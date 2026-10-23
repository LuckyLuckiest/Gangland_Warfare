package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lane bag: {@link ServerBagEnv}, the production seam. Every server access of the bag goes through it, so a wrong wire
 * here (star cause, wallet, tag check, scheduler) kills the feature with the fake-env tests still green.
 */
@DisplayName("ServerBagEnv - the production BagEnv")
class ServerBagEnvTest {

	private JavaPlugin               plugin;
	private BukkitScheduler          scheduler;
	private UserManager<Player>      users;
	private DetainmentService        detainment;
	private WantedStars              stars;
	private WantedMessages           messages;
	private Predicate<ItemStack>     moneyItem;
	private ToIntFunction<ItemStack> reader;
	private final List<String>       bar = new ArrayList<>();
	private ServerBagEnv             env;
	private Player                   player;
	private User<Player>             user;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		BagFixture.primeMoneySymbol();
		plugin    = mock(JavaPlugin.class);
		scheduler = mock(BukkitScheduler.class);
		Server server = mock(Server.class);
		when(plugin.getServer()).thenReturn(server);
		when(server.getScheduler()).thenReturn(scheduler);
		users      = mock(UserManager.class);
		detainment = mock(DetainmentService.class);
		stars      = mock(WantedStars.class);
		messages   = mock(WantedMessages.class);
		moneyItem  = item -> true;
		reader     = item -> 250;
		BagSpill spill = new BagSpill(plugin, dollars -> {
			ItemStack stack = mock(ItemStack.class);
			ItemMeta  meta  = mock(ItemMeta.class);
			when(meta.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
			when(stack.getItemMeta()).thenReturn(meta);
			return stack;
		});
		env = new ServerBagEnv(plugin, users, detainment, stars, spill, () -> BagSettings.DEFAULT, messages,
		                       item -> moneyItem.test(item), item -> reader.applyAsInt(item),
		                       (p, text) -> bar.add(text));
		player = mock(Player.class);
		user   = mock(User.class);
		when(users.getUser(player)).thenReturn(user);
	}

	private ItemStack taggedItem(boolean tagged) {
		ItemStack               item = mock(ItemStack.class);
		ItemMeta                meta = mock(ItemMeta.class);
		PersistentDataContainer pdc  = mock(PersistentDataContainer.class);
		when(pdc.has(BagSpill.KEY, PersistentDataType.BYTE)).thenReturn(tagged);
		when(meta.getPersistentDataContainer()).thenReturn(pdc);
		when(item.getItemMeta()).thenReturn(meta);
		return item;
	}

	@Test
	@DisplayName("wantedLevel is 0 without an online user, else the Wanted level")
	void wantedLevelReadsTheUser() {
		assertEquals(0, env.wantedLevel(mock(Player.class)));

		Wanted wanted = mock(Wanted.class);
		when(user.getWanted()).thenReturn(wanted);
		when(wanted.getLevel()).thenReturn(3);

		assertEquals(3, env.wantedLevel(player));
	}

	@Test
	@DisplayName("raiseHotStar raises exactly one star with cause CRIME; no user, no raise")
	void raiseHotStarIsOneCrimeStar() {
		env.raiseHotStar(mock(Player.class));
		verify(stars, never()).raise(any(), anyInt(), any());

		env.raiseHotStar(player);

		verify(stars).raise(user, 1, WantedCause.CRIME);
	}

	@Test
	@DisplayName("restrained asks the detainment service")
	void restrainedDelegates() {
		when(detainment.isRestrained(player)).thenReturn(true);

		assertTrue(env.restrained(player));
		assertFalse(env.restrained(mock(Player.class)));
	}

	@Test
	@DisplayName("deposit credits the online user's economy; an offline player is skipped without throwing")
	void depositCreditsTheWallet() {
		EconomyHandler economy = new EconomyHandler(null);
		when(user.getEconomy()).thenReturn(economy);
		BigDecimal before = economy.getAmount();
		BigDecimal amount = new BigDecimal("24.37");

		env.deposit(player, amount);
		env.deposit(mock(Player.class), amount);

		assertEquals(0, before.add(amount).compareTo(economy.getAmount()), economy.getAmount().toPlainString());
	}

	@Test
	@DisplayName("isHotMoney needs BOTH a money item and the hot tag")
	void isHotMoneyNeedsMoneyAndTag() {
		moneyItem = item -> false;
		assertFalse(env.isHotMoney(taggedItem(true)), "a hot-tagged item that is not money");

		moneyItem = item -> true;
		assertFalse(env.isHotMoney(taggedItem(false)), "a plain bill");
		assertTrue(env.isHotMoney(taggedItem(true)), "a hot bill");
	}

	@Test
	@DisplayName("billValue is the money reader's value for one bill")
	void billValueReadsOneBill() {
		assertEquals(250, env.billValue(mock(ItemStack.class)));
	}

	@Test
	@DisplayName("spill drops Spill_Stacks hot bills at the location")
	void spillDropsTheConfiguredStacks() {
		World    world = mock(World.class);
		Location at    = new Location(world, 1, 64, 1);

		env.spill(player, at, new BigDecimal("1000"));

		verify(world, times(BagSettings.DEFAULT.spillStacks())).dropItemNaturally(eq(at), any(ItemStack.class));
	}

	@Test
	@DisplayName("hud sends Bag.Hud with the money symbol and the formatted bag to the action bar")
	@SuppressWarnings("unchecked")
	void hudFormatsBagHud() {
		when(messages.format(eq(WantedMessages.Key.BAG_HUD), anyMap())).thenReturn("HUD-TEXT");

		env.hud(player, new BigDecimal("250"));

		ArgumentCaptor<Map<String, String>> values = ArgumentCaptor.forClass(Map.class);
		verify(messages).format(eq(WantedMessages.Key.BAG_HUD), values.capture());
		assertEquals(Settings.formatAmount(new BigDecimal("250")), values.getValue().get("amount"));
		assertEquals(Settings.getMoneySymbol(), values.getValue().get("money_symbol"));
		assertEquals(List.of("HUD-TEXT"), bar);
	}

	@Test
	@DisplayName("runTask queues the task on the next main-thread tick")
	void runTaskUsesTheScheduler() {
		Runnable task = () -> {
		};

		env.runTask(task);

		verify(scheduler).runTask(plugin, task);
	}

	@Test
	@DisplayName("the clock is the server's: today's epoch day and the current millis")
	void clockIsTheServers() {
		long before = System.currentTimeMillis();
		long now    = env.nowMillis();

		assertTrue(now >= before && now <= System.currentTimeMillis() + 1);
		assertTrue(Math.abs(LocalDate.now().toEpochDay() - env.epochDay()) <= 1);
	}
}
