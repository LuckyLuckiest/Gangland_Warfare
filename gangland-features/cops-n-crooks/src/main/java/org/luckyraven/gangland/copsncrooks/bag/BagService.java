package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages.Key;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.data.economy.HotCash;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The hot-cash bag: takes cash while wanted, and moves the bag exactly once on every ending (bank, convert, seize,
 * spill), the fence and a bribe.
 *
 * @since 0.20.0
 */
public final class BagService implements HotCash, BagFunding, BeanLifecycle {

	private static final long FULL_THROTTLE_MILLIS = 3_000L;

	private final HotCashBags           bags;
	private final Supplier<BagSettings> settings;
	private final WantedMessages        messages;
	private final BagEnv                env;
	private final Map<UUID, Long>       lastFullLine = new ConcurrentHashMap<>();
	private final Set<UUID>             settled      = ConcurrentHashMap.newKeySet();
	private volatile boolean            shuttingDown;

	public BagService(HotCashBags bags, Supplier<BagSettings> settings, WantedMessages messages, BagEnv env) {
		this.bags     = bags;
		this.settings = settings;
		this.messages = messages;
		this.env      = env;
	}

	@Override
	public Take take(Player player, BigDecimal amount, String source) {
		BagSettings s = settings.get();
		if (!s.enable() || env.wantedLevel(player) <= 0) {
			return Take.WALLET;
		}

		UUID id = player.getUniqueId();
		if (!bags.add(id, amount, BigDecimal.valueOf(s.maxPerChase()))) {
			long now  = env.nowMillis();
			Long last = lastFullLine.get(id);
			if (last == null || now - last >= FULL_THROTTLE_MILLIS) {
				lastFullLine.put(id, now);
				player.sendMessage(line(Key.BAG_FULL));
			}
			env.debug("BAG " + player.getName() + " full source=" + source + " bag=" + plain(bags.amount(id)));
			return Take.FULL;
		}

		BigDecimal bag = bags.amount(id);
		env.debug("BAG " + player.getName() + " +" + plain(amount) + " source=" + source + " bag=" + plain(bag));
		if (s.hudEnable()) {
			env.hud(player, bag);
		}
		return Take.BAGGED;
	}

	@Override
	public boolean fence(Player player) {
		String name = player.getName();
		if (env.restrained(player)) {
			player.sendMessage(line(Key.FENCE_CUFFED));
			env.debug("FENCE " + name + " refused reason=cuffed");
			return false;
		}

		UUID id = player.getUniqueId();
		if (bags.amount(id).signum() <= 0) {
			player.sendMessage(line(Key.FENCE_EMPTY));
			env.debug("FENCE " + name + " refused reason=empty");
			return false;
		}

		BigDecimal amount = bags.takeAllKeepChase(id);
		BigDecimal paid   = convert(amount);
		pay(player, paid);
		player.sendMessage(line(Key.FENCE_DONE, "amount", paid, "bag", amount));
		env.debug("FENCE " + name + " bag=" + plain(amount) + " paid=" + plain(paid));
		return true;
	}

	/** A wanted state ended with {@code cause}: pays the bag once, per D-W2-07. */
	public void end(Player player, WantedCause cause) {
		UUID       id     = player.getUniqueId();
		BigDecimal amount = bags.takeAll(id);
		if (amount.signum() <= 0) {
			return;
		}

		boolean    restrained = env.restrained(player);
		BigDecimal paid       = BigDecimal.ZERO;
		String     outcome;

		switch (cause) {
			case EVASION, DECAY -> {
				if (restrained) {
					bags.setSeized(id, amount);
					outcome = "seized";
				} else {
					long       day      = env.epochDay();
					BigDecimal cap      = BigDecimal.valueOf(settings.get().maxBankedPerDay());
					BigDecimal banked   = bags.allowance(id, day, cap, amount);
					BigDecimal rest     = amount.subtract(banked);
					BigDecimal restPaid = convert(rest);
					paid = banked.add(restPaid);
					bags.recordBanked(id, day, banked);
					pay(player, paid);
					if (banked.signum() > 0) {
						player.sendMessage(line(Key.BAG_BANKED, "amount", banked));
					}
					if (rest.signum() > 0) {
						player.sendMessage(line(Key.BAG_OVER_CAP, "cut", rest.subtract(restPaid), "rest", rest));
					}
					outcome = "banked";
				}
			}
			case ARREST -> {
				bags.setSeized(id, amount);
				outcome = "seized";
			}
			case DEATH -> {
				if (restrained) {
					bags.setSeized(id, amount);
					outcome = "seized";
				} else {
					env.spill(player, player.getLocation(), amount);
					player.sendMessage(line(Key.BAG_SPILLED, "amount", amount));
					outcome = "spilled";
				}
			}
			default -> {
				paid = convert(amount);
				pay(player, paid);
				player.sendMessage(line(Key.BAG_CONVERTED, "bag", amount, "amount", paid));
				outcome = "converted";
			}
		}

		env.debug("BAG " + player.getName() + " end cause=" + cause + " outcome=" + outcome + " bag=" + plain(amount)
		          + " paid=" + plain(paid));
	}

	/** Takes the whole bag for the police ({@code seized} on the row); returns the amount (0 when empty). */
	public double seize(Player player) {
		UUID       id     = player.getUniqueId();
		BigDecimal amount = bags.takeAll(id);
		bags.setSeized(id, amount);
		return amount.doubleValue();
	}

	@Override
	public BigDecimal available(Player player) {
		return bags.amount(player.getUniqueId());
	}

	@Override
	public void debit(Player player, BigDecimal amount) {
		bags.debit(player.getUniqueId(), amount);
	}

	@Override
	public void announceBribe(Player player, BigDecimal fromBag, BigDecimal fromWallet) {
		player.sendMessage(line(Key.BAG_BRIBE, "bag", fromBag, "wallet", fromWallet));
		env.debug("BAG " + player.getName() + " bribe bag=" + plain(fromBag) + " wallet=" + plain(fromWallet));
	}

	/** The "hot cash, the cops want you" warning line (listener pickup and redeem). */
	public String hotLine() {
		return line(Key.BAG_HOT);
	}

	/** The paperwork line for a seized bag, or null when nothing was seized. */
	public String paperworkLine(UUID player) {
		BigDecimal seized = bags.seized(player);
		return seized.signum() > 0 ? line(Key.BAG_PAPERWORK, "amount", seized) : null;
	}

	/** Join settle (after the star restore): a saved bag with stars is restored, without stars converted. */
	public void settle(Player player) {
		settled.add(player.getUniqueId());
		BigDecimal bag = bags.amount(player.getUniqueId());
		if (bag.signum() <= 0) {
			return;
		}
		if (env.wantedLevel(player) > 0) {
			env.hud(player, bag);
			env.debug("BAG " + player.getName() + " restored bag=" + plain(bag));
		} else {
			end(player, WantedCause.UNKNOWN);
		}
	}

	/** Quit: spill / seize / keep per D-W2-08, D-W2-31 and the shutdown flag. */
	public void onQuit(Player player) {
		UUID id = player.getUniqueId();
		boolean wasSettled = settled.remove(id);
		BigDecimal bag = bags.amount(id);
		if (bag.signum() <= 0 || shuttingDown) {
			return;
		}

		String outcome;
		if (!wasSettled) {
			outcome = "kept";
		} else if (env.restrained(player)) {
			seize(player);
			outcome = "seized";
		} else {
			bags.takeAll(id);
			env.spill(player, player.getLocation(), bag);
			outcome = "spilled";
		}
		env.debug("BAG " + player.getName() + " quit outcome=" + outcome + " bag=" + plain(bag));
	}

	/** Marks the service as shutting down (quits no longer spill) and saves every row. */
	public void shutdown() {
		shuttingDown = true;
		bags.flush();
	}

	/** A reload runs the shutdown hook (quits stop spilling); re-initialising clears that flag again. */
	@Override
	public void onInitialize(boolean firstLoad) {
		shuttingDown = false;
	}

	public boolean isShuttingDown() {
		return shuttingDown;
	}

	/** Sends the HUD to every online player holding a bag (the repeating task body). */
	public void refreshHud(Iterable<? extends Player> online) {
		if (!settings.get().hudEnable()) {
			return;
		}
		for (Player player : online) {
			BigDecimal bag = bags.amount(player.getUniqueId());
			if (bag.signum() > 0) {
				env.hud(player, bag);
			}
		}
	}

	/** {@code amount} minus the fence cut, rounded down to the cent. */
	private BigDecimal convert(BigDecimal amount) {
		BigDecimal keep = BigDecimal.ONE.subtract(BigDecimal.valueOf(settings.get().fenceCut()));
		return amount.multiply(keep).setScale(2, RoundingMode.DOWN);
	}

	private void pay(Player player, BigDecimal amount) {
		if (amount.signum() > 0) {
			env.deposit(player, amount);
		}
	}

	private static BigDecimal clean(BigDecimal value) {
		BigDecimal stripped = value.stripTrailingZeros();
		return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
	}

	private static String plain(BigDecimal value) {
		return clean(value).toPlainString();
	}

	/** {@code kv}: placeholder name, BigDecimal, ... */
	private String line(Key key, Object... kv) {
		Map<String, String> values = new HashMap<>();
		values.put("money_symbol", Settings.getMoneySymbol());
		for (int i = 0; i < kv.length; i += 2) {
			values.put((String) kv[i], Settings.formatAmount(clean((BigDecimal) kv[i + 1])));
		}
		return messages.format(key, values);
	}
}
