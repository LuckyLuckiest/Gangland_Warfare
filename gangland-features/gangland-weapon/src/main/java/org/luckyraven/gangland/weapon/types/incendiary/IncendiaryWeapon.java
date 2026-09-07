package org.luckyraven.gangland.weapon.types.incendiary;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.dto.AmmunitionData;
import org.luckyraven.gangland.weapon.dto.IncendiaryData;
import org.luckyraven.gangland.weapon.dto.ReloadData;
import org.luckyraven.gangland.weapon.types.WeaponType;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class IncendiaryWeapon extends Weapon {

	private final IncendiaryData incendiaryData;

	public IncendiaryWeapon(UUID uuid, String name, String displayName, WeaponType category, Material material,
	                        int customModelData, short durability, List<String> lore, boolean dropHologram,
	                        @Nullable List<String> deathMessages,
	                        IncendiaryData incendiaryData, @Nullable ReloadData reloadData,
	                        @Nullable AmmunitionData ammunitionData) {
		super(uuid, name, displayName, category, material, customModelData, durability, lore, dropHologram,
		      deathMessages, reloadData, ammunitionData);
		this.incendiaryData = incendiaryData;
	}

	@Override
	public boolean consumeShot() {
		if (isMagazineEmpty()) return false;
		setCurrentMagCapacity(Math.max(0, getCurrentMagCapacity() - incendiaryData.getConsumeRate()));
		return true;
	}

	@Override
	public IncendiaryWeapon copyWithUUID(UUID newUuid) {
		IncendiaryWeapon copy = (IncendiaryWeapon) super.clone();
		copy.setUUID(newUuid);
		return copy;
	}

	@Override
	public IncendiaryWeapon clone() {
		// No extra mutable fields — Weapon.clone() handles everything.
		return (IncendiaryWeapon) super.clone();
	}

}
