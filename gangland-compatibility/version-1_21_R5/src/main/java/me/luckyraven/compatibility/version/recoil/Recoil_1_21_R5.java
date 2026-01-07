package me.luckyraven.compatibility.version.recoil;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import net.minecraft.network.protocol.game.PacketPlayOutPosition;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.craftbukkit.v1_21_R5.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Recoil_1_21_R5 extends RecoilCompatibility {

	@Override
	public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean position) {
		float newYaw   = -yaw + 1;
		float newPitch = pitch - 1;

		// Use zero vector for position since we only want to change rotation (relative movement)
		Vec3D zeroPosition = new Vec3D(0, 0, 0);

		PositionMoveRotation moveRotation = new PositionMoveRotation(zeroPosition, zeroPosition, newYaw, newPitch);

		// move the yaw and pitch only - include position flags as relative
		Set<Relative> rotationFlags = new HashSet<>(Arrays.asList(
				Relative.a, Relative.b, Relative.c, // X, Y, Z (relative)
				Relative.d, Relative.e              // Yaw, Pitch (relative)
		));
		PacketPlayOutPosition packet = new PacketPlayOutPosition(0, moveRotation, rotationFlags);

		(((CraftPlayer) player).getHandle()).g.b(packet);
	}

}
