# Easy Entity Crosshair & Hitbox Tint

Minecraft **26.3**（Fabric）客户端模组：准星着色、瞄准实体碰撞箱着色、攻击指示器染色。所有功能默认独立开关，配置为 **TOML**，可在游戏内用 Mod Menu 可视化修改。

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `enabled` | 准星功能总开关。`false` 时准星完全保持原版行为 | `true` |
| `opacity` | 准星透明度，`0.0`（完全透明）~ `1.0`（不透明） | `1.0` |
| `target_color` | 准星瞄准实体时的颜色（`#RRGGBB` 或 `#AARRGGBB`） | `#FF0000`（红） |
| `target_entities` | 生效的实体列表；留空 / `*` = 任意实体 | `[]`（任意实体） |
| `hitbox_enabled` | 瞄准实体碰撞箱着色开关 | `false` |
| `hitbox_always_show` | 即使原版 `F3+B` 碰撞箱显示关闭，也画出瞄准实体的碰撞箱 | `false` |
| `hitbox_color` | 碰撞箱颜色（`#RRGGBB` 或 `#AARRGGBB`） | `#FF0000`（红） |
| `hitbox_opacity` | 碰撞箱线条透明度，`0.0` ~ `1.0` | `1.0` |
| `hitbox_line_width` | 碰撞箱线条粗细（原版为 `2.5`），可用 `0.5` ~ `8.0` | `2.5` |
| `attack_indicator_enabled` | 攻击指示器（准星下方的攻击冷却显示）染色开关 | `false` |
| `attack_indicator_color` | 攻击指示器染色（`#RRGGBB` 或 `#AARRGGBB`） | `#FF0000`（红） |
| `attack_indicator_threshold` | 攻击冷却达到该比例才染色，`0.885` = 88.5%（也可直接写 `88.5`） | `0.885` |
| `attack_style_enabled` | 攻击准星样式总开关（需瞄准到攻击范围内的实体才显示） | `false` |
| `attack_style_crit` | 暴击：准星四角出现**虚斜线** | `true` |
| `attack_style_knockback` | 疾跑击退攻击：准星上方出现 **`^`** | `true` |
| `attack_style_sweep` | 横扫攻击：准星下方出现**半弧** | `true` |
| `attack_style_color` | 上述三种标记的颜色（`#RRGGBB` 或 `#AARRGGBB`） | `#FF0000`（红） |
| `attack_style_mode` | 模式：`decorate` = 在准星周围装饰（默认）；`override` = 用对应贴图替换原版准星 | `decorate` |
* decorate（默认）保留原版准星, 准星周围装饰; override 隐藏原版准星, 改用 crosshair_*_override.png 整把准星贴图。
* `target_entities` 每一项可以是实体 ID（`minecraft:zombie`）或实体标签（`#minecraft:raiders`），
  准星与碰撞箱功能共用这一份过滤条件。
* 攻击指示器的**底色条不变**，只给进度条和"充满"图标染色，保证进度可读。
* 攻击准星样式（暴击/击退/横扫）的判定**完全照抄原版 `Player#attack`**：
  `attackStrengthScale > 0.9`、`canCriticalAttack`、`isSprinting`、`isSweepAttack`（含主手剑判断、
  移动速度判断），因此不会出现"原版不暴击但模组显示暴击"的情况；三种标记都是 1×1 像素点画的，
  不依赖任何贴图，资源包随便换。

## 配置文件（TOML）

首次启动时自动生成 `config/easy_entity_crosshair_hitbox_tint.toml`：

```toml
# ===== 准星 =====
# 准星功能总开关
enabled = true
# 准星透明度 0.0（全透明）- 1.0（不透明）
opacity = 1.0
# 瞄准实体时准星的颜色（#RRGGBB 或 #AARRGGBB）
target_color = "#FF0000"
# 生效实体：实体 ID（minecraft:zombie）或标签（#minecraft:raiders）；留空 = 任意实体
target_entities = []

# ===== 碰撞箱 =====
# 瞄准实体时是否给它的碰撞箱染色
hitbox_enabled = false
# 即使原版 F3+B 碰撞箱显示关闭，也画出瞄准实体的碰撞箱（默认关）
hitbox_always_show = false
# 碰撞箱颜色（#RRGGBB 或 #AARRGGBB）
hitbox_color = "#FF0000"
# 碰撞箱线条透明度 0.0 - 1.0
hitbox_opacity = 1.0
# 碰撞箱线条粗细，原版为 2.5，可用 0.5 - 8.0
hitbox_line_width = 2.5

# ===== 攻击指示器（准星下方的攻击冷却显示） =====
# 是否给攻击指示器染色
attack_indicator_enabled = false
# 染色颜色（#RRGGBB 或 #AARRGGBB）
attack_indicator_color = "#FF0000"
# 攻击冷却达到该比例时才染色：0.885 = 88.5%，也可以直接写 88.5
attack_indicator_threshold = 0.885
```

* 文件保存后 **约 1 秒内自动生效**，不需要重启游戏；也支持 UTF-8 BOM（记事本另存为不会出问题）。
* 如果你之前用的是 JSON 配置，第一次启动新版本时会**自动迁移**成 TOML（保留原有设置），旧文件不会删除。

## Mod Menu 可视化配置（可选）

安装 [Mod Menu](https://modrinth.com/mod/modmenu) 后，在模组列表里点 **Configure** 会打开本模组的配置界面，
按 **准星 / 碰撞箱 / 攻击指示器** 三个分页组织：

* **准星** → 准星样式（开关、透明度、颜色、生效实体）+ 攻击准星样式（总开关、暴击虚斜线、
  击退 `^`、横扫半弧、颜色），本页下方还有**实时预览**，改颜色/开关立刻能看到效果；
* **碰撞箱** → 瞄准碰撞箱（着色开关、无 F3+B 也显示、颜色、透明度、线宽）；
* **攻击指示器** → 指示器蓄力（染色开关、颜色、触发阈值）。

点「保存」立刻写回 TOML 并生效；界面里还有「打开 TOML」按钮用于直接编辑文本。
配置界面只用**原版控件**实现，不需要 Cloth Config / YACL 等任何额外前置。
Mod Menu 本身也只是编译期依赖（`compileOnly`）：不装 Mod Menu 一样能用，直接改 TOML 即可。

## 用资源包自定义攻击样式贴图

三种攻击标记都是普通的 GUI 精灵，**资源包可以直接重画**（32×32，透明背景，准星中心 = 贴图正中心）：

```
assets/easy_entity_crosshair_hitbox_tint/textures/gui/sprites/hud/crosshair_crit.png        # 暴击·四角虚斜线
assets/easy_entity_crosshair_hitbox_tint/textures/gui/sprites/hud/crosshair_knockback.png   # 疾跑击退·上方 ^
assets/easy_entity_crosshair_hitbox_tint/textures/gui/sprites/hud/crosshair_sweep.png       # 横扫·下方半弧
# 覆盖模式（attack_style_mode = "override"）用的整把准星贴图，同样 32x32、白色可染色：
assets/easy_entity_crosshair_hitbox_tint/textures/gui/sprites/hud/crosshair_crit_override.png
assets/easy_entity_crosshair_hitbox_tint/textures/gui/sprites/hud/crosshair_knockback_override.png
assets/easy_entity_crosshair_hitbox_tint/textures/gui/sprites/hud/crosshair_sweep_override.png
```



* 模组自带的三张默认贴图是**白色**的，所以 `attack_style_color` 会给它们上色；
  如果你自己的贴图已经画好颜色，把颜色设成 `#FFFFFF` 即可原样显示。
* 贴图不是 32×32 也没关系，会被拉伸到 32×32；想要更精细可以画 64×64。
* 没有资源包时用模组内置贴图；**连模组自带贴图都加载不到时**（例如只用 Fabric Loader、
  没有装 Fabric API 的整合包——26.3 的模组资源是由 `fabric-resource-loader-v0` 提供的），
  会自动退回**内置的像素绘制**，外观与默认贴图一致，绝不会出现紫黑"缺失贴图"。
  你自己资源包里的贴图在任何情况下都会生效。



三个功能共用同一套"瞄准的是谁"判定，和原版攻击指示器保持一致：

1. 先取原版拾取结果（`Minecraft.hitResult`）；
2. 没有命中时，再用一条 **受方块阻挡的短射线** 补判，射线长度取玩家当前的
   **攻击范围**（原版 `AttackRange` 数据，没有该组件时用 `AttackRange.defaultFor(player)`，
   因此长柄武器等更远的攻击距离同样生效），并且命中点必须通过 `AttackRange#isInRange` 校验。

也就是说：**只有在攻击范围内瞄准实体才会变色 / 显示碰撞箱**，超出攻击范围一律不算，
也不会穿墙（被方块挡住即中断，且射线结果每帧最多计算一次，开销可忽略）。

## 设计要点

* 前置需求：只有 **Fabric Loader**，不需要 Fabric API。
* 侵入性极低，只有三处 Mixin，且都只改动"已经存在的"原版绘制路径，不添加贴图/资源包：
  * `HudMixin`：只重定向 `Hud#extractCrosshair` 里的 `blitSprite` 调用（准星 + 攻击指示器）。
  * `DebugRendererMixin`：`hitbox_always_show` 打开时，用原版 Gizmo API 画出瞄准实体的碰撞箱。
  * `EntityHitboxDebugRendererMixin`：原版 `F3+B` 碰撞箱显示打开时，只给瞄准实体的那个框染色
    （避免同一个框画两遍）。
* 兼容自定义资源包：仍然使用原版精灵（`hud/crosshair` 等），只叠加颜色/透明度，
  资源包自带的准星贴图照常生效。
* 原版准星默认使用"反色"混合（`RenderPipelines.CROSSHAIR`），只有真正需要变色或半透明时才改用
  普通的 `GUI_TEXTURED` 颜色混合管线，其余情况完全走原版逻辑。
* **兼容性优先、能不做就不做**：配置值异常（线宽 ≤ 0 或 NaN、攻击范围数值异常）时直接放弃修改、
  保留原版外观；碰撞箱自绘一旦抛错会在本次游戏内自动关闭并写一条 WARN 日志，不会影响渲染；
  功能关闭时每帧只多一次布尔判断。

## 出问题时怎么排查

模组会在日志里（`latest.log`）用 `[EasyCrosshairHitboxTint]` 前缀打印：

* 启动时打印读到的配置：`loaded <路径> (crosshair=..., hitbox=..., attackIndicator=...)`；
* 第一次画出瞄准实体碰撞箱：`drawing aimed entity hitbox for minecraft:zombie (...)`；
* 改为染原版 `F3+B` 的框：`tinted aimed entity hitbox (vanilla F3+B display is on)`；
* 自绘出错被自动关闭：`disabled aimed entity hitbox drawing after an error`。

## 构建

已把 Gradle 发行版与依赖仓库指向国内镜像（腾讯/阿里云），`gradlew` 可直接使用：

```bash
./gradlew build          # 产物在 build/libs/
./gradlew runClient      # 开发环境里直接启动客户端
```

`gradle/gradle-daemon-jvm.properties` 已声明构建需要 Java 25，Gradle 会自动挑选本机已安装的 JDK 25
（例如 `~/.jdks/jdk-25.0.1`），无需手动设置 `JAVA_HOME`。

安装：把 `build/libs/easy_entity_crosshair_hitbox_tint-1.0.0.jar` 放进 `.minecraft/mods/`。
