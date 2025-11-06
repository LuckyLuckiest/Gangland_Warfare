package me.luckyraven.compatibility.version;

import me.luckyraven.compatibility.Compatibility;
import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.compatibility.version.recoil.Recoil_1_19_R3;

public class v1_19_R3 implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;

	public v1_19_R3() {
		this.recoilCompatibility = new Recoil_1_19_R3();
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}
}
