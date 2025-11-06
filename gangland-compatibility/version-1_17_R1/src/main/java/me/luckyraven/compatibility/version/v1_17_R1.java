package me.luckyraven.compatibility.version;

import me.luckyraven.compatibility.Compatibility;
import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.compatibility.version.recoil.Recoil_1_17_R1;

public class v1_17_R1 implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;

	public v1_17_R1() {
		this.recoilCompatibility = new Recoil_1_17_R1();
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}
}
