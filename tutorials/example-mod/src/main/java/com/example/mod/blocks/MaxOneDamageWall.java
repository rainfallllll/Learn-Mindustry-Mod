package com.example.mod.blocks;

import arc.graphics.Color;
import mindustry.entities.bullet.Bullet;
import mindustry.world.blocks.defense.Wall;

/**
 * 自定义墙体：每次受到的伤害最多只有 1 点。
 *
 * <h2>实现原理</h2>
 *
 * <p>Mindustry 的伤害计算流程：
 * <ol>
 *   <li>子弹撞击方块时，调用 {@link BuildingComp#collision(Bullet)}</li>
 *   <li>在处理伤害时，调用 {@link BuildingComp#handleDamage(float)} 来"过滤"伤害值</li>
 *   <li>handleDamage 返回的值才是实际扣除的血量</li>
 * </ol>
 * </p>
 *
 * <p>因此，我们只需要覆写 {@code handleDamage(float amount)} 方法，
 * 将伤害值截断为 {@code Math.min(amount, 1f)}，即可实现"每次最多受 1 点伤害"的效果。</p>
 *
 * <h2>关于内部类继承</h2>
 *
 * <p>{@link Wall.WallBuild} 是 {@link Wall} 的非静态内部类（inner class），
 * 它隐式持有一个外部 Wall 实例的引用。当 {@code MaxOneDamageWall} 继承 {@link Wall} 后，
 * {@code WallBuild} 被继承到本类中。因此本类的内部类 {@code MaxOneDamageWallBuild}
 * 可以直接 {@code extends WallBuild}。</p>
 *
 * <p>Mindustry 的 Block 类会通过反射扫描子类中继承 Building 的内部类，
 * 并自动用 {@code new MaxOneDamageWallBuild(this)} 实例化（this 为 Block 实例）。</p>
 */
public class MaxOneDamageWall extends Wall {

    public MaxOneDamageWall(String name) {
        super(name);
        // 继承 Wall 的默认设置：solid=true, destructible=true 等
        // 这里可以额外调整属性
        this.flashHit = true;           // 受击时闪烁
        this.flashColor = Color.valueOf("#4fc3f7");
    }

    /**
     * 方块的 Build 实体类 —— 对应游戏中实际放置的方块实例。
     *
     * <p>继承自 {@link Wall.WallBuild}（即 {@link Building} 的子类）。
     * Mindustry 会自动识别这个内部类并用于创建方块实体。</p>
     */
    public class MaxOneDamageWallBuild extends WallBuild {

        /**
         * 覆写伤害处理方法，将每次伤害限制为最多 1 点。
         *
         * <p>该方法在 BuildingComp 中定义，是游戏计算实际伤害的"过滤器"：
         * 传入的 amount 是原始伤害值，返回值才是真正扣血的数值。</p>
         *
         * @param amount 原始伤害值
         * @return 实际伤害值（最大 1f）
         */
        @Override
        public float handleDamage(float amount) {
            return Math.min(amount, 1f);
        }

        /**
         * 覆写碰撞方法，添加受击特效。
         *
         * <p>当子弹碰撞到此方块时调用。调用 super 保留父类行为（闪烁、闪电等），
         * 然后可以添加自定义特效。</p>
         *
         * @param bullet 碰撞的子弹
         * @return 是否阻挡子弹（true = 子弹被阻挡）
         */
        @Override
        public boolean collision(Bullet bullet) {
            // 调用父类的碰撞处理（包含闪烁效果、子弹偏转等）
            return super.collision(bullet);
        }
    }
}
