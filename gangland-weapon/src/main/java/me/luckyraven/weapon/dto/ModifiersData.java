package me.luckyraven.weapon.dto;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.weapon.modifiers.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ModifiersData implements Cloneable {

	private List<BlockBreakModifier> breakBlocks;
	private PenetrationModifier      penetration;
	private List<RicochetModifier>   ricochets;
	private TracerModifier           tracer;
	private ArmorPiercingModifier    armorPiercing;

	public ModifiersData() {
		this.breakBlocks = new ArrayList<>();
		this.ricochets   = new ArrayList<>();
	}

	public void addBreakBlock(BlockBreakModifier modifier) {
		breakBlocks.add(modifier);
	}

	public void addRicochet(RicochetModifier modifier) {
		ricochets.add(modifier);
	}

	public boolean hasPenetration() {
		return penetration != null;
	}

	public boolean hasRicochet() {
		return !ricochets.isEmpty();
	}

	public boolean hasTracer() {
		return tracer != null;
	}

	public boolean hasArmorPiercing() {
		return armorPiercing != null;
	}

	@Override
	public ModifiersData clone() {
		try {
			ModifiersData clone = (ModifiersData) super.clone();

			clone.breakBlocks = new ArrayList<>(breakBlocks);

			return clone;
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
