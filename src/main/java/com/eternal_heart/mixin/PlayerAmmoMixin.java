package com.eternal_heart.mixin;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.features.EquipTracker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 无限箭矢 —— 在<b>弹药判定层</b>拦截，而不是往背包塞箭。
 *
 * <p>{@code Player#getProjectile(ItemStack)} 是弓、弩以及绝大多数模组远程武器
 * 统一的「有没有弹药」查询入口：拉弓前的可用性检查、发射时的实弹创建、
 * 弩的装填流程全都走它。因此在这里做手脚即可让整套原版流程正常运转，
 * 而不必污染玩家背包。</p>
 *
 * <h3>两种情况的处理</h3>
 * <ul>
 *   <li><b>没有箭</b>：返回一支普通箭 —— 弓/弩据此认为有弹药可拉，实际不进入背包；</li>
 *   <li><b>有箭</b>：返回该箭的<b>副本</b> —— 原版后续的 {@code shrink} 落在副本上，
 *       背包里的箭毫发无损，同时保留了药箭等箭种类型（发射出来仍是原本的箭）。</li>
 * </ul>
 *
 * <p>相比"往背包塞一支带标记的箭"：不占格子、不会掉出来、不会被误用，
 * 也不需要在卸下与切武器时做扫描回收。弩因此同样被覆盖（它没有 {@code ArrowLooseEvent}）。</p>
 */
@Mixin(Player.class)
public abstract class PlayerAmmoMixin {

    @Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
    private void eternalHeart$infiniteAmmo(ItemStack shootable, CallbackInfoReturnable<ItemStack> cir) {
        Player self = (Player) (Object) this;
        if (self.getAbilities().instabuild) return; // 创造模式原版就不消耗
        if (!ConfigValues.get(EternalHeartConfig.INFINITE_ARROWS)) return;
        if (!EquipTracker.isEquipped(self)) return;
        if (!acceptsArrows(shootable)) return;

        ItemStack ammo = cir.getReturnValue();
        if (ammo == null) return;
        cir.setReturnValue(ammo.isEmpty() ? new ItemStack(Items.ARROW) : ammo.copy());
    }

    /**
     * 该武器是否以箭为弹药。
     *
     * <p>用原版弹药物种判定而不是硬编码 {@code Items.BOW}：既覆盖模组的弓弩，
     * 又不会给「枪械类」武器凭空塞一支箭。</p>
     */
    private static boolean acceptsArrows(ItemStack shootable) {
        return shootable.getItem() instanceof ProjectileWeaponItem weapon
                && weapon.getAllSupportedProjectiles().test(new ItemStack(Items.ARROW));
    }
}
