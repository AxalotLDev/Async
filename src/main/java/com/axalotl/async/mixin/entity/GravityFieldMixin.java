package com.axalotl.async.mixin.entity;

import net.minecraft.util.math.GravityField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(GravityField.class)
public class GravityFieldMixin {

    @Shadow
    private final List<GravityField.Point> points = new CopyOnWriteArrayList<>();
}
