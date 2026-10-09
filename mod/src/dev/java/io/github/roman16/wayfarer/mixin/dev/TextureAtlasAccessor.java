package io.github.roman16.wayfarer.mixin.dev;

import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
    @Accessor("animatedTextures")
    List<TextureAtlasSprite.Ticker> wayfarer$animated();

    @Accessor("animatedTextures")
    void wayfarer$animated(List<TextureAtlasSprite.Ticker> animated);

    @Accessor("texturesByName")
    Map<ResourceLocation, TextureAtlasSprite> wayfarer$sprites();
}
