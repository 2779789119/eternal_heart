package com.eternal_heart.mixin;

import com.eternal_heart.EternalHeartConfig;
import com.eternal_heart.config.ConfigValues;
import com.eternal_heart.core.Debuffs;
import com.eternal_heart.features.AegisSystem;
import com.eternal_heart.features.EquipTracker;
import com.eternal_heart.features.SuppressionSystem;
import com.eternal_heart.features.TimeStop;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版方法层的效果硬拦截。
 *
 * <p><b>为什么需要 Mixin</b>：{@code MobEffectEvent.Applicable} 只覆盖
 * {@code addEffect(...)} 这条常规路径；原版还提供
 * {@code LivingEntity#forceAddEffect(MobEffectInstance, Entity)}——「无视既有状态、
 * 强制写入」的入口，<b>不触发任何事件</b>。其它模组（或原版机制）经由它施加负面效果时，
 * 纯事件层方案会被完全绕过，只能在周期清扫阶段补救（最长一个清扫间隔的窗口期）。</p>
 *
 * <p>这里在最头部直接取消（cancel = 效果不写入），做到「零窗口期」。</p>
 *
 * <p><b>三级防线各司其职</b>：</p>
 * <ol>
 *   <li>{@code MobEffectEvent.Applicable}（预防层）—— 常规 {@code addEffect} 路径；</li>
 *   <li>本 Mixin（原版方法层）—— {@code forceAddEffect} 强制写入路径；</li>
 *   <li>{@code DefenseSystem} 周期清扫（兜底）—— 直接改写 activeEffects 等极端路径。</li>
 * </ol>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /**
     * 拦截强制写入的负面效果。
     *
     * <p>目标方法签名：{@code forceAddEffect(MobEffectInstance, Entity)V}
     * （SRG {@code m_147215_}）—— 返回 void，故使用 {@link CallbackInfo}。</p>
     */
    @Inject(method = "forceAddEffect", at = @At("HEAD"), cancellable = true)
    private void eternal_heart$blockForcedDebuffs(MobEffectInstance instance, Entity source, CallbackInfo ci) {
        // Mixin 类的 this 静态类型是 mixin 自身，需先转 Object 再做类型判断
        if (!((Object) this instanceof Player player)) return;
        if (!EquipTracker.isEquipped(player)) return;
        if (!ConfigValues.get(EternalHeartConfig.DEBUFF_GUARD)) return;
        if (Debuffs.blocked(instance.getEffect())) {
            ci.cancel();
        }
    }

    /**
     * 血量硬锁：在 {@code setHealth} 写入的瞬间拒绝「未经伤害管线的降血」。
     *
     * <p>为什么不能只靠每 tick 审计：审计只能下一 tick 回滚，那一 tick 里其它模组
     * 已经看到了低血量，也可能顺手触发了死亡判定；硬锁把窗口压到 0。</p>
     *
     * <p>放行规则（治疗 / 钳制到上限 / 伤害额度 / 本模组主动改血 / 登录加载阶段）
     * 全部由 {@link AegisSystem#blockHealthChange} 判定，本注入只做转发。</p>
     */
    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void eternalHeart$guardHealthWrites(float health, CallbackInfo ci) {
        if (!((Object) this instanceof Player player)) return;
        if (AegisSystem.blockHealthChange(player, health)) {
            ci.cancel();
        }
    }

    /**
     * 攻击压制·禁疗 —— 被压制的实体无法通过 {@code heal} 回血。
     *
     * <p>原版把「所有」回血都收敛到 {@code LivingEntity#heal(float)}：再生效果、
     * 治疗药水 / 金苹果的瞬间治疗、饱食度自然恢复、不死图腾恢复都走它。
     * 因此在最头部直接取消，一次覆盖全部常规回血路径。</p>
     *
     * <p>是否处于压制状态由 {@link SuppressionSystem#suppressed} 判定（UUID 记账表），
     * 不依赖药水效果系统 —— 凋灵 / 末影龙这类「效果免疫」生物同样会被拦下。</p>
     *
     * <p>与 {@code setHealth} 注入同理：先做纯字段判定（血量 ≤ 0 时原版 heal 本来就是空操作），
     * 避免在实体构造期去查任何会被其它模组注入的状态。</p>
     */
    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void eternalHeart$blockSuppressedHeal(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getHealth() <= 0.0F) return;
        if (SuppressionSystem.suppressed(self)) {
            ci.cancel();
        }
    }

    /**
     * 攻击压制·禁疗的兜底 —— 直接写血的回血路径（{@code setHealth} 抬高血量）一并拒绝。
     *
     * <p>为什么还要拦这里：{@code heal} 之外，其它模组 / 命令常用
     * {@code setHealth(getHealth() + x)} 绕过回血入口，只拦 {@code heal} 会漏。</p>
     *
     * <p><b>注入体里只读字段，绝不调用实体状态查询方法</b>。{@code setHealth} 会在
     * {@code LivingEntity.<init>} 里被调（血量初始化成上限），此刻 Player 的
     * {@code inventory} 还没赋值；一旦在这里调用 {@code isDeadOrDying()} 这类会被其它模组
     * 注入的方法（简易刀剑在它上面取主手物品 → 精妙战斗的 accessor 读 inventory），
     * 就是构造期 NPE：「Couldn't place player in world」。因此判定顺序是
     * 「抬高血量？」→「记账表里有它吗（构造期必然没有，直接放行）」→「血量 ≤ 0？」，
     * 全程只用 {@code getHealth()} 与记账表，等价于 {@code isDeadOrDying()} 却不触发任何外部注入。</p>
     */
    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void eternalHeart$blockSuppressedHealthGain(float health, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (health <= self.getHealth()) return;          // 降血 / 钳制到上限：放行
        if (!SuppressionSystem.suppressed(self)) return; // 未被压制：放行（构造期必走这里，不做任何状态查询）
        if (self.getHealth() <= 0.0F) return;            // 死亡 / 重生 / 读档：放行（读字段，等价 isDeadOrDying）
        ci.cancel();
    }

    /**
     * 压制·吸收值 —— 被压制的目标无法再获得吸收值（金苹果的黄心）。
     *
     * <p>为什么需要单独拦：吸收值是一条与生命值等价的回血路径（{@code actuallyHurt} 里
     * 先扣吸收再扣血）。此前禁疗只拦 {@code heal} 与「抬高型 {@code setHealth}」，
     * 目标吃一个金苹果就能拿到 4 点可挡伤害的「血量」，禁疗在最需要它的高血量 Boss 上形同虚设。</p>
     *
     * <p>只拦「抬高」：清零 / 扣减（包括压制生效时的清零）一律放行，否则会把自己也锁死。</p>
     */
    @Inject(method = "setAbsorptionAmount", at = @At("HEAD"), cancellable = true)
    private void eternalHeart$blockSuppressedAbsorption(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (amount <= self.getAbsorptionAmount()) return;
        if (!SuppressionSystem.absorptionBlocked()) return;
        if (SuppressionSystem.suppressed(self)) {
            ci.cancel();
        }
    }

    /**
     * 无视受击无敌帧 —— 让我方造成的每一次打击都全额结算。
     *
     * <p><b>为什么需要 Mixin</b>：原版 {@code LivingEntity#hurt} 开头就是
     * {@code if (this.invulnerableTime > 10) { 只结算差额、并且不再触发受击事件 }}。
     * 因此光环每 20 tick 一跳、溅射、反伤、额外打击这些「同一 tick 内多次命中」的能力
     * 会被无敌帧吞掉大半，只能在各个调用点零散地 {@code invulnerableTime = 0} 打补丁。</p>
     *
     * <p>这里在伤害管线的<b>最入口</b>统一清零，一次覆盖全部伤害来源：
     * 只要攻击方佩戴永恒之心，目标就不再有 10 tick 的受击免疫窗口。</p>
     *
     * <p>刻意<b>不对玩家目标生效</b>：否则多人游戏里两名佩戴者会互相清帧，变成抢先手秒杀。</p>
     */
    @Inject(method = "hurt", at = @At("HEAD"))
    private void eternalHeart$ignoreInvulnerableTime(DamageSource source, float amount,
                                                     CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player) return;              // 玩家之间的受击保持原版规则
        if (!(source.getEntity() instanceof Player player)) return;
        if (player == self) return;
        if (!ConfigValues.get(EternalHeartConfig.IGNORE_INVULNERABLE_TIME)) return;
        if (!EquipTracker.isEquipped(player)) return;
        self.invulnerableTime = 0;
    }

    /**
     * 时停冻结层 —— 覆盖**生物实体**。
     *
     * <p>{@code LivingEntity#tick} 的 AI / 移动 / 感知等逻辑不经过 {@code Entity#tick}，
     * 因此必须在这里单独拦截：取消即彻底静止（不移动、不攻击、不呼吸、不执行 AI）。</p>
     *
     * <p>是否冻结（含「发动者不受影响」「其它玩家按配置」）全部由
     * {@link TimeStop#frozen} 判定，本注入只做转发。</p>
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void eternalHeart$freezeInTimeStop(CallbackInfo ci) {
        if (TimeStop.frozen((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
