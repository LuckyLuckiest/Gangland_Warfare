package me.luckyraven.compatibility.version.recoil;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.RelativeMovement;
import org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class Recoil_1_21_R1 extends RecoilCompatibility {

	@Override
	public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean position) {
		float newYaw   = -yaw + 1;
		float newPitch = pitch - 1;

		// Use zero deltas (relative movement)
		double x = 0.0;
		double y = 0.0;
		double z = 0.0;

		// Relative movement flags (ONLY rotation if position == false)
		Set<RelativeMovement> flags = position ?
									  Set.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z,
											 RelativeMovement.Y_ROT, RelativeMovement.X_ROT) :
									  Set.of(RelativeMovement.Y_ROT, RelativeMovement.X_ROT);

		// Teleport ID (0 is fine for instant updates)
		ClientboundPlayerPositionPacket packet = new ClientboundPlayerPositionPacket(x, y, z, newYaw, newPitch, flags,
																					 0);

		CraftPlayer craftPlayer = (CraftPlayer) player;
		craftPlayer.getHandle().connection.send(packet);
	}

}
