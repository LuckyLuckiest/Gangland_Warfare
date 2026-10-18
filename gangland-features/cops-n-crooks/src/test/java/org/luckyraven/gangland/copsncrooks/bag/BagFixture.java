package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test double for the lane bag: a fake {@link BagEnv} that records everything, plus a real {@link HotCashBags},
 * {@link WantedMessages} (in-code fallbacks) and {@link BagService} wired on it. Players are Mockito mocks whose chat is
 * captured colour-stripped in {@link #chat}.
 */
public final class BagFixture implements BagEnv {

	/** One spill the service asked for. */
	public record Spilled(Player player, Location at, BigDecimal amount) {
	}

	public final Map<UUID, Integer>    levels     = new HashMap<>();
	public final Set<UUID>             restrained = new HashSet<>();
	public final Map<UUID, BigDecimal> wallet     = new HashMap<>();
	public final List<String>          debug      = new ArrayList<>();
	public final List<Spilled>         spills     = new ArrayList<>();
	public final List<String>          chat       = new ArrayList<>();
	public final List<BigDecimal>      huds       = new ArrayList<>();
	public final List<Player>          stars      = new ArrayList<>();
	public final Deque<Runnable>       tasks      = new ArrayDeque<>();

	public Predicate<ItemStack>   hot  = item -> true;
	public ToIntFunction<ItemStack> bill = item -> 100;
	public long                 day = 10L;
	public long                 now = 1_000_000L;
	public BagSettings          settings = BagSettings.DEFAULT;

	public final HotCashBags     bags = new HotCashBags(null);
	public final WantedMessages  messages;
	public final BagService      service;

	public BagFixture(Path tempDir) {
		primeMoneySymbol();
		try {
			Path file = tempDir.resolve("wanted_messages.yml");
			Files.writeString(file, "# fallbacks only\n", StandardCharsets.UTF_8);
			JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
			FileManager fileManager = new FileManager(plugin);
			fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
			messages = new WantedMessages(fileManager);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		service = new BagService(bags, () -> settings, messages, this);
	}

	static void primeMoneySymbol() {
		try {
			Field field = Settings.class.getDeclaredField("moneySymbol");
			field.setAccessible(true);
			if (field.get(null) == null) {
				field.set(null, "$");
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A fresh online player; his chat lands in {@link #chat}. */
	public Player player(String name) {
		Player player = mock(Player.class);
		UUID   id     = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(id);
		when(player.getName()).thenReturn(name);
		when(player.isOnline()).thenReturn(true);
		when(player.getLocation()).thenReturn(new Location(mock(World.class), 5, 64, 5));
		doAnswer(inv -> chat.add(ChatColor.stripColor(inv.getArgument(0)))).when(player).sendMessage(any(String.class));
		return player;
	}

	public void level(Player player, int level) {
		levels.put(player.getUniqueId(), level);
	}

	public BigDecimal walletOf(Player player) {
		return wallet.getOrDefault(player.getUniqueId(), BigDecimal.ZERO);
	}

	public BigDecimal bag(Player player) {
		return bags.amount(player.getUniqueId());
	}

	public static BigDecimal bd(String value) {
		return new BigDecimal(value);
	}

	/** The amount as the shipped messages print it. */
	public static String money(String amount) {
		return Settings.formatAmount(new BigDecimal(amount));
	}

	/** The expected, colour-stripped line: {@code kv} is name, amount, name, amount ...; the symbol is added. */
	public String line(WantedMessages.Key key, String... kv) {
		Map<String, String> values = new HashMap<>();
		values.put("money_symbol", Settings.getMoneySymbol());
		for (int i = 0; i < kv.length; i += 2) {
			values.put(kv[i], money(kv[i + 1]));
		}
		return ChatColor.stripColor(messages.format(key, values));
	}

	/** Runs the queued main-thread tasks in the order they were queued. */
	public void runTasks() {
		while (!tasks.isEmpty()) {
			tasks.poll().run();
		}
	}

	@Override
	public int wantedLevel(Player player) {
		return levels.getOrDefault(player.getUniqueId(), 0);
	}

	@Override
	public boolean restrained(Player player) {
		return restrained.contains(player.getUniqueId());
	}

	@Override
	public void deposit(Player player, BigDecimal amount) {
		wallet.merge(player.getUniqueId(), amount, BigDecimal::add);
	}

	@Override
	public void spill(Player player, Location at, BigDecimal amount) {
		spills.add(new Spilled(player, at, amount));
	}

	@Override
	public long epochDay() {
		return day;
	}

	@Override
	public long nowMillis() {
		return now;
	}

	@Override
	public void hud(Player player, BigDecimal bag) {
		huds.add(bag);
	}

	@Override
	public void debug(String line) {
		debug.add(line);
	}

	@Override
	public void runTask(Runnable task) {
		tasks.add(task);
	}

	@Override
	public void raiseHotStar(Player player) {
		stars.add(player);
		levels.merge(player.getUniqueId(), 1, Integer::sum); // a raise really lifts the level
	}

	@Override
	public boolean isHotMoney(ItemStack item) {
		return hot.test(item);
	}

	@Override
	public int billValue(ItemStack item) {
		return bill.applyAsInt(item);
	}
}
