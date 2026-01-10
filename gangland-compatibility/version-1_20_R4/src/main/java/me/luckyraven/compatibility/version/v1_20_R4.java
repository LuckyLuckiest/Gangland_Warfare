package me.luckyraven.compatibility.version;

import me.luckyraven.compatibility.Compatibility;
import me.luckyraven.compatibility.pathfinding.PathfindingHandler;
import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.compatibility.version.pathfinding.PathfindingHandler_v1_20_R4;
import me.luckyraven.compatibility.version.recoil.Recoil_1_20_R4;

public class v1_20_R4 implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;
	private final PathfindingHandler  pathfindingHandler;

	public v1_20_R4() {
		this.recoilCompatibility = new Recoil_1_20_R4();
		this.pathfindingHandler  = new PathfindingHandler_v1_20_R4();
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
