package org.luckyraven.gangland.copsncrooks.bag;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.file.configuration.Settings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * The production {@link BagEnv}: users, wanted stars, detainment, the scheduler, the spill and the action bar.
 *
 * @since 0.20.0
 */
@CustomLog
public final class ServerBagEnv implements BagEnv {

	private final JavaPlugin                plugin;
	private final UserManager<Player>       users;
	private final DetainmentService         detainment;
	private final WantedStars               stars;
	private final BagSpill                  spill;
	private final Supplier<BagSettings>     settings;
	private final WantedMessages            messages;
	private final Predicate<ItemStack>      moneyItem;
	private final ToIntFunction<ItemStack>  billReader;
	private final BiConsumer<Player, String> actionBar;

	/**
	 * @param moneyItem  {@code MoneyItemUtil::isMoneyItem}
	 * @param billReader {@code MoneyItemUtil::readAmount}
	 * @param actionBar  sends the text to the player's action bar
	 */
	public ServerBagEnv(JavaPlugin plugin, UserManager<Player> users, DetainmentService detainment, WantedStars stars,
	                    BagSpill spill, Supplier<BagSettings> settings, WantedMessages messages,
	                    Predicate<ItemStack> moneyItem, ToIntFunction<ItemStack> billReader,
	                    BiConsumer<Player, String> actionBar) {
		this.plugin     = plugin;
		this.users      = users;
		this.detainment = detainment;
		this.stars      = stars;
		this.spill      = spill;
		this.settings   = settings;
		this.messages   = messages;
		this.moneyItem  = moneyItem;
		this.billReader = billReader;
		this.actionBar  = actionBar;
	}

	private User<Player> userOf(Player player) {
		return users.getUser(player);
	}

	@Override
	public int wantedLevel(Player player) {
		User<Player> user = userOf(player);
		return user == null ? 0 : user.getWanted().getLevel();
	}

	@Override
	public boolean restrained(Player player) {
		return detainment.isRestrained(player);
	}

	@Override
	public void deposit(Player player, BigDecimal amount) {
		User<Player> user = userOf(player);
		if (user != null) {
			user.getEconomy().depositAmount(amount);
		}
	}

	@Override
	public void spill(Player player, Location at, BigDecimal amount) {
		spill.drop(at, amount, settings.get().spillStacks());
	}

	@Override
	public long epochDay() {
		return LocalDate.now().toEpochDay();
	}

	@Override
	public long nowMillis() {
		return System.currentTimeMillis();
	}

	@Override
	public void hud(Player player, BigDecimal bag) {
		String text = messages.format(WantedMessages.Key.BAG_HUD,
		                              Map.of("money_symbol", Settings.getMoneySymbol(),
		                                     "amount", Settings.formatAmount(bag)));
		actionBar.accept(player, text);
	}

	@Override
	public void debug(String line) {
		log.debug(line);
	}

	@Override
	public void runTask(Runnable task) {
		plugin.getServer().getScheduler().runTask(plugin, task);
	}

	@Override
	public void raiseHotStar(Player player) {
		User<Player> user = userOf(player);
		if (user != null) {
			stars.raise(user, 1, WantedCause.CRIME);
		}
	}

	@Override
	public boolean isHotMoney(ItemStack item) {
		return moneyItem.test(item) && spill.isHot(item);
	}

	@Override
	public int billValue(ItemStack item) {
		return billReader.applyAsInt(item);
	}
}
