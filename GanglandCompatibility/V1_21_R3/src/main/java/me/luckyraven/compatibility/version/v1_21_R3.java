package me.luckyraven.compatibility.version;

import me.luckyraven.compatibility.Compatibility;
import me.luckyraven.compatibility.version.recoil.Recoil_1_21_R3;
import me.luckyraven.feature.weapon.projectile.recoil.RecoilCompatibility;

public class v1_21_R3 implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;

	public v1_21_R3() {
		this.recoilCompatibility = new Recoil_1_21_R3();
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}
}
