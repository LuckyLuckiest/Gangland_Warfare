package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;

import java.math.BigDecimal;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * The production {@link BagEnv}: users, wanted stars, detainment, the scheduler, the spill and the action bar.
 *
 * @since 0.20.0
 */
public final class ServerBagEnv implements BagEnv {

	/**
	 * @param moneyItem  {@code MoneyItemUtil::isMoneyItem}
	 * @param billReader {@code MoneyItemUtil::readAmount}
	 * @param actionBar  sends the text to the player's action bar
	 */
	public ServerBagEnv(JavaPlugin plugin, UserManager<Player> users, DetainmentService detainment, WantedStars stars,
	                    BagSpill spill, Supplier<BagSettings> settings, WantedMessages messages,
	                    Predicate<ItemStack> moneyItem, ToIntFunction<ItemStack> billReader,
	                    BiConsumer<Player, String> actionBar) {
	}

	@Override
	public int wantedLevel(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public boolean restrained(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void deposit(Player player, BigDecimal amount) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void spill(Player player, Location at, BigDecimal amount) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public long epochDay() {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public long nowMillis() {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void hud(Player player, BigDecimal bag) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void debug(String line) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void runTask(Runnable task) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void raiseHotStar(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public boolean isHotMoney(ItemStack item) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public int billValue(ItemStack item) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
