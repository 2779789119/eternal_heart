# 永恒之心 (Eternal Heart)

还原泰拉瑞亚 Fargo 魂石模组 **「永恒之魂」** 的终极饰品 —— 一枚佩戴即开启数十项能力的 Curios 饰品。

- **版本**：1.19.4
- **适用平台**：Minecraft 1.20.1 · Forge 47.4+
- **协议**：MIT
- **说明**：改编自 Inolia_Zaicek 的 MineFargo

## 功能一览

佩戴「永恒之心」饰品后解锁 **10 个功能模块**，全部可在游戏内配置面板中独立开关与调参：

| 模块 | 主要能力 |
|------|----------|
| 属性 (Attribute) | 基于血量等条件的自定义属性加成 |
| 永怒 (Fury) | `eternal_fury` 效果，按层数提供攻击加成 |
| 防御 (Defense) | 减伤 / 护盾等防御机制 |
| 战斗 (Combat) | 攻击强化、暴击、斩杀相关 |
| 机动 (Mobility) | 移动、跳跃等机动能力（可选集成 Caelus 鞘翅飞行） |
| 生存 (Survival) | 生存辅助与容错机制 |
| 实用 (Utility) | 挖掘、连锁等实用功能（可选集成 FTB Ultimine） |
| 光环 (Aura) | 范围光环效果 |
| 神盾 (Aegis) | 不死图腾、灵魂绑定等死亡保护 |
| 饰品守护 (CurioGuard) | 饰品死亡不掉落 / 无视绑定 |

其他特色：

- **时停**：冻结周围实体与世界（含专属音效 `eternal_heart:shi_ting`）
- **配置面板**：游戏内 GUI 直接调整 140+ 配置项，悬浮即见说明
- **图形化配方编辑器**：面板内 3×3 网格编辑自定义配方（有序 / 无序 / 熔炼），保存即生效
- **负面效果黑 / 白名单**：指定效果强制清除或永久保护
- **启示录兼容**：自动 / 手动维护 RevelationFix 的天启豁免名单（仅读写其公开配置）
- **属性编辑**：为玩家附加自定义属性（支持其他模组的属性）

## 依赖

### 前置（必需）

| 模组 | 版本要求 |
|------|----------|
| Forge | ≥ 47.4 |
| Minecraft | 1.20.1 |
| Curios API | ≥ 5.14 |
| MineFargo | ≥ 1.2（随仓库提供 `libs/mine_fargo-1.2.42-all.jar`） |

### 可选集成（运行时自动激活）

| 模组 | 功能 |
|------|------|
| FTB Ultimine | 连锁挖掘 |
| Caelus | 鞘翅飞行 |
| AttributeFix | 解除血量上限 |
| RevelationFix | 天启阶段饰品防收 |

## 游戏内命令

所有命令需 2 级权限（`/eternalheart`）：

```
/eternalheart debuff list                        查看负面效果名单
/eternalheart debuff blacklist add|remove <效果>  黑名单（强制清除）
/eternalheart debuff whitelist add|remove <效果>  白名单（永不清除）
/eternalheart debuff check <效果>                 查询效果归属

/eternalheart attribute list                      查看自定义属性
/eternalheart attribute set <属性> <值> [operation]
/eternalheart attribute remove <属性>

/eternalheart recipe reload                       手动重载自定义配方
/eternalheart timestop                            手动触发时停
/eternalheart compat revelationfix [on|off|auto]  启示录兼容开关
```

## 构建

### 环境要求

- JDK 17
- Gradle（随仓库附带的 `gradlew.bat`，无需全局安装）

### 构建步骤

```bat
:: 直接使用仓库内脚本
build_mod.bat

:: 或手动执行
gradlew.bat clean build
```

产物输出至 `build/libs/`。构建结束时会自动校验产物包含
`eternal_heart.mixins.json` 与 `eternal_heart.refmap.json`（Mixin 生产环境注入依赖，
缺失会直接导致启动崩溃）。

### 开发 / 测试

- `runClient` / `runServer`：开发环境启动（工作目录 `run/`）
- `gameTestServer`：游戏测试服务器（工作目录 `run-gametest/`，命名空间 `eternal_heart`）
- 跑测试时若 MineFargo 内嵌依赖与 mixin 环境冲突，可加 `-PnoMineFargo` 将其移出 classpath：

```bat
gradlew.bat -PnoMineFargo runGameTestServer
```

## 项目结构

```
src/main/java/com/eternal_heart/
├── EternalHeartMod.java      模组入口（物品 / 效果 / 音效 / 战利品注册）
├── EternalHeartItem.java     永恒之心饰品
├── EternalHeartConfig.java   配置定义（140+ 项）
├── EternalHeartEvents.java   全局事件分发
├── features/                 10 个功能模块（IFeature 接口）
├── client/                   配置面板 / 配方编辑器 / 属性查看等 GUI
├── command/                  游戏内命令
├── integration/              可选模组集成（FTB Ultimine / Caelus / RevelationFix）
├── network/                  配置与网络同步
├── mixin/                    Mixin 注入（冷却 / 冻结 / 弹药等）
└── loot/                     方块掉落战利品修改
src/gametest/                 游戏测试源集（不打包进发布 jar）
```

## 配置

配置文件生成于 `config/eternal_heart-common.toml`，也可通过游戏内配置面板
（按键打开 `EternalHeartConfigScreen`）实时修改并同步，无需重启。

## 更新日志

详见 [CHANGELOG.md](CHANGELOG.md)。
