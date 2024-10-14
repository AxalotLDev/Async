package com.axalotl.async.config;

import com.axalotl.async.Async;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

import java.util.ArrayList;
import java.util.List;

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

    @Comment("Enable virtual threads java 21 (Don't use for game!)")
    public boolean useVirtualThreads = false;

    // Entity
    @Comment("Disable entity parallelisation")
    public boolean disableEntity = false;

    @Comment("Disable tnt entity parallelisation")
    public boolean disableTNT = false;

    @Comment("""
            List of tile entity classes that will always be fully parallelised
            This will occur even when chunkLockModded is set to true
            Adding pistons to this will not parallelise them"""
    )
    public List<String> teWhiteListString = new ArrayList<>();

    @Comment("List of tile entity classes that will always be chunklocked\n"
            + "This will occur even when chunkLockModded is set to false")
    public List<String> teBlackListString = new ArrayList<>();

    // Any TE class strings that aren't available in the current environment
    // We use classes for the main operation as class-class comparisons are memhash based
    // So (should) be MUCH faster than string-string comparisons
    @ConfigEntry.Gui.Excluded
    public List<String> teUnfoundWhiteList = new ArrayList<>();
    @ConfigEntry.Gui.Excluded
    public List<String> teUnfoundBlackList = new ArrayList<>();
    // More Debug
    @Comment("Enable ops tracing; this will probably have a performance impact, but allows for better debugging")
    public boolean opsTracing = false;



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

    public void loadTELists() {
        teWhiteListString.forEach(str -> {
            Class<?> c;
            try {
                c = Class.forName(str);
                BlockEntityLists.teWhiteList.add(c);
            } catch (ClassNotFoundException cnfe) {
                teUnfoundWhiteList.add(str);
            }
        });

        teBlackListString.forEach(str -> {
            Class<?> c;
            try {
                c = Class.forName(str);
                BlockEntityLists.teBlackList.add(c);
            } catch (ClassNotFoundException cnfe) {
                teUnfoundBlackList.add(str);
            }
        });
    }
}