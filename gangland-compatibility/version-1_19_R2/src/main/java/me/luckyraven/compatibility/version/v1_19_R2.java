package me.luckyraven.compatibility.version;

import me.luckyraven.compatibility.Compatibility;
import me.luckyraven.compatibility.version.recoil.Recoil_1_19_R2;
import me.luckyraven.feature.weapon.projectile.recoil.RecoilCompatibility;

public class v1_19_R2 implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;

	public v1_19_R2() {
		this.recoilCompatibility = new Recoil_1_19_R2();
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}
}
