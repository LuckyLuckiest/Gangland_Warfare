package org.luckyraven.gangland.turf.npc.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupNpc;
import org.luckyraven.gangland.turf.npc.config.TurfPowerupSettings;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.luckyraven.gangland.turf.npc.view.QuartermasterAccess.money;

/**
 * One-button confirm menu behind {@code /glw turf quartermaster} (W7-B, D29=B): the gang that owns the turf places or
 * moves its own Quartermaster where the player stands, paying {@code Powerup_Npc.Placement_Cost} from the gang bank.
 * Allies never place (D25=B). The staff command {@code /glw turf powerupnpc set|remove} is separate and free.
 */
@CustomLog
public final class QuartermasterPlacement implements Panel<TurfPowerupFlowSession> {

	static final String PANEL = "place";

	private static final int ROWS       = 3;
	private static final int SLOT_PLACE = 13;
	private static final int SLOT_CLOSE = 22;

	static final String PLACE_NOT_OWNER  = "Quartermaster.Place_Not_Owner";
	static final String PLACE_NO_CITIZENS = "Quartermaster.Place_No_Citizens";
	static final String PLACE_OUTSIDE    = "Quartermaster.Place_Outside";
	private static final String GANG_MISSING = "Quartermaster.Gang_Missing";

	private final JavaPlugin                    plugin;
	private final InventoryService              inventoryService;
	private final TurfManager                   turfs;
	private final GangLookupContract            gangs;
	private final UserLookupContract            users;
	private final QuartermasterAccess           access;
	private final TurfPowerupManager            npcs;
	private final TurfMessages                  messages;
	private final Supplier<TurfPowerupSettings> settings;
	private final BooleanSupplier               citizens;
	private final String                        fillItem;
	private final String                        fillName;

	public QuartermasterPlacement(JavaPlugin plugin, InventoryService inventoryService, TurfManager turfs,
	                              GangLookupContract gangs, UserLookupContract users, QuartermasterAccess access,
	                              TurfPowerupManager npcs, TurfMessages messages,
	                              Supplier<TurfPowerupSettings> settings, BooleanSupplier citizens,
	                              String fillItem, String fillName) {
		this.plugin           = plugin;
		this.inventoryService = inventoryService;
		this.turfs            = turfs;
		this.gangs            = gangs;
		this.users            = users;
		this.access           = access;
		this.npcs             = npcs;
		this.messages         = messages;
		this.settings         = settings;
		this.citizens         = citizens;
		this.fillItem         = fillItem;
		this.fillName         = fillName;
	}

	/**
	 * @return {@code null} when {@code player} may place the Quartermaster of {@code turf} now, else the key of the
	 * refusal line
	 */
	@Nullable
	String denyKey(Player player, Turf turf) {
		if (!citizens.getAsBoolean()) {
			return PLACE_NO_CITIZENS;
		}
		Integer ownerId = turf.getOwnerGangId();
		if (ownerId == null) {
			return PLACE_NOT_OWNER;
		}
		User<Player> user = users.findByPlayer(player);
		if (user == null || !user.hasGang() || user.getGangId() != ownerId) {
			return PLACE_NOT_OWNER;
		}
		Gang owner = gangs.findById(ownerId);
		if (owner == null) {
			return GANG_MISSING;
		}
		return access.denyKey(player, new TurfPowerupFlowSession(turf, owner, owner, settings.get().displayName()));
	}

	public void open(Player player, Turf turf) {
		String deny = denyKey(player, turf);
		if (deny != null) {
			player.sendMessage(refusalLine(deny, turf));
			return;
		}
		Gang owner = gangs.findById(turf.getOwnerGangId());
		TurfPowerupFlowSession session = new TurfPowerupFlowSession(turf, owner, owner,
		                                                            settings.get().displayName());
		MenuFlow<TurfPowerupFlowSession> flow = MenuFlow.builder(inventoryService, plugin, player, session)
		                                                .panel(PANEL, this)
		                                                .build();
		flow.openAt(PANEL);
	}

	@Override
	public int rows(TurfPowerupFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(TurfPowerupFlowSession session) {
		return messages.text("Quartermaster.Place_Title", "&8&l[&6&lPlace %npc%&8&l]",
		                     "npc", session.getNpcDisplayName());
	}

	@Override
	public void render(MenuFlow<TurfPowerupFlowSession> flow, ChestMenuBuilder builder,
	                   TurfPowerupFlowSession session) {
		Turf   turf = session.getTurf();
		String npc  = session.getNpcDisplayName();

		String name = npcs.get(turf.getId()) != null
				? messages.text("Quartermaster.Place_Move_Name", "&a&lMove the %npc% here", "npc", npc)
				: messages.text("Quartermaster.Place_Name", "&a&lPlace the %npc% here", "npc", npc);
		ItemBuilder place = new ItemBuilder(Material.EMERALD)
				.setDisplayName(name)
				.setLore(messages.text("Quartermaster.Place_Lore_Turf", "&7Turf: &f%turf%",
				                       "turf", turf.getDisplayName()),
				         messages.text("Quartermaster.Place_Lore_Cost", "&7Cost: &6%money% &7from the gang bank",
				                       "money", money(price())),
				         messages.text("Quartermaster.Place_Lore_Click", "&aClick to place it where you stand."));
		builder.slot(SLOT_PLACE, ItemComponent.of(place)
		                                      .onAnyClick(ctx -> confirm(flow, ctx.player(), session)));

		ItemBuilder close = new ItemBuilder(Material.BARRIER)
				.setDisplayName(messages.text("Quartermaster.Close", "&cClose"));
		builder.slot(SLOT_CLOSE, ItemComponent.of(close).onAnyClick(ctx -> flow.end()));

		builder.fill(FillComponent.of(materialOf(fillItem)).name(fillName));
	}

	/** Re-checks everything, takes the price from the gang bank and places the Quartermaster at the player. */
	void confirm(MenuFlow<TurfPowerupFlowSession> flow, Player player, TurfPowerupFlowSession session) {
		Turf turf = turfs.get(session.getTurf().getId());
		if (turf == null) {
			player.sendMessage(QuartermasterAccess.refusal(messages, QuartermasterAccess.TURF_GONE));
			flow.end();
			return;
		}
		String deny = denyKey(player, turf);
		if (deny != null) {
			player.sendMessage(refusalLine(deny, turf));
			flow.end();
			return;
		}
		Turf at = turfs.findAt(player.getLocation());
		if (at == null || at.getId() != turf.getId()) {
			player.sendMessage(refusalLine(PLACE_OUTSIDE, turf));
			flow.end();
			return;
		}

		BigDecimal price = price();
		Gang       gang  = gangs.findById(turf.getOwnerGangId());
		if (price.signum() > 0) {
			if (gang.getEconomy().getAmount().compareTo(price) < 0) {
				player.sendMessage(cannotAfford(price));
				flow.rerender();
				return;
			}
			try {
				gang.getEconomy().withdrawAmount(price);
			} catch (EconomyException exception) {
				player.sendMessage(cannotAfford(price));
				flow.rerender();
				return;
			}
		}

		TurfPowerupNpc placed = npcs.place(turf.getId(), player.getLocation(), null);
		if (placed == null) {
			// place() returns null when the spawn fails (bad Type_Id, Citizens spawn cancelled); refund, never announce.
			// T-804: place() saved the anchor before the failed spawn; drop it so a later chunk load or restart cannot
			// spawn a Quartermaster the gang was refunded for.
			npcs.remove(turf.getId());
			if (price.signum() > 0) {
				gang.getEconomy().depositAmount(price);
			}
			log.warn("Quartermaster: could not place the Quartermaster of turf {} (type {}); {} was not charged",
			         turf.getId(), settings.get().typeId(), gang.getName());
			player.sendMessage(messages.text("Quartermaster.Place_Failed",
			                                 "&cThe %npc% could not be placed here. Your gang was not charged.",
			                                 "npc", npcName()));
			flow.end();
			return;
		}
		log.info("Quartermaster: {} ({}) placed the Quartermaster of turf {} ({}) for {}",
		         player.getName(), gang.getName(), turf.getId(), turf.getDisplayName(), money(price));
		player.sendMessage(messages.text("Quartermaster.Placed", "&aThe %npc% now stands here on &f%turf%&a. Paid &6%money%&a.",
		                                 "npc", npcName(), "turf", turf.getDisplayName(), "money", money(price)));
		flow.end();
	}

	BigDecimal price() {
		return BigDecimal.valueOf(Math.max(0.0, settings.get().placementCost())).setScale(0, RoundingMode.HALF_UP);
	}

	private String npcName() {
		return settings.get().displayName();
	}

	private String cannotAfford(BigDecimal price) {
		return messages.text("Quartermaster.Cannot_Afford", "&cYour gang bank can't cover &f%money%&c.",
		                     "money", money(price));
	}

	private String refusalLine(String key, Turf turf) {
		return switch (key) {
			case PLACE_NOT_OWNER -> messages.text("Quartermaster.Place_Not_Owner",
			                                      "&cOnly the gang that owns %turf% can place its %npc%.",
			                                      "turf", turf.getDisplayName(), "npc", npcName());
			case PLACE_NO_CITIZENS -> messages.text("Quartermaster.Place_No_Citizens",
			                                        "&cThe %npc% needs the Citizens plugin, which this server does not have.",
			                                        "npc", npcName());
			case PLACE_OUTSIDE -> messages.text("Quartermaster.Place_Outside",
			                                    "&cStand inside %turf% to place its %npc%.",
			                                    "turf", turf.getDisplayName(), "npc", npcName());
			case GANG_MISSING -> messages.text("Quartermaster.Gang_Missing",
			                                   "&cGang record missing — contact an admin.");
			default -> QuartermasterAccess.refusal(messages, key);
		};
	}

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}
}
