package org.luckyraven.gangland.compatibility.version;

import org.luckyraven.gangland.compatibility.Compatibility;
import org.luckyraven.gangland.compatibility.recoil.RecoilCompatibility;
import org.luckyraven.gangland.compatibility.version.recoil.Recoil_1_20_R3;

public class v1_20_R3 implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;

	public v1_20_R3() {
		this.recoilCompatibility = new Recoil_1_20_R3();
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}

}
