package io.github.roman16.wayfarer.client;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.Wayfarer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;

public final class MusicPlayer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FADE_TICKS = 64;
    @Nullable
    private static Piece playing;

    private MusicPlayer() {}

    public static void play(Music.Play play) {
        Minecraft minecraft = Minecraft.getInstance();
        if (play.piece().isEmpty()) {
            fade();
            return;
        }
        if (playing != null && playing.piece.equals(play.piece()) && play.loop() && minecraft.getSoundManager().isActive(playing)) {
            return;
        }
        fade();
        playing = new Piece(play.piece(), play.loop());
        minecraft.getSoundManager().play(playing);
        LOGGER.info("story music {} {}", play.piece(), play.loop() ? "looping" : "once");
    }

    public static boolean playing(String piece) {
        return playing != null && playing.piece.equals(piece);
    }

    private static void fade() {
        if (playing != null) {
            LOGGER.info("story music {} fades", playing.piece);
            playing.fading = FADE_TICKS;
            playing = null;
        }
    }

    private static final class Piece extends AbstractTickableSoundInstance {
        private final String piece;
        private int fading = -1;

        private Piece(String piece, boolean loop) {
            super(SoundEvent.createVariableRangeEvent(Wayfarer.id("story_music." + piece)), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.piece = piece;
            this.looping = loop;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        @Override
        public void tick() {
            if (fading < 0) {
                return;
            }
            volume = (float) fading / FADE_TICKS;
            if (fading-- == 0) {
                stop();
            }
        }
    }
}
