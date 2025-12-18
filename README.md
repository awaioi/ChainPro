# ChainPro

一个针对生存服务器设计的连锁采集 / 砍树插件，兼顾性能与安全：

- 支持矿物连锁采集
- 支持单棵树砍伐（不会一刀清空整片树林）
- 内置 WorldGuard / GriefPrevention / Residence 领地建造权限检测
- 每个玩家可以随时开启 / 关闭连锁
- 带有冷却、世界黑名单、Y 轴范围、工具白名单等限制
- 支持限制单次连锁的最大方块数（硬上限 20）
- 可选的连锁结束统计提示

---

## 环境与依赖

- 服务器：推荐 Paper 1.21.x
- 构建 JDK：默认使用 Java 21（见 `pom.xml` 中的 `java.version`），如需兼容其他版本可自行调整
- 依赖插件（可选）：
  - `WorldGuard`
  - `GriefPrevention`
  - `Residence`

以上领地插件为软依赖（`softdepend`），如果未安装，则自动跳过对应检查。

---

## 功能概览

### 连锁采集

- 当持有符合条件的工具破坏一个方块时，插件会自动寻找与之相连的同类方块并按配置进行连锁破坏。
- 支持矿物 / 沙子 / 雪等方块的连锁采集，具体类型由配置控制。
- 单次连锁的最大方块数量为 1–20，默认 20，超出会被硬性截断并给出提示。

### 单棵树砍伐

- 当破坏一棵树的树干（原木）时：
  - 插件只会沿着**原木方块**向上 / 向旁扩散，识别为“当前这一棵树”的树干；
  - 不会通过树叶把附近其他树连在一起，从而避免一刀清掉整片树林。
- 是否连锁树叶由配置项控制（见下文 `tree.break-leaves`）。

### 领地与保护插件兼容

实现位置：`org.awaioi.chainpro.protection.ProtectionService`。

- 插件通过反射对接以下常见领地插件：
  - WorldGuard
  - GriefPrevention
  - Residence
- 每一次连锁破坏都会先调用对应插件的“是否允许破坏（build / break）”判断：
  - 任意一个插件拒绝，则本次连锁立即停止；
  - 未安装或调用失败时，默认视为允许（不阻塞破坏）。
- 这样可以最大程度保持与各服常用领地体系的兼容性，同时避免显式编译依赖。

### 内部破坏标记

实现位置：`org.awaioi.chainpro.service.ChainMiningService:isInternalBreak`。

- 插件连锁破坏方块时，会临时将坐标记录到内部集合中；
- `BlockBreakListener` 会跳过这些“内部破坏事件”，避免递归触发下一轮连锁；
- 对玩家手动挖掘或其他插件破坏的方块不做干扰。

### 玩家开关与状态

实现位置：

- `org.awaioi.chainpro.data.PlayerToggleStore`
- `org.awaioi.chainpro.command.ChainProCommand`

每个玩家可以独立控制是否启用连锁采集，状态保存在 `plugins/ChainPro/data.yml` 中，在服务器重启后仍然生效。

---

## 指令与权限

### 指令

注册信息：`src/main/resources/plugin.yml`。

- `/chainpro toggle`
  - 仅玩家可用
  - 切换自己是否开启连锁采集
- `/chainpro status`
  - 仅玩家可用
  - 查看自己当前的连锁采集状态（开启 / 关闭）
- `/chainpro reload`
  - 重载插件配置（`config.yml`），不重启服务器

同时还注册了别名：`/chainmine`、`/cm`。

### 权限

- `chainmine.use`
  - 默认：`true`
  - 允许玩家使用连锁采集（包括命令开关和实际连锁）
- `chainmine.reload`
  - 默认：`op`
  - 允许使用 `/chainpro reload`
- `chainmine.admin`
  - 默认：`op`
  - 也可以使用 `/chainpro reload`（与 `chainmine.reload` 互为“管理级”权限）

---

## 配置说明（config.yml）

配置文件路径：`plugins/ChainPro/config.yml`。

### 基础行为

- `enabled-by-default: true`
  - 玩家从未设置过开关时的默认状态
- `cooldown-ms: 200`
  - 连锁触发冷却，单位毫秒
  - 若上一次连锁距今未超过该时间，将忽略新的触发
- `max-blocks-per-chain: 20`
  - 单次连锁的最大方块数量
  - 即使用更大的值，内部也会被限制在 1–20 之间
- `breaks-per-tick: 16`
  - 每个 Tick 最多破坏的方块数
  - 越小越安全，越大打一刀结束越快，但可能略微加大单 Tick 负载

### 世界与高度限制

- `world-blacklist: []`
  - 禁用连锁采集的世界名称列表（如 `world_nether` 等）
- `y-min: -64`
  - 允许连锁的最低 Y 值
- `y-max: 320`
  - 允许连锁的最高 Y 值

### 方块匹配与工具限制

- `matching.exact-blockdata: false`
  - `true`：要求方块类型与 BlockData 完全一致才会连锁
  - `false`：仅按 Material 类型匹配（大多数场景推荐）
- `tools.whitelist: []`
  - 工具白名单（Material 名称列表）
  - 若为空，则自动允许所有镐 / 斧 / 铲（以 `_PICKAXE` / `_AXE` / `_SHOVEL` 结尾）

### 可连锁方块类型

- `blocks.same-material-whitelist:`
  - 列出了允许“同材质连锁”的基础方块，例如：矿物、沙子、雪等
  - 仅当玩家破坏的方块类型在此列表中时，才会进行“同材质连锁”（非树木场景）

### 树木相关

- `tree.enabled: true`
  - 是否启用砍树连锁
- `tree.break-leaves: false`
  - 是否在砍树时一并连锁树叶
  - `false`（默认）：只连锁树干（原木 / 木块等）
  - `true`：在连锁完本棵树的树干后，再连锁贴在这些树干上的叶子，不会扩散到其它树

### 消息相关

- `messages.notify-chain-count: true`
  - 当一次连锁结束后，是否在 ActionBar 提示“本次连锁了多少个方块”
  - 关闭后不再发送统计提示，但连锁本身仍然正常执行

---

## 保护插件集成细节

实现策略：

- 启动时检测是否存在 WorldGuard / GriefPrevention / Residence 插件；
- 对每个已启用的插件，尝试通过反射构建一个 `Checker`：
  - WorldGuard：通过其 API 判断当前玩家在目标坐标是否有破坏权限；
  - GriefPrevention：检查对应地皮 / 领地是否允许玩家破坏；
  - Residence：检查 `build` 权限；
- 在实际破坏方块前，依次调用所有生效的 `Checker`：
  - 有任意一个返回“不允许”，则立即停止本次连锁任务；
  - 调用过程中若抛异常，则忽略该插件，继续其他检查。

这样可以在不增加编译期依赖的前提下，与多种保护插件保持较好的兼容。

---

## 构建与安装

### 从源码构建

在项目根目录执行：

```bash
mvn package
```

构建成功后，将在 `target/` 目录下生成对应的 jar 文件（如 `ChainPro-1.0.3-SNAPSHOT.jar`）。

### 安装到服务器

1. 将构建出的 jar 放入服务器的 `plugins/` 目录；
2. （可选）将 WorldGuard / GriefPrevention / Residence 等领地插件放入 `plugins/`；
3. 启动服务器，等待插件加载；
4. 在 `plugins/ChainPro/` 中检查是否生成了 `config.yml` 和 `data.yml`；
5. 根据需要编辑 `config.yml`，然后执行 `/chainpro reload` 应用配置。

---

## 使用建议

- 建议将 `max-blocks-per-chain` 保持在 20 或以下，既安全又实用；
- 对于高价值矿物，可以只在 `blocks.same-material-whitelist` 中保留需要连锁的类型；
- 如需更强力的“伐木体验”，可以开启 `tree.break-leaves`，但请结合 TPS 情况调整 `breaks-per-tick`；
- 若服务器安装了多种领地插件，ChainPro 会对它们全部进行检查，不需要额外配置。

