package com.axalotl.async.common.mixin.entity.breed;

import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.turtle.Turtle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Turtle.TurtleBreedGoal.class)
public abstract class TurtleMixin extends BreedGoal {

    @Shadow
    @Final
    private Turtle turtle;

    public TurtleMixin(Animal animal, double speed) {
        super(animal, speed);
    }

    @Redirect(method = "breed()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/turtle/Turtle;setHasEgg(Z)V"))
    private void redirectSetHasEgg(Turtle instance, boolean onOff) {
        if (this.partner == null) return;

        Turtle t1 = this.turtle.getId() < this.partner.getId()
                ? this.turtle : (Turtle) this.partner;
        Turtle t2 = t1 == this.turtle
                ? (Turtle) this.partner : this.turtle;

        synchronized (t1) {
            synchronized (t2) {
                if (!this.turtle.hasEgg() && !((Turtle) this.partner).hasEgg()) {
                    if (this.turtle.getRandom().nextBoolean()) {
                        this.turtle.setHasEgg(true);
                    } else {
                        ((Turtle) this.partner).setHasEgg(true);
                    }
                }
            }
        }
    }
}