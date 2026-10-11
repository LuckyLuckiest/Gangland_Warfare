package org.luckyraven.gangland.turf.map;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.config.TurfMapSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.TurfGeometry;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.state.TurfState;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/**
 * The {@code /glw map} data path: turfs of the viewer's world -> {@link TerritoryRaster} -> {@link TurfMapRenderer}
 * components, the header, the hovers and the text form. The only class here that touches Bukkit or a Player.
 * Every string goes through {@link TurfMessages#text}, with an in-code fallback equal to the shipped line.
 */
public final class TurfMapService {

	private static final long ONLINE_NOW_MILLIS = 120_000L;
	private static final int  TEXT_ROWS        = 10;

	private final TurfManager           turfs;
	private final GangLookupContract    gangs;
	private final UserLookupContract    users;
	private final CaptureEligibility    eligibility;
	private final TurfMapSettings       mapSettings;
	private final TurfSettings          turfSettings;
	private final TurfMessages          text;
	private final TurfMessageContract   legacy;
	private final GarrisonManager       garrisons;
	private final TurfMapRenderer       renderer = new TurfMapRenderer();
	private final MapPrefsService       prefs;

	public TurfMapService(TurfManager turfs, GangLookupContract gangs, UserLookupContract users,
	                      CaptureEligibility eligibility, TurfMapSettings mapSettings, TurfSettings turfSettings,
	                      TurfMessages text, TurfMessageContract legacy, GarrisonManager garrisons,
	                      MapPrefsService prefs) {
		this.prefs        = prefs;
		this.turfs        = turfs;
		this.gangs        = gangs;
		this.users        = users;
		this.eligibility  = eligibility;
		this.mapSettings  = mapSettings;
		this.turfSettings = turfSettings;
		this.text         = text;
		this.legacy       = legacy;
		this.garrisons    = garrisons;
	}

	/** The raster of the viewer's window; turfs of the viewer's world only (a null world has none). */
	public TerritoryRaster.Raster raster(Player viewer) {
		Location        at          = viewer.getLocation();
		Integer         viewerGang  = viewerGangId(viewer);
		long            now         = System.currentTimeMillis();
		List<Turf>      inWorld     = turfsInWorld(at);
		List<TerritoryRaster.Input>               inputs = new ArrayList<>();
		Map<Integer, TerritoryRaster.GangName>    names  = new HashMap<>();
		for (Turf turf : inWorld) {
			TurfRelation relation  = TurfRelation.of(turf, viewerGang, gangs);
			TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
			boolean underAttack = state != null && state.getState() == TurfState.CONTESTING;
			// T-231: a gangless viewer never gets shield styling from "attackable for him" (he cannot capture at all).
			boolean shielded    = viewerGang != null
			                      && (relation == TurfRelation.RIVAL || relation == TurfRelation.UNCLAIMED)
			                      && !eligibility.evaluate(turf, now).attackable();
			inputs.add(new TerritoryRaster.Input(turf.getId(), turf.getDisplayName(), turf.getRegion(), relation,
			                                     turf.getOwnerGangId(), underAttack, shielded));
			Integer ownerId = turf.getOwnerGangId();
			if (ownerId != null && !names.containsKey(ownerId)) {
				Gang owner = gangs.findById(ownerId);
				if (owner != null) {
					names.put(ownerId, new TerritoryRaster.GangName(ownerId, nz(owner.getDisplayName()),
					                                                nz(owner.getName()), null));
				}
			}
		}
		return TerritoryRaster.render(at.getBlockX(), at.getBlockZ(), mapSettings.getWidth(), mapSettings.getHeight(),
		                              mapSettings.getCellBlocks(), inputs, names, viewerGang);
	}

	/** Chat grid, or the text form for a Bedrock player when Map.Bedrock_Text_Form is on. */
	public void send(Player viewer) {
		if (mapSettings.isBedrockTextForm() && isBedrock(viewer.getUniqueId())) {
			sendText(viewer);
			return;
		}
		Location              at       = viewer.getLocation();
		Integer               viewerGang = viewerGangId(viewer);
		List<Turf>            inWorld  = turfsInWorld(at);
		TerritoryRaster.Raster raster  = raster(viewer);
		long                  now      = System.currentTimeMillis();
		Map<Integer, Turf>    byId     = byId(inWorld);

		IntFunction<String> hover = id -> {
			Turf turf = byId.get(id);
			return turf == null ? "" : hoverText(viewer, turf, viewerGang, now);
		};
		List<BaseComponent[]> lines = renderer.lines(header(at, raster, inWorld, viewerGang), raster, hover,
		                                             plusHover(viewer, raster, byId), legendLines(raster));
		for (BaseComponent[] line : lines) {
			viewer.spigot().sendMessage(line);
		}
	}

	/** The nearest turfs of the viewer's world, one clickable line each, at most ten. */
	public void sendText(Player viewer) {
		Location   at      = viewer.getLocation();
		List<Turf> inWorld = turfsInWorld(at);
		if (inWorld.isEmpty()) {
			viewer.sendMessage(text.text("Map.Header_None", "&7No turf in this world."));
			return;
		}
		Integer viewerGang = viewerGangId(viewer);
		long    now        = System.currentTimeMillis();
		int     x          = at.getBlockX();
		int     z          = at.getBlockZ();
		List<Turf> nearest = inWorld.stream()
				.sorted(Comparator.comparingInt((Turf turf) -> TurfGeometry.distance(turf.getRegion(), x, z))
						        .thenComparingInt(Turf::getId))
				.limit(TEXT_ROWS)
				.toList();
		for (Turf turf : nearest) {
			TurfGeometry.Nearest where = TurfGeometry.nearest(List.of(turf), x, z);
			String owner = turf.isUnclaimed()
			               ? text.text("Map.Text_Unclaimed", "Unclaimed")
			               : GangDisplayNameResolver.resolve(gangs.findById(turf.getOwnerGangId()));
			String reason = textReason(turf, owner, viewerGang, now);
			String legacyLine = text.text("Map.Text_Row",
			                              "&f%name% &7- &f%owner% &7(%relation%) - %distance%m %direction% - %reason%",
			                              "name", turf.getDisplayName(),
			                              "owner", owner,
			                              "relation", relationWord(TurfRelation.of(turf, viewerGang, gangs)),
			                              "distance", where.distance(),
			                              "direction", where.direction().isEmpty()
			                                           ? text.text("Map.Text_Here", "here")
			                                           : where.direction(),
			                              "reason", reason);
			viewer.spigot().sendMessage(renderer.clickable(legacyLine, turf.getId(),
			                                               hoverText(viewer, turf, viewerGang, now)));
		}
	}

	/**
	 * Reason column of a text row: the gangless sentence for a viewer without a gang (as the HUD), "under attack" while
	 * a capture runs (as the HUD and hover), else the eligibility verdict.
	 */
	String textReason(Turf turf, String owner, @Nullable Integer viewerGang, long now) {
		if (viewerGang == null) {
			return gangless();
		}
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		if (TurfHudLine.isContesting(state)) {
			Gang challenger = state.getChallengerGangId() == null ? null : gangs.findById(state.getChallengerGangId());
			return TurfHudLine.underAttack(text, state, GangDisplayNameResolver.resolve(challenger));
		}
		return text.reason(eligibility.evaluate(turf, now), owner, now);
	}

	/** The one reason a player without a gang sees, the same key and text the HUD uses (Hud.Gangless). */
	private String gangless() {
		return text.text("Hud.Gangless", "&ejoin a gang to capture");
	}

	/** One line per gang in view with its relation and state, for {@code /glw map legend}. */
	public void sendLegend(Player viewer) {
		TerritoryRaster.Raster raster = raster(viewer);
		if (raster.legend().isEmpty()) {
			viewer.sendMessage(text.text("Map.Legend_Empty", "&7No gang in view."));
			return;
		}
		for (TerritoryRaster.LegendEntry entry : raster.legend()) {
			String row = text.text("Map.Legend_Row", "&7%name% %relation%",
			                       "name", entry.fullName(), "relation", legendWord(entry.relation())).trim();
			if (entry.shielded()) {
				row += " " + text.text("Map.Legend_Shielded", "&7shielded");
			}
			if (entry.underAttack()) {
				row += " " + text.text("Map.Legend_Under_Attack", "&7under attack");
			}
			viewer.spigot().sendMessage(renderer.legendRow(entry, row));
		}
	}

	/** Hover of one turf run: facts a viewer may see; guards and online time only for the owning gang. */
	String hoverText(Player viewer, Turf turf, @Nullable Integer viewerGang, long now) {
		List<String> lines = new ArrayList<>();
		lines.add(text.text("Map.Hover_Name", "&f%name%", "name", turf.getDisplayName()));

		Gang owner = turf.getOwnerGangId() == null ? null : gangs.findById(turf.getOwnerGangId());
		String ownerName = owner == null ? "" : GangDisplayNameResolver.resolve(owner);
		lines.add(turf.isUnclaimed()
		          ? text.text("Map.Hover_Unclaimed", "&7Owner &fUnclaimed")
		          : text.text("Map.Hover_Owner", "&7Owner &f%gang%", "gang", ownerName));

		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		if (state != null && state.getState() == TurfState.CONTESTING) {
			Gang challenger = state.getChallengerGangId() == null ? null : gangs.findById(state.getChallengerGangId());
			lines.add(text.text("Map.Hover_Under_Attack", "&cUnder attack by %gang%, %percent%%",
			                    "gang", GangDisplayNameResolver.resolve(challenger),
			                    "percent", Math.round(state.getCaptureProgress())));
		} else {
			lines.add(text.text("Map.Hover_Quiet", "&7Quiet"));
		}

		lines.add(viewerGang == null
		          ? gangless()
		          : text.reason(eligibility.evaluate(turf, now), ownerName, now));

		boolean ownGang = owner != null && viewerGang != null && viewerGang.equals(turf.getOwnerGangId());
		if (ownGang) {
			long last = owner.getLastMemberOnlineAt();
			lines.add(now - last <= ONLINE_NOW_MILLIS
			          ? text.text("Map.Hover_Online_Now", "&7Members &aonline now")
			          : text.text("Map.Hover_Online", "&7Last member online &f%time% &7ago",
			                      "time", legacy.formatDuration(Math.max(1L, (now - last) / 1000L))));
			lines.add(text.text("Map.Hover_Guards", "&7Your guards: &f%count% &7in reserve",
			                    "count", garrisons.count(turf.getId())));
		}

		BigDecimal income = turf.getIncomeAmount();
		lines.add(income != null && income.signum() > 0
		          ? text.text("Map.Hover_Pays", "&7Pays &f%money_symbol%%amount% &7per %time%",
		                      "money_symbol", Settings.getMoneySymbol(),
		                      "amount", Settings.formatAmount(income),
		                      "time", legacy.formatDuration(turfSettings.getIncomeIntervalMinutes() * 60L))
		          : text.text("Map.Hover_Dormant", "&7Pays &fnothing &7(dormant)"));

		if (viewer.hasPermission(WandSelectionManager.ADMIN_PERMISSION)) {
			lines.add(text.text("Map.Hover_Admin_Tp", "&7Admin: &f/glw turf tp %id%", "id", turf.getId()));
		}
		return String.join("\n", lines);
	}

	private String plusHover(Player viewer, TerritoryRaster.Raster raster, Map<Integer, Turf> byId) {
		Location at = viewer.getLocation();
		Turf under = byId.get(raster.viewerTurfId());
		String place = under == null
		               ? text.text("Map.Hover_You_No_Turf", "no turf")
		               : under.getDisplayName();
		double radians = Math.toRadians(at.getYaw());
		int    dx      = (int) Math.round(-Math.sin(radians) * 100);
		int    dz      = (int) Math.round(Math.cos(radians) * 100);
		return text.text("Map.Hover_You", "&fYou&7: %place% &8(%x%, %z%) &7facing %facing%",
		                 "place", place, "x", at.getBlockX(), "z", at.getBlockZ(),
		                 "facing", TurfGeometry.bearing(0, 0, dx, dz));
	}

	private String header(Location at, TerritoryRaster.Raster raster, List<Turf> inWorld,
	                      @Nullable Integer viewerGang) {
		if (inWorld.isEmpty()) {
			return text.text("Map.Header_None", "&7No turf in this world.");
		}
		if (raster.visibleTurfs() == 0) {
			TurfGeometry.Nearest nearest = TurfGeometry.nearest(inWorld, at.getBlockX(), at.getBlockZ());
			return text.text("Map.Header_Empty", "&7No turf in view - nearest &f%turf% &7%distance%m %direction%",
			                 "turf", nearest == null ? "" : nearest.turf().getDisplayName(),
			                 "distance", nearest == null ? 0 : nearest.distance(),
			                 "direction", nearest == null ? "" : nearest.direction());
		}
		if (viewerGang == null) {
			return text.text("Map.Header_Gangless", "&7Map - north is up - &f+ &7you");
		}
		long owned = turfs.getAll().stream().filter(turf -> viewerGang.equals(turf.getOwnerGangId())).count();
		return text.text("Map.Header", "&7Map - &fTurfs %owned% &7- north is up - &f+ &7you", "owned", owned);
	}

	private List<BaseComponent[]> legendLines(TerritoryRaster.Raster raster) {
		String you   = text.text("Map.Legend_You", "(you)");
		String ally  = text.text("Map.Legend_Ally", "(ally)");
		String rival = text.text("Map.Legend_Rival", "(rival)");
		Function<TerritoryRaster.LegendEntry, String> label = entry -> LegendLine.label(entry, you, ally, rival);
		LegendLine.Packed packed = LegendLine.pack(raster.legend(), label, this::legendMore);

		String hidden = packed.hidden()
				.stream()
				.map(entry -> entry.glyph() + " " + entry.fullName())
				.collect(Collectors.joining(", "));
		BaseComponent[] first = renderer.legendLine(packed, label,
		                                            legendMore(packed.hidden().size()),
		                                            text.text("Map.Legend_More_Hover", "&7Not shown: %list%",
		                                                      "list", hidden));
		BaseComponent[] key = TextComponent.fromLegacyText(text.text("Map.Legend_Key",
		                                                             "&7# unclaimed  &8- &7none  &7&oitalic&r &7attacked  &7&nunderlined&r &7shielded"));
		List<BaseComponent[]> lines = new ArrayList<>();
		lines.add(first);
		lines.add(key);
		return lines;
	}

	private String legendMore(int count) {
		return text.text("Map.Legend_More", "+%count% more", "count", count);
	}

	private String legendWord(TurfRelation relation) {
		return switch (relation) {
			case OWN -> text.text("Map.Legend_You", "(you)");
			case ALLY -> text.text("Map.Legend_Ally", "(ally)");
			case RIVAL -> text.text("Map.Legend_Rival", "(rival)");
			default -> "";
		};
	}

	private String relationWord(TurfRelation relation) {
		return switch (relation) {
			case OWN -> text.text("Map.Relation_Own", "own");
			case ALLY -> text.text("Map.Relation_Ally", "ally");
			case RIVAL -> text.text("Map.Relation_Rival", "rival");
			case UNCLAIMED -> text.text("Map.Relation_Unclaimed", "unclaimed");
			case GANGLESS -> text.text("Map.Relation_Other", "other");
		};
	}

	private @Nullable Integer viewerGangId(Player viewer) {
		User<Player> user = users.findByPlayer(viewer);
		return user != null && user.hasGang() ? user.getGangId() : null;
	}

	private List<Turf> turfsInWorld(Location at) {
		World world = at.getWorld();
		return world == null ? List.of() : turfs.getTurfsInWorld(world.getName());
	}

	private static Map<Integer, Turf> byId(List<Turf> list) {
		Map<Integer, Turf> map = new HashMap<>();
		for (Turf turf : list) {
			map.put(turf.getId(), turf);
		}
		return map;
	}

	static boolean isBedrock(UUID uuid) {
		return uuid.getMostSignificantBits() == 0L;
	}

	private static String nz(@Nullable String value) {
		return value == null ? "" : value;
	}
}
