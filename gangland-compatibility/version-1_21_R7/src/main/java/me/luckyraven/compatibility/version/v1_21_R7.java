package me.luckyraven.compatibility.version;

import me.luckyraven.compatibility.Compatibility;
import me.luckyraven.compatibility.pathfinding.PathfindingHandler;
import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.compatibility.version.pathfinding.PathfindingHandler_v1_21_R7;
import me.luckyraven.compatibility.version.recoil.Recoil_1_21_R7;

public class v1_21_R7 implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;
	private final PathfindingHandler  pathfindingHandler;

	public v1_21_R7() {
		this.recoilCompatibility = new Recoil_1_21_R7();
		this.pathfindingHandler  = new PathfindingHandler_v1_21_R7();
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}

	@Override
	public PathfindingHandler getPathfindingHandler() {
		return pathfindingHandler;
	}

}
