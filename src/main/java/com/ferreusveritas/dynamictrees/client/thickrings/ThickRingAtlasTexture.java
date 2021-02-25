package com.ferreusveritas.dynamictrees.client.thickrings;

import com.ferreusveritas.dynamictrees.DynamicTrees;
import com.ferreusveritas.dynamictrees.api.TreeRegistry;
import com.ferreusveritas.dynamictrees.trees.Species;
import com.ferreusveritas.dynamictrees.trees.TreeFamily;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.StitcherException;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.crash.ReportedException;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ThickRingAtlasTexture extends AtlasTexture {

    private static final Logger LOGGER = LogManager.getLogger();
    private final int maximumTextureSize;

    private static final int spriteSizeMultiplier = 3;

    public static final ResourceLocation LOCATION_THICKRINGS_TEXTURE = new ResourceLocation(DynamicTrees.MOD_ID, "textures/atlas/thick_rings.png");

    public ThickRingAtlasTexture() {
        super(LOCATION_THICKRINGS_TEXTURE);
        maximumTextureSize = RenderSystem.maxSupportedTextureSize();
    }

//    @Override
//    public TextureAtlasSprite getSprite(ResourceLocation resloc) {
//        TextureAtlasSprite sprite = super.getSprite(resloc);
//        if (sprite instanceof ThickRingTextureAtlasSprite){
//            ((ThickRingTextureAtlasSprite) sprite).loadAtlasTexture();
//        }
//        return sprite;
//    }


    private static boolean uploaded = false;
    @Override
    public void upload(SheetData sheetData) {
        if (!uploaded){
            super.upload(sheetData);
            uploaded = true;
        }
    }

    public AtlasTexture.SheetData stitch(IResourceManager resourceManagerIn, Stream<ResourceLocation> resourceLocationsIn, IProfiler profilerIn, int maxMipmapLevelIn) {
        profilerIn.startSection("preparing");
        Set<ResourceLocation> set = resourceLocationsIn.peek((resloc) -> {
            if (resloc == null) {
                throw new IllegalArgumentException("Location cannot be null!");
            }
        }).collect(Collectors.toSet());
        int i = this.maximumTextureSize;
        Stitcher stitcher = new Stitcher(i, i, maxMipmapLevelIn);
        int j = Integer.MAX_VALUE;
        int k = 1 << maxMipmapLevelIn;
        profilerIn.endStartSection("extracting_frames");
        net.minecraftforge.client.ForgeHooksClient.onTextureStitchedPre(this, set);

        for(TextureAtlasSprite.Info spriteInfo : this.makeSprites(resourceManagerIn, set)) {
            int spriteWidth = spriteInfo.getSpriteWidth()*spriteSizeMultiplier;
            int spriteHeight = spriteInfo.getSpriteHeight()*spriteSizeMultiplier;
            j = Math.min(j, Math.min(spriteWidth, spriteHeight));
            int l = Math.min(Integer.lowestOneBit(spriteWidth), Integer.lowestOneBit(spriteHeight));
            if (l < k) {
                LOGGER.warn("Texture {} with size {}x{} limits mip level from {} to {}", spriteInfo.getSpriteLocation(), spriteWidth, spriteHeight, MathHelper.log2(k), MathHelper.log2(l));
                k = l;
            }

            stitcher.addSprite(spriteInfo);
        }

        int i1 = Math.min(j, k);
        int j1 = MathHelper.log2(i1);
        int k1 = maxMipmapLevelIn;
        if (false) // FORGE: do not lower the mipmap level
            if (j1 < maxMipmapLevelIn) {
                LOGGER.warn("{}: dropping miplevel from {} to {}, because of minimum power of two: {}", LOCATION_THICKRINGS_TEXTURE, maxMipmapLevelIn, j1, i1);
                k1 = j1;
            } else {
                k1 = maxMipmapLevelIn;
            }

        profilerIn.endStartSection("register");
        stitcher.addSprite(MissingTextureSprite.getSpriteInfo());
        profilerIn.endStartSection("stitching");

        try {
            stitcher.doStitch();
        } catch (StitcherException stitcherexception) {
            CrashReport crashreport = CrashReport.makeCrashReport(stitcherexception, "Stitching");
            CrashReportCategory crashreportcategory = crashreport.makeCategory("Stitcher");
            crashreportcategory.addDetail("Sprites", stitcherexception.getSpriteInfos().stream().map((p_229216_0_) -> {
                return String.format("%s[%dx%d]", p_229216_0_.getSpriteLocation(), p_229216_0_.getSpriteWidth(), p_229216_0_.getSpriteHeight());
            }).collect(Collectors.joining(",")));
            crashreportcategory.addDetail("Max Texture Size", i);
            throw new ReportedException(crashreport);
        }

        profilerIn.endStartSection("loading");
        List<TextureAtlasSprite> list = this.getStitchedSprites(resourceManagerIn, stitcher, k1);
        profilerIn.endSection();
        return new AtlasTexture.SheetData(set, stitcher.getCurrentWidth(), stitcher.getCurrentHeight(), k1, list);
    }

    private Collection<TextureAtlasSprite.Info> makeSprites(IResourceManager resourceManagerIn, Set<ResourceLocation> spriteLocationsIn) {
        List<CompletableFuture<?>> list = Lists.newArrayList();
        ConcurrentLinkedQueue<TextureAtlasSprite.Info> concurrentlinkedqueue = new ConcurrentLinkedQueue<>();

        for(ResourceLocation thickSpriteLocation : spriteLocationsIn) {
            if (!MissingTextureSprite.getLocation().equals(thickSpriteLocation)) {
                list.add(CompletableFuture.runAsync(() -> {
                    ResourceLocation baseSpriteLocation = ThickRingTextureManager.getBaseRingFromThickRing(thickSpriteLocation);
                    ResourceLocation baseSpritePath = this.getSpritePath(baseSpriteLocation);

                    TextureAtlasSprite.Info textureatlassprite$info;
                    try (IResource baseRingResource = resourceManagerIn.getResource(baseSpritePath)) {
                        PngSizeInfo pngsizeinfo = new PngSizeInfo(baseRingResource.toString(), baseRingResource.getInputStream());
                        AnimationMetadataSection animationmetadatasection = baseRingResource.getMetadata(AnimationMetadataSection.SERIALIZER);
                        if (animationmetadatasection == null) {
                            animationmetadatasection = AnimationMetadataSection.EMPTY;
                        }

                        Pair<Integer, Integer> pair = animationmetadatasection.getSpriteSize(pngsizeinfo.width, pngsizeinfo.height);
                        textureatlassprite$info = new TextureAtlasSprite.Info(baseSpriteLocation, pair.getFirst(), pair.getSecond(), animationmetadatasection);
                    } catch (RuntimeException runtimeexception) {
                        LOGGER.error("Unable to parse metadata from {} : {}", baseSpritePath, runtimeexception);
                        return;
                    } catch (IOException ioexception) {
                        LOGGER.error("Using missing texture, unable to load {} : {}", baseSpritePath, ioexception);
                        return;
                    }

                    concurrentlinkedqueue.add(textureatlassprite$info);
                }, Util.getServerExecutor()));
            }
        }

        CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join();
        return concurrentlinkedqueue;
    }

    private List<TextureAtlasSprite> getStitchedSprites(IResourceManager resourceManagerIn, Stitcher stitcherIn, int mipmapLevelIn) {
        ConcurrentLinkedQueue<TextureAtlasSprite> concurrentlinkedqueue = new ConcurrentLinkedQueue<>();
        List<CompletableFuture<?>> list = Lists.newArrayList();
        stitcherIn.getStitchSlots((spriteInfo, width, height, x, y) -> {
            if (spriteInfo == MissingTextureSprite.getSpriteInfo()) {
                MissingTextureSprite missingtexturesprite = MissingTextureSprite.create(this, mipmapLevelIn, width, height, x, y);
                concurrentlinkedqueue.add(missingtexturesprite);
            } else {
                list.add(CompletableFuture.runAsync(() -> {
                    TextureAtlasSprite textureatlassprite = this.loadSprite(resourceManagerIn, spriteInfo, width, height, mipmapLevelIn, x, y);
                    if (textureatlassprite != null) {
                        concurrentlinkedqueue.add(textureatlassprite);
                    }

                }, Util.getServerExecutor()));
            }

        });
        CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join();
        return Lists.newArrayList(concurrentlinkedqueue);
    }

    @Nullable
    private TextureAtlasSprite loadSprite(IResourceManager resourceManagerIn, TextureAtlasSprite.Info spriteInfoIn, int widthIn, int heightIn, int mipmapLevelIn, int originX, int originY) {
        ResourceLocation baseSpritePath = this.getSpritePath(spriteInfoIn.getSpriteLocation());

        TextureAtlasSprite.Info thickSpriteInfo = new TextureAtlasSprite.Info(
                ThickRingTextureManager.getThickRingFromBaseRing(spriteInfoIn.getSpriteLocation()),
                spriteInfoIn.getSpriteWidth()*spriteSizeMultiplier,
                spriteInfoIn.getSpriteHeight()*spriteSizeMultiplier,
                AnimationMetadataSection.EMPTY);

        try (IResource iresource = resourceManagerIn.getResource(baseSpritePath)) {
            NativeImage nativeimage = NativeImage.read(iresource.getInputStream());
            TextureAtlasSprite thinRings = new TextureAtlasSprite(this, spriteInfoIn, mipmapLevelIn, widthIn, heightIn, originX, originY, nativeimage){};
            return new ThickRingTextureAtlasSprite(this, thickSpriteInfo, mipmapLevelIn, widthIn, heightIn, originX, originY, thinRings, baseSpritePath);
        } catch (RuntimeException runtimeexception) {
            LOGGER.error("Unable to parse metadata from {}", baseSpritePath, runtimeexception);
            return null;
        } catch (IOException ioexception) {
            LOGGER.error("Using missing texture, unable to load {}", baseSpritePath, ioexception);
            return null;
        }
    }

    private ResourceLocation getSpritePath(ResourceLocation location) {
        return new ResourceLocation(location.getNamespace(), String.format("textures/%s%s", location.getPath(), ".png"));
    }

}