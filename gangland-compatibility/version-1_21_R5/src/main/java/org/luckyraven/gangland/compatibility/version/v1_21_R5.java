package org.luckyraven.gangland.compatibility.version;

import org.luckyraven.gangland.compatibility.Compatibility;
import org.luckyraven.gangland.compatibility.recoil.RecoilCompatibility;
import org.luckyraven.gangland.compatibility.version.recoil.Recoil_1_21_R5;

public class v1_21_R5 implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;

	public v1_21_R5() {
		this.recoilCompatibility = new Recoil_1_21_R5();
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}

}
