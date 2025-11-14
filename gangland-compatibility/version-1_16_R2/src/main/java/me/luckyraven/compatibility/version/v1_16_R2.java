package me.luckyraven.compatibility.version;

import me.luckyraven.compatibility.Compatibility;
import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.compatibility.version.recoil.Recoil_1_16_R2;

public class v1_16_R2 implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;

	public v1_16_R2() {
		this.recoilCompatibility = new Recoil_1_16_R2();
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}
}
