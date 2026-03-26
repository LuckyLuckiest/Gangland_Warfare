package me.luckyraven.weapon.dto;

import lombok.Builder;
import lombok.Getter;
import me.luckyraven.weapon.reload.ReloadType;

@Getter
@Builder
public class ReloadData {

	private final int        cooldown;
	private final ReloadType type;

	@Override
	public String toString() {
		return String.format("ReloadData{cooldown=%d,type=%s}", cooldown, type);
	}

}
