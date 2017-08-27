package com.ferreusveritas.dynamictrees.special;

import java.util.Random;

import com.ferreusveritas.dynamictrees.trees.DynamicTree;

import net.minecraft.world.World;

public interface IBottomListener {

	//Override to create a custom special effect
	public void run(World world, DynamicTree tree, int x, int y, int z, Random random);

	//Returns 0 to 1 chance of something happening. Must return a positive non-zero number.  Return 1 to always run
	public float chance();

	//Name of the special effect
	public String getName();
}