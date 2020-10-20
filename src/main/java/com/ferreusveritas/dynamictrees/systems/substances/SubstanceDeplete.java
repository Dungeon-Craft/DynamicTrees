package com.ferreusveritas.dynamictrees.systems.substances;

import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.api.substances.ISubstanceEffect;
import com.ferreusveritas.dynamictrees.blocks.BlockRooty;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SubstanceDeplete implements ISubstanceEffect {
	
	int amount;
	
	@Override
	public boolean apply(World world, BlockPos rootPos) {
		final BlockRooty dirt = TreeHelper.getRooty(world.getBlockState(rootPos));

		if (dirt.fertilize(world, rootPos, -amount)) {
			TreeHelper.treeParticles(world, rootPos, ParticleTypes.ANGRY_VILLAGER, 8);
			return true;
		}

		return false;
	}
	
	@Override
	public boolean update(World world, BlockPos rootPos, int deltaTicks) {
		return false;
	}
	
	@Override
	public String getName() {
		return "deplete";
	}
	
	public SubstanceDeplete setAmount(int amount) {
		this.amount = amount;
		return this;
	}
	
	@Override
	public boolean isLingering() {
		return false;
	}
	
}
