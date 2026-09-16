# Easy Entity Crosshair & Hitbox Tint

Minecraft **26.3**（Fabric）客户端模组：准星着色 + 瞄准实体的碰撞箱着色。所有功能都可单独开关。

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `enabled` | 准星功能总开关。`false` 时准星完全保持原版行为 | `true` |
| `opacity` | 准星透明度，`0.0`（完全透明）~ `1.0`（不透明） | `1.0` |
| `target_color` | 准星瞄准实体时的颜色（`#RRGGBB` 或 `#AARRGGBB`） | `#FF0000`（红） |
| `target_entities` | 生效的实体列表；留空 / `*` = 任意实体 | `[]`（任意实体） |
| `hitbox_enabled` | 瞄准实体碰撞箱着色开关 | `false` |
| `hitbox_color` | 碰撞箱颜色（`#RRGGBB` 或 `#AARRGGBB`） | `#FF0000`（红） |
| `hitbox_opacity` | 碰撞箱线条透明度，`0.0` ~ `1.0` | `1.0` |
| `hitbox_line_width` | 碰撞箱线条粗细（原版为 `2.5`），可用范围 `0.5` ~ `8.0` | `2.5` |

`target_entities` 每一项可以是实体 ID（`minecraft:zombie`）或实体标签（`#minecraft:raiders`），两个功能共用这一份过滤条件。

## 配置文件

首次启动时自动生成 `config/easy_entity_crosshair_hitbox_tint.json`：

```json
{
  "enabled": true,
  "opacity": 1.0,
  "target_color": "#FF0000",
  "target_entities": [],
  "hitbox_enabled": false,
  "hitbox_color": "#FF0000",
  "hitbox_opacity": 1.0,
  "hitbox_line_width": 2.5
}
```

示例：只对僵尸和袭击者生效，准星半透明，碰撞箱用青色 3.0 粗线条、70% 不透明度：

```json
{
  "enabled": true,
  "opacity": 0.5,
  "target_color": "#FF0000",
  "target_entities": ["minecraft:zombie", "#minecraft:raiders"],
  "hitbox_enabled": true,
  "hitbox_color": "#00FFFF",
  "hitbox_opacity": 0.7,
  "hitbox_line_width": 3.0
}
```

配置文件保存后 **约 1 秒内自动生效**，不需要重启游戏。

## Mod Menu 联动（可选）

安装 [Mod Menu](https://modrinth.com/mod/modmenu) 后，本模组会在列表中出现，点击 **Configure**
会直接用系统默认编辑器（Windows 上为**记事本**）打开上面的 JSON 文件，并立刻返回 Mod Menu，
不用退出游戏即可改配置。

Mod Menu 只是编译期依赖（`compileOnly`）：**不安装 Mod Menu 也能正常使用本模组**，
只是少一个快捷入口（此时直接编辑配置文件即可）。

## 设计要点

* 前置需求：只有 **Fabric Loader**，不需要 Fabric API。
* 侵入性极低：只有三处 Mixin，且都只改动“已经存在的”原版绘制路径，不添加贴图/资源包：
  * `HudMixin`：只重定向原版绘制准星的那一次 `blitSprite` 调用。
  * `DebugRendererMixin`：瞄准实体时，用原版 Gizmo API 画一个属于该实体的碰撞箱
    （和原版 `F3+B` 碰撞箱完全同一条渲染路径），因此**不需要打开 `F3+B`**。
  * `EntityHitboxDebugRendererMixin`：如果玩家自己开着 `F3+B` 碰撞箱显示，则改为
    **只把瞄准实体的那个碰撞箱**染成配置的颜色，避免同一个框被画两遍。
* 兼容自定义资源包：仍然使用原版 `minecraft:hud/crosshair` 精灵，只叠加颜色/透明度，
  资源包自带的准星贴图照常生效。
* 原版准星默认使用“反色”混合（`RenderPipelines.CROSSHAIR`），只有真正需要变色或半透明时才改用
  普通的 `GUI_TEXTURED` 颜色混合管线，其余情况完全走原版逻辑。
* **兼容性优先、能不做就不做**：碰撞箱沿用原版 Gizmo 渲染管线；配置值异常（线宽 ≤ 0 或 NaN）时
  直接放弃修改、保留原版外观；瞄准实体绘制一旦抛错会在本次游戏内自动关闭并写一条 WARN 日志，
  不会持续报错或影响渲染；功能关闭时每帧只多一次布尔判断。
* 配置文件读取忽略 UTF-8 BOM（记事本另存为可能带上 BOM），解析失败只警告一次并沿用上一次/默认值。

## 瞄准判定（必须在攻击范围内）

两个功能共用同一套"瞄准的是谁"判定，和原版攻击指示器保持一致：

1. 先取原版拾取结果（`Minecraft.hitResult`）；
2. 没有命中时，再用一条 **受方块阻挡的短射线** 补判，射线长度取玩家当前的
   **攻击范围**（原版 `AttackRange` 数据，`DataComponents.ATTACK_RANGE`，没有该组件时用
   `AttackRange.defaultFor(player)`，因此长柄武器等更远的攻击距离同样生效），
   并且命中点必须通过 `AttackRange#isInRange` 校验。

也就是说：**只有在攻击范围内瞄准实体才会变色 / 显示碰撞箱**，超出攻击范围一律不算，
也不会穿墙（被方块挡住即中断，且射线结果每帧最多计算一次，开销可忽略）。

模组会在日志里（`latest.log`）用 `[EasyCrosshairHitboxTint]` 前缀打印：

* 启动时打印读到的配置内容：`loaded <路径> (crosshair=true, ..., hitbox=true, hitboxColor=#FF0000, ...)`；
* 第一次真正画出瞄准实体碰撞箱时打印：`drawing aimed entity hitbox for minecraft:zombie (...)`；
* 如果改为染原版 `F3+B` 的框，会打印 `tinted aimed entity hitbox (vanilla F3+B display is on)`。

看到第一条说明配置读到了，看到第二/三条说明着色已经生效。

## 构建

已把 Gradle 发行版与依赖仓库指向国内镜像（腾讯/阿里云），`gradlew` 可直接使用：

```bash
./gradlew build          # 产物在 build/libs/
./gradlew runClient      # 开发环境里直接启动客户端
```

`gradle/gradle-daemon-jvm.properties` 已声明构建需要 Java 25，Gradle 会自动挑选本机已安装的 JDK 25
（例如 `~/.jdks/jdk-25.0.1`），无需手动设置 `JAVA_HOME`。

安装：把 `build/libs/easy_entity_crosshair_hitbox_tint-1.0.0.jar` 放进 `.minecraft/mods/`。
