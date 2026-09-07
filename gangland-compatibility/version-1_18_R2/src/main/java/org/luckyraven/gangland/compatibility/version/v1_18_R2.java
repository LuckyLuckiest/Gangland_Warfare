package org.luckyraven.gangland.compatibility.version;

import org.luckyraven.gangland.compatibility.Compatibility;
import org.luckyraven.gangland.compatibility.recoil.RecoilCompatibility;
import org.luckyraven.gangland.compatibility.version.recoil.Recoil_1_18_R2;

public class v1_18_R2 implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;

	public v1_18_R2() {
		this.recoilCompatibility = new Recoil_1_18_R2();
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}

}
