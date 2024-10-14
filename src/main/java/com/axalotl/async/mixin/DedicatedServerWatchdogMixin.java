package com.axalotl.async.mixin;

import com.axalotl.async.Async;
import com.axalotl.async.ParallelProcessor;
import net.minecraft.server.dedicated.DedicatedServerWatchdog;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

@Mixin(DedicatedServerWatchdog.class)
public class DedicatedServerWatchdogMixin {
    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/Bootstrap;println(Ljava/lang/String;)V"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void addCustomCrashReport(CallbackInfo ci, long l, long m, long n, ThreadMXBean threadMXBean, ThreadInfo threadInfos[], StringBuilder stringBuilder, Error error, CrashReport crashReport, CrashReportSection crashReportSection, CrashReportSection crashReportSection2) {
        if (Async.config.opsTracing) {
            CrashReportSection AsyncSection = crashReport.addElement("Async");
            AsyncSection.add("currentTasks", () -> ParallelProcessor.currentEnts.toString());
        }
    }
}
