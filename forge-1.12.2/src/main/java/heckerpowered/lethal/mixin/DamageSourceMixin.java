package heckerpowered.lethal.mixin;

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess;
import heckerpowered.lethal.bridge.adapter.entity.damagesource.DamageSourceView;
import heckerpowered.lethal.bridge.math.VectorView;
import heckerpowered.lethal.platform.GeometryInterop;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;

@Mixin(DamageSource.class)
@Implements(@Interface(iface = DamageSourceView.class, prefix = "damageSourceView$"))
abstract
class DamageSourceMixin {
    @Shadow
    public String damageType;

    @Shadow
    @Nullable
    public abstract Entity getImmediateSource();

    @Shadow
    @Nullable
    public abstract Entity getTrueSource();

    @Shadow
    @Nullable
    public abstract Vec3d getDamageLocation();

    public EntityAccess damageSourceView$getDirectEntity() {
        return (EntityAccess) getImmediateSource();
    }

    public EntityAccess damageSourceView$getCausingEntity() {
        return (EntityAccess) getTrueSource();
    }

    @Nullable
    public VectorView damageSourceView$getPosition() {
        final Vec3d position = getDamageLocation();
        if (position == null) return null;
        
        return GeometryInterop.vector(position);
    }
}
