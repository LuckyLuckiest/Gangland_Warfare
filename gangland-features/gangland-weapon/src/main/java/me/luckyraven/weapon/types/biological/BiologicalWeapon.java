package me.luckyraven.weapon.types.biological;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.BiologicalData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.types.WeaponType;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BiologicalWeapon extends Weapon {

	private final BiologicalData biologicalData;

	public BiologicalWeapon(UUID uuid, String name, String displayName, WeaponType category, Material material,
	                        int customModelData, short durability, List<String> lore, boolean dropHologram,
	                        @Nullable List<String> deathMessages,
	                        BiologicalData biologicalData, @Nullable ReloadData reloadData,
	                        @Nullable AmmunitionData ammunitionData) {
		super(uuid, name, displayName, category, material, customModelData, durability, lore, dropHologram,
		      deathMessages, reloadData, ammunitionData);
		this.biologicalData = biologicalData;
	}

	@Override
	public BiologicalWeapon copyWithUUID(UUID newUuid) {
		BiologicalWeapon copy = (BiologicalWeapon) super.clone();
		copy.setUUID(newUuid);
		return copy;
	}

	@Override
	public BiologicalWeapon clone() {
		return (BiologicalWeapon) super.clone();
	}

}
