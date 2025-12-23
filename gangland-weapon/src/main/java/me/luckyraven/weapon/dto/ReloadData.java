package me.luckyraven.weapon.dto;

import lombok.Builder;
import lombok.Getter;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.reload.ReloadType;

@Getter
@Builder
public class ReloadData {

	private final int        maxMagCapacity;
	private final int        cooldown;
	private final Ammunition ammoType;
	private final int        consume;
	private final int        restore;
	private final ReloadType type;

	@Override
	public String toString() {
		return String.format("ReloadData{maxMagCapacity=%d,cooldown=%d,ammoType=%s,consume=%d,restore=%d,type=%s}",
							 maxMagCapacity, cooldown, ammoType, consume, restore, type);
	}

}
