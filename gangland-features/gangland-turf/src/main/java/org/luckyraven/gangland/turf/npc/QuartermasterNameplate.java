package org.luckyraven.gangland.turf.npc;

import lombok.CustomLog;
import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.HologramTrait;
import org.bukkit.ChatColor;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.npc.NpcSupport;

/**
 * Draws the owning gang's short tag ({@link GangTag}) as a Citizens hologram line above a Quartermaster's nameplate
 * (D27=C). The nameplate itself stays the civilians.yml type name; the line follows every owner change (via
 * {@code QuartermasterNameplateListener}) and is drawn at spawn through the {@link TurfPowerupManager} spawn hook.
 */
@CustomLog
public final class QuartermasterNameplate {

	static final int    MAX_PLAIN = 16;
	static final String DATA_KEY  = "gangland-turf-owner-line";

	private final TurfManager        turfs;
	private final GangLookupContract gangs;
	private final TurfPowerupManager npcs;
	private final TurfMessages       messages;

	public QuartermasterNameplate(TurfManager turfs, GangLookupContract gangs, TurfPowerupManager npcs,
	                              TurfMessages messages) {
		this.turfs    = turfs;
		this.gangs    = gangs;
		this.npcs     = npcs;
		this.messages = messages;
		npcs.setSpawnHook(this::apply);
	}

	/** The coloured line for {@code turfId}, or "" when the turf is gone, unowned or its gang record is missing. */
	String lineFor(int turfId) {
		Turf turf = turfs.get(turfId);
		if (turf == null || turf.getOwnerGangId() == null) return "";
		Gang gang = gangs.findById(turf.getOwnerGangId());
		if (gang == null) return "";

		String tag  = GangTag.of(GangDisplayNameResolver.resolve(gang), gang.getId());
		String line = messages.text("Quartermaster.Nameplate_Tag", "&8[&e%tag%&8]", "tag", tag);
		if (ChatColor.stripColor(line).length() > MAX_PLAIN) {
			return GanglandChatUtil.color("&8[&e" + tag + "&8]");
		}
		return line;
	}

	public void refresh(int turfId) {
		TurfPowerupNpc npc = npcs.get(turfId);
		if (npc != null && npc.isAlive()) apply(npc);
	}

	void apply(TurfPowerupNpc npc) {
		int    turfId = npc.getData().getTurfId();
		String line   = lineFor(turfId);
		log.debug("Quartermaster nameplate for turf {}: {}", turfId, line.isEmpty() ? "(none)" : ChatColor.stripColor(line));
		if (!NpcSupport.available()) return;
		CitizensBridge.show(npc.getCivilian().getNpc(), line);
	}

	/** Citizens types live only in this class's signatures, so it is never loaded without Citizens. */
	static final class CitizensBridge {

		private CitizensBridge() {
		}

		static void show(NPC npc, String line) {
			MetadataStore data     = npc.data();
			String        previous = data.has(DATA_KEY) ? data.get(DATA_KEY) : null;

			HologramTrait hologram = line.isEmpty() ? npc.getTraitNullable(HologramTrait.class)
			                                        : npc.getOrAddTrait(HologramTrait.class);
			if (hologram == null) {
				data.remove(DATA_KEY);
				return;
			}
			int index = previous == null ? -1 : hologram.getLines().indexOf(previous);

			if (line.isEmpty()) {
				if (index >= 0) {
					hologram.removeLine(index);
					hologram.onDespawn();
				}
				data.remove(DATA_KEY);
				return;
			}
			if (index >= 0) {
				if (!line.equals(previous)) hologram.setLine(index, line);
			} else {
				hologram.addLine(line);
			}
			data.set(DATA_KEY, line);
		}
	}
}
