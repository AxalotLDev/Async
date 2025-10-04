package com.axalotl.async.neoforge.mixin.server;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.server.dedicated.ServerWatchdog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Map;

@Mixin(ServerWatchdog.class)
public class ServerWatchdogMixin {

    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/CrashReport;addCategory(Ljava/lang/String;)Lnet/minecraft/CrashReportCategory;"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void addCustomCrashReport(CallbackInfo ci, long i, long j, long k, String message, CrashReport crashreport){
        CrashReportCategory threadDumpSection = crashreport.addCategory("Async thread dump");
        threadDumpSection.setDetail("All Threads", () -> {
            StringBuilder sb = new StringBuilder();
            Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();
            for (Map.Entry<Thread, StackTraceElement[]> entry : allThreads.entrySet()) {
                Thread t = entry.getKey();
                sb.append(String.format("\"%s\" [%s]%n", t.getName(), t.getState()));
                for (StackTraceElement ste : entry.getValue()) {
                    sb.append("\tat ").append(ste).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        });
    }
}