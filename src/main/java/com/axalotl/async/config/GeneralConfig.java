package com.axalotl.async.config;

import com.axalotl.async.Async;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = "async")
public class GeneralConfig implements ConfigData {
    // Actual config stuff
    //////////////////////

    // General
    @Comment("Globally disable all toggleable functionality")
    public boolean disabled = false;

    // Parallelism
    @Comment("Thread count config; In standard mode: will never create more threads than there are CPU threads (as that causeses Context switch churning)\n" +
            "Values <=1 are treated as 'all cores'")
    public int paraMax = -1;

    @Comment("""
            Other modes for paraMax
            Override: Standard but without the CoreCount Ceiling (So you can have 64k threads if you want)
            Reduction: Parallelism becomes Math.max(CoreCount-paramax, 2), if paramax is set to be -1, it's treated as 0
            Todo: add more"""
    )
    public ParaMaxMode paraMaxMode = ParaMaxMode.Standard;

    // Entity
    @Comment("Disable entity parallelisation")
    public boolean disableEntity = false;

    @Comment("Disable tnt entity parallelisation")
    public boolean disableTNT = false;


    public enum ParaMaxMode {
        Standard,
        Override,
        Reduction
    }

    // Functions intended for usage
    ///////////////////////////////

    @Override
    public void validatePostLoad() throws ValidationException {
        if (paraMax >= -1)
            if (paraMaxMode == ParaMaxMode.Standard || paraMaxMode == ParaMaxMode.Override || paraMaxMode == ParaMaxMode.Reduction)
                return;
        throw new ValidationException("Failed to validate Async config.");
    }

    public static int getParallelism() {
        GeneralConfig config = Async.config;
        return switch (config.paraMaxMode) {
            case Standard -> config.paraMax <= 1 ?
                    Runtime.getRuntime().availableProcessors() :
                    Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), config.paraMax));
            case Override -> config.paraMax <= 1 ?
                    Runtime.getRuntime().availableProcessors() :
                    config.paraMax;
            case Reduction -> Math.max(
                    Runtime.getRuntime().availableProcessors() - Math.max(0, config.paraMax),
                    2);
        };
    }
}