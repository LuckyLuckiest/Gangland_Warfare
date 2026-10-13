package org.luckyraven.gangland.gadget.car.theft;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.events.vehicle.CarTheftAttemptEvent;
import org.luckyraven.keystone.util.ActionBarManager;
import org.luckyraven.gangland.gadget.car.access.CarAccessPolicy;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Lockpick hotwire of a car the player may not use (P-CRIME-1). A single repeating task ticks every active hotwire
 * each 10 ticks on the main thread; success grants the thief the car for the session, marks it stolen and commits
 * {@code Car_Theft}. Ownership never transfers (docket T-73).
 */
@CustomLog
public class HotwireService {

	private static final long TICK_PERIOD = 10L; // one step = 10 ticks = half a second

	private final JavaPlugin                      plugin;
	private final CarAccessPolicy                 policy;
	private final CrimeService                    crimes;
	private final GadgetPhysicsConfig             config;
	private final HotwireMessages                 messages;
	private final Function<ItemStack, String>     itemKeyReader;
	private final NamespacedKey                   stolenBy;
	private final Map<UUID, Session>              byThief = new HashMap<>();
	private final Map<UUID, UUID>                 carsBusy = new HashMap<>(); // carEntityId -> thief
	private       BukkitTask                      task;

	public HotwireService(JavaPlugin plugin, CarAccessPolicy policy, CrimeService crimes, GadgetPhysicsConfig config,
	                      HotwireMessages messages, Function<ItemStack, String> itemKeyReader) {
		this.plugin        = plugin;
		this.policy        = policy;
		this.crimes        = crimes;
		this.config        = config;
		this.messages      = messages;
		this.itemKeyReader = itemKeyReader;
		this.stolenBy      = new NamespacedKey(plugin, "stolen_by");
	}

	/**
	 * @return {@code Hotwire.Enable}
	 */
	public boolean isEnabled() {
		return config.isHotwireEnabled();
	}

	/**
	 * @return true when the stack's unique item key equals {@code Hotwire.Lockpick_Item}
	 */
	public boolean isLockpick(@Nullable ItemStack stack) {
		return stack != null && config.getHotwireLockpickItem().equals(itemKeyReader.apply(stack));
	}

	/**
	 * Fires {@code CarTheftAttemptEvent}; when not cancelled starts the timer.
	 *
	 * @return true when a hotwire is now running
	 */
	public boolean start(Player thief, Entity car, @Nullable UUID owner, String carType) {
		if (!isEnabled()) {
			return false;
		}
		if (byThief.containsKey(thief.getUniqueId()) || carsBusy.containsKey(car.getUniqueId())) {
			return false;
		}
		if (!isLockpick(thief.getInventory().getItemInMainHand())) {
			ActionBarManager.send(thief, messages.noLockpick());
			return false;
		}

		CarTheftAttemptEvent attempt = new CarTheftAttemptEvent(thief, car, owner);
		Bukkit.getPluginManager().callEvent(attempt);
		log.debug("CAR_EVENT theft {} car={}", thief.getName(), carType);
		if (attempt.isCancelled()) {
			return false;
		}

		byThief.put(thief.getUniqueId(), new Session(thief, car, carType, config.getHotwireSeconds() * 2));
		carsBusy.put(car.getUniqueId(), thief.getUniqueId());
		ensureTask();
		log.debug("HOTWIRE {} car={} result={}", thief.getName(), carType, "start");
		return true;
	}

	/**
	 * One 10-tick step over every active hotwire.
	 */
	public void tick() {
		Iterator<Session> it = byThief.values().iterator();
		while (it.hasNext()) {
			Session session = it.next();
			if (step(session)) {
				it.remove();
				carsBusy.remove(session.car.getUniqueId());
			}
		}
		if (byThief.isEmpty() && task != null) {
			task.cancel();
			task = null;
		}
	}

	public boolean isHotwiring(UUID thief) {
		return byThief.containsKey(thief);
	}

	private void ensureTask() {
		if (task == null || task.isCancelled()) {
			task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
		}
	}

	/**
	 * @return true when the session ended (done or cancelled)
	 */
	private boolean step(Session s) {
		Player thief = s.thief;
		if (!thief.isOnline()) {
			s.log("cancel");
			return true;
		}
		if (!s.car.isValid() || s.car.isDead() || !inRange(thief, s.car)) {
			ActionBarManager.send(thief, messages.cancel());
			s.log("cancel");
			return true;
		}
		if (findLockpick(thief) < 0) {
			ActionBarManager.send(thief, messages.noLockpick());
			s.log("cancel");
			return true;
		}

		s.remaining--;
		if (s.remaining > 0) {
			ActionBarManager.send(thief, messages.progress((s.remaining + 1) / 2));
			return false;
		}

		finish(s);
		return true;
	}

	private void finish(Session s) {
		Player thief = s.thief;
		policy.grant(s.car.getUniqueId(), thief.getUniqueId());
		s.car.getPersistentDataContainer().set(stolenBy, PersistentDataType.STRING, thief.getUniqueId().toString());
		if (config.isHotwireConsumeLockpick()) {
			consumeLockpick(thief);
		}
		crimes.commit(thief, Crimes.CAR_THEFT, s.car.getLocation());
		ActionBarManager.send(thief, messages.done());
		s.log("done");
	}

	private boolean inRange(Player thief, Entity car) {
		Location a = thief.getLocation();
		Location b = car.getLocation();
		return a.getWorld() == b.getWorld() && a.distance(b) <= config.getHotwireMaxDistance();
	}

	private int findLockpick(Player thief) {
		ItemStack[] contents = thief.getInventory().getContents();
		for (int i = 0; i < contents.length; i++) {
			if (isLockpick(contents[i])) {
				return i;
			}
		}
		return -1;
	}

	private void consumeLockpick(Player thief) {
		int slot = findLockpick(thief);
		if (slot < 0) {
			return;
		}
		ItemStack stack = thief.getInventory().getContents()[slot];
		if (stack.getAmount() > 1) {
			stack.setAmount(stack.getAmount() - 1);
		} else {
			thief.getInventory().setItem(slot, null);
		}
	}

	private static final class Session {

		final Player thief;
		final Entity car;
		final String carType;
		int          remaining;

		Session(Player thief, Entity car, String carType, int remaining) {
			this.thief     = thief;
			this.car       = car;
			this.carType   = carType;
			this.remaining = remaining;
		}

		void log(String result) {
			log.debug("HOTWIRE {} car={} result={}", thief.getName(), carType, result);
		}

	}

}
