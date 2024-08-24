package site.hjfunny.velochatx;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalLong;

public class sounds implements Sound {

    @Override
    public @NotNull Key name() {
        return Key.key("block.note_block.pling");
    }

    @Override
    public @NotNull Source source() {
        return Source.BLOCK;
    }

    @Override
    public float volume() {
        return 1.0f;
    }

    @Override
    public float pitch() {
        return 1.0f;
    }

    @Override
    public @NotNull OptionalLong seed() {
        return null;
    }

    @Override
    public @NotNull SoundStop asStop() {
        return null;
    }
}
