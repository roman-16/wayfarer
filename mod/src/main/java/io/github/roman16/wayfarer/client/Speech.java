package io.github.roman16.wayfarer.client;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import io.github.roman16.wayfarer.story.Talk;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class Speech {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLDER = "voices/";
    private static final int TICK_MILLIS = 50;
    private static final int SILENT_TICKS = 20;
    private static final Deque<Voice> waiting = new ArrayDeque<>();
    private static final float WHOLE = 16;
    @Nullable
    private static Voice playing;
    private static boolean paged;
    private static float music = 1;

    private Speech() {}

    public static void band(String clip, Talk.Source source) {
        stop();
        paged = false;
        start(new Voice(clip, source));
    }

    public static void pages(List<String> clips) {
        stop();
        paged = true;
        clips.forEach(clip -> waiting.add(new Voice(clip, Talk.Source.HEAD)));
        next();
    }

    public static void after(String clip) {
        waiting.add(new Voice(clip, Talk.Source.HEAD));
        if (playing == null) {
            next();
        }
    }

    public static void fade() {
        waiting.clear();
        if (playing != null) {
            playing.fading = Math.max(1, Stamp.VOICE_FADE_MILLIS / TICK_MILLIS);
        }
    }

    public static void stop() {
        waiting.clear();
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
        }
    }

    public static void stopPages() {
        if (paged) {
            stop();
        }
    }

    public static boolean speaking() {
        return playing != null;
    }

    public static float music() {
        return music;
    }

    static void tick(Minecraft minecraft) {
        if (playing != null && ended(minecraft, playing)) {
            playing = null;
            next();
        }
        float wanted = playing == null ? 1 : Balance.get().talk().musicSixteenths() / WHOLE;
        float step = TICK_MILLIS / (float) Stamp.VOICE_FADE_MILLIS;
        music = wanted < music ? Math.max(wanted, music - step) : Math.min(wanted, music + step);
    }

    private static boolean ended(Minecraft minecraft, Voice voice) {
        if (minecraft.getSoundManager().isActive(voice)) {
            voice.began = true;
            return false;
        }
        return voice.began || ++voice.age > SILENT_TICKS;
    }

    private static void next() {
        Voice voice = waiting.poll();
        if (voice != null) {
            start(voice);
        }
    }

    private static void start(Voice voice) {
        LOGGER.info("voice {}", voice.file.getPath());
        playing = voice;
        Minecraft.getInstance().getSoundManager().play(voice);
    }

    private static final class Voice extends AbstractTickableSoundInstance {
        private final ResourceLocation file;
        private final Talk.Source source;
        private int age;
        private boolean began;
        private int fading;
        private int fadeTicks;

        private Voice(String clip, Talk.Source source) {
            super(SoundEvent.createVariableRangeEvent(Wayfarer.id(FOLDER + clip)), SoundSource.VOICE, SoundInstance.createUnseededRandom());
            this.file = Wayfarer.id(FOLDER + clip);
            this.source = source;
            place();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            WeighedSoundEvents events = new WeighedSoundEvents(file, null);
            sound = new Sound(file, ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, Balance.get().talk().reachBlocks());
            events.addSound(sound);
            return events;
        }

        @Override
        public void tick() {
            place();
            if (fading > 0) {
                if (fadeTicks == 0) {
                    fadeTicks = fading;
                }
                volume = (float) --fading / fadeTicks;
                if (fading == 0) {
                    stop();
                }
            }
        }

        private void place() {
            Minecraft minecraft = Minecraft.getInstance();
            Entity body = source.head() || minecraft.level == null ? null : minecraft.level.getEntity(source.entity());
            Vec3 at = body != null ? body.getEyePosition() : new Vec3(source.x(), source.y(), source.z());
            int reach = Balance.get().talk().reachBlocks();
            boolean inHead = source.head() || minecraft.player == null || body == null && minecraft.player.getEyePosition().distanceToSqr(at) > (double) reach * reach
                    || body == minecraft.player;
            relative = inHead;
            attenuation = inHead ? Attenuation.NONE : Attenuation.LINEAR;
            x = inHead ? 0 : at.x;
            y = inHead ? 0 : at.y;
            z = inHead ? 0 : at.z;
        }
    }
}
