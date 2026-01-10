package me.luckyraven.compatibility;

import me.luckyraven.compatibility.pathfinding.PathfindingHandler;
import me.luckyraven.compatibility.recoil.RecoilCompatibility;

public interface Compatibility {

	RecoilCompatibility getRecoilCompatibility();

	PathfindingHandler getPathfindingHandler();

}
