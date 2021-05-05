package com.ferreusveritas.dynamictrees.items;

import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.api.TreeRegistry;
import com.ferreusveritas.dynamictrees.api.treedata.ITreePart;
import com.ferreusveritas.dynamictrees.blocks.branches.BranchBlock;
import com.ferreusveritas.dynamictrees.blocks.branches.TrunkShellBlock;
import com.ferreusveritas.dynamictrees.init.DTRegistries;
import com.ferreusveritas.dynamictrees.trees.Species;
import com.ferreusveritas.dynamictrees.util.SafeChunkBounds;
import com.ferreusveritas.dynamictrees.worldgen.JoCode;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;

/**
 * Try the following in a command block to demonstrate the extra tag functionality.
 * /give @p dynamictrees:staff{color:"#88FF00",code:"OUiVpPzkbtJ9uSRPbZP",read_only:1,tree:"dynamictrees:birch",max_uses:16,display:{Name:'[{"text":"Name","italic":false}]'}}
 */
public class Staff extends Item {
	
	public final static String HANDLE = "handle";
	public final static String COLOR = "color";
	
	public final static String READ_ONLY = "read_only";
	public final static String TREE = "tree";
	public final static String CODE = "code";
	public final static String USES = "uses";
	public final static String MAX_USES = "max_uses";

	private final Multimap<Attribute, AttributeModifier> attributeModifiers;

	public Staff() {
		super(new Item.Properties().stacksTo(1)
				.tab(DTRegistries.ITEM_GROUP));

		ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
		builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 5.0, AttributeModifier.Operation.ADDITION));
		builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", -2.4, AttributeModifier.Operation.ADDITION));
		this.attributeModifiers = builder.build();
	}
	
	
	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		if(state.getBlock() instanceof BranchBlock || state.getBlock() instanceof TrunkShellBlock) {
			return 64.0f;
		}
		return super.getDestroySpeed(stack, state);
	}
	
	@Override
	public boolean mineBlock(ItemStack stack, World worldIn, BlockState state, BlockPos pos, LivingEntity entityLiving) {
		if(state.getBlock() instanceof BranchBlock || state.getBlock() instanceof TrunkShellBlock) {
			if(decUses(stack)) {
				stack.shrink(1);
			}
			return true;
		}
		return false;
	}
	
	@Override
	public ActionResultType useOn(ItemUseContext context) {
		World world = context.getLevel();
		ItemStack heldStack = context.getPlayer().getItemInHand(context.getHand());

		BlockPos pos = context.getClickedPos();
		BlockState state = world.getBlockState(pos);

		BlockPos rootPos = TreeHelper.findRootNode(world, pos);
		ITreePart treePart = TreeHelper.getTreePart(world.getBlockState(rootPos));

		// Get the code from a tree or rooty dirt and set it in the staff
		if(!isReadOnly(heldStack) && treePart.isRootNode()) {
			Species species = TreeHelper.getExactSpecies(world, rootPos);
			if(species.isValid()) {
				if(!context.getPlayer().isShiftKeyDown()) {
					String code = new JoCode(world, rootPos, context.getPlayer().getDirection()).toString();
					setCode(heldStack, code);
					if(world.isClientSide) { // Make sure this doesn't run on the server
						Minecraft.getInstance().keyboardHandler.setClipboard(code); // Put the code in the system clipboard to annoy everyone.
					}
				}
				setSpecies(heldStack, species);
				return ActionResultType.SUCCESS;
			}
		}
		
		//Create a tree from right clicking on soil
		Species species = getSpecies(heldStack);
		if(species.isValid() && species.isAcceptableSoil(world, pos, state)) {
			species.getJoCode(getCode(heldStack)).setCareful(true).generate(world, world, species, pos, world.getBiome(pos), context.getPlayer().getDirection(), 8, SafeChunkBounds.ANY);
			if(hasMaxUses(heldStack)) {
				if(decUses(heldStack)) {
					heldStack.shrink(1);//If the player is in creative this will have no effect.
				}
			} else {
				heldStack.shrink(1);//If the player is in creative this will have no effect.
			}
			return ActionResultType.SUCCESS;
		}
		
		return ActionResultType.FAIL;
	}
	
	@Override
	public boolean showDurabilityBar(ItemStack stack) {
		return hasMaxUses(stack);
	}

	@Override
	public double getDurabilityForDisplay(ItemStack stack) {
		double damage = getUses(stack) / (double)getMaxUses(stack);
		return 1 - damage;
	}

	public boolean isReadOnly(ItemStack itemStack) {
		return itemStack.getOrCreateTag().getBoolean(READ_ONLY);
	}
	
	public Staff setReadOnly(ItemStack itemStack, boolean readonly) {
		itemStack.getOrCreateTag().putBoolean(READ_ONLY, readonly);
		return this;
	}
	
	public Staff setSpecies(ItemStack itemStack, Species species) {
		String name;

		if (species == Species.NULL_SPECIES) name = "null";
		else name = species.getRegistryName().toString();

		itemStack.getOrCreateTag().putString(TREE, name);
		return this;
	}
	
	public Staff setCode(ItemStack itemStack, String code) {
		itemStack.getOrCreateTag().putString(CODE, code);
		return this;
	}
	
	public Species getSpecies(ItemStack itemStack) {
		CompoundNBT nbt = itemStack.getOrCreateTag();
		
		if (nbt.contains(TREE)) {
			return TreeRegistry.findSpecies(nbt.getString(TREE));
		} else {
			Species species = TreeRegistry.findSpeciesSloppy("oak");
			setSpecies(itemStack, species);
			return species;
		}
	}
	
	public int getUses(ItemStack itemStack) {
		CompoundNBT nbt = itemStack.getOrCreateTag();
		
		if(nbt.contains(USES)) {
			return nbt.getInt(USES);
		} else {
			int uses = getMaxUses(itemStack);
			setUses(itemStack, uses);
			return uses;
		}
		
	}
	
	public Staff setUses(ItemStack itemStack, int value) {
		itemStack.getOrCreateTag().putInt(USES, value);
		return this;
	}
	
	public int getMaxUses(ItemStack itemStack) {
		CompoundNBT nbt = itemStack.getOrCreateTag();
		
		if(nbt.contains(MAX_USES)) {
			return nbt.getInt(MAX_USES);
		}
		
		return 0;
	}
	
	public Staff setMaxUses(ItemStack itemStack, int value) {
		itemStack.getOrCreateTag().putInt(MAX_USES, value);
		return this;
	}
	
	public boolean hasMaxUses(ItemStack itemStack) {
		return itemStack.getOrCreateTag().contains(MAX_USES);
	}
	
	public boolean decUses(ItemStack itemStack) {
		int uses = Math.max(0, getUses(itemStack) - 1);
		setUses(itemStack, uses);
		return uses <= 0;
	}
	
	public int getColor(ItemStack itemStack, int tint) {
		final CompoundNBT nbt = itemStack.getOrCreateTag();

		if (tint == 0) {
			int color = 0x005b472f; // Original brown wood color
			
			Species species = getSpecies(itemStack);
			
			if (nbt.contains(HANDLE)) {
				try {
					color = Color.decode(nbt.getString(HANDLE)).getRGB();
				} catch (NumberFormatException e) {
					nbt.remove(HANDLE);
				}
			}
			else if (species.isValid()) {
				color = species.getFamily().woodBarkColor;
			}
			
			return color;
		} else if (tint == 1) {
			int color = 0x0000FFFF; // Cyan crystal like Radagast the Brown's staff.

			if (nbt.contains(COLOR)) {
				try {
					color = Color.decode(nbt.getString(COLOR)).getRGB();
				} catch (NumberFormatException e) {
					nbt.remove(COLOR);
				}
			}

			return color;
		}
		
		
		return 0xFFFFFFFF;//white
	}
	
	public Staff setColor(ItemStack itemStack, String colStr) {
		itemStack.getOrCreateTag().putString(COLOR, colStr);
		return this;
	}
	
	public String getCode(ItemStack itemStack) {
		String code = "P";//Code of a sapling

		if (itemStack.getOrCreateTag().contains(CODE)) {
			code = itemStack.getTag().getString(CODE);
		} else {
			itemStack.getTag().putString(CODE, code);
		}
		
		return code;
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void appendHoverText(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {
		final Species species = getSpecies(stack);
		tooltip.add(new StringTextComponent("Tree: ").append(species.isValid() ? new StringTextComponent(species.getRegistryName().toString()): new StringTextComponent("none")));
		tooltip.add(new StringTextComponent("Code: ").append(new StringTextComponent(TextFormatting.GOLD + this.getCode(stack))));
	}

	/**
	 * Gets a map of item attribute modifiers, used by ItemSword to increase hit damage.
	 */

	@Override
	public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlotType slot, ItemStack stack) {
		return slot == EquipmentSlotType.MAINHAND ? this.attributeModifiers : super.getAttributeModifiers(slot, stack);
	}

}
