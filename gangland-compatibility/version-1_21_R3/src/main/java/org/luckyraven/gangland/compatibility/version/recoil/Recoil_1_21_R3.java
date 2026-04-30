package org.luckyraven.gangland.compatibility.version.recoil;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.v1_21_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.compatibility.recoil.RecoilCompatibility;

import java.util.Set;

public class Recoil_1_21_R3 extends RecoilCompatibility {

	@Override
	public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean position) {
		float newYaw   = -yaw + 1;
		float newPitch = pitch - 1;

		// Zero position vector since we only modify rotation
		Vec3 zeroPosition = Vec3.ZERO;
		Vec3 zeroDelta    = Vec3.ZERO;

		// Create position move rotation with only yaw/pitch changes
		PositionMoveRotation moveRotation = new PositionMoveRotation(zeroPosition, zeroDelta, newYaw, newPitch);

		// All values are relative (player's current position/rotation + the delta we provide)
		Set<Relative> relativeFlags = Set.of(Relative.X, Relative.Y, Relative.Z, Relative.Y_ROT, Relative.X_ROT);

		// Create the position packet (teleport ID 0 for immediate)
		ClientboundPlayerPositionPacket packet = new ClientboundPlayerPositionPacket(0, moveRotation, relativeFlags);

		// Send packet to player
		CraftPlayer craftPlayer = (CraftPlayer) player;
		craftPlayer.getHandle().connection.send(packet);
	}

}
