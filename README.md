# MarketBox

面向 Minecraft 的服务器经济模组：**存款生息、玩家市场、转账、收支统计、财富排行**。

> Minecraft 1.21.1 · Fabric · Java 21

[GitHub](https://github.com/koko101dev/MarketBox) · [问题反馈](https://github.com/koko101dev/MarketBox/issues) · [最新版本](https://github.com/koko101dev/MarketBox/releases)

---

## ⚠️ 使用前必读

### 关于 AI 生成

**本项目的代码、文档与资源均由 AI 辅助生成**，使用的模型是 **DeepSeek V4.1 Flash**。

如果你对 AI 生成的内容有顾虑，或者你的服务器对内容来源有严格要求，**建议不要使用**。

### 测试范围（请务必看清）

| 项目 | 状态 |
|---|---|
| 单人游戏（100+ 模组共存环境） | ✅ 已测试 |
| 多人游戏基础功能（内置服务器） | ✅ 已测试 |
| 专用服务器启动与模组加载 | ✅ 已验证（44 个模组共存，`Done (0.882s)`） |
| 专用服务器玩家实际连接与长时间运行 | ❌ 未测试 |
| **高强度负载**（数百人并发） | ❌ **未测试** |
| 与其他经济 / 权限模组的兼容性 | ⚠️ 仅做过有限测试 |

专用服务器已确认能正常启动、模组正常加载、无异常抛错，但**没有验证玩家实际连接后的完整交互，也没有做过长时间运行**。作者同样没有条件搭建高负载环境。

### 如果你担心以下情况，请不要使用

- 游戏崩溃
- **存档损坏**
- 移除模组后出现异常
- 经济数值出现意料之外的结果

这是个人项目，没有经过大规模生产环境验证。代码里做了防护（服务端权威校验、存档逐条容错、网络包限流），但**无法提供任何形式的保证**。

### 务必先备份

在**安装前、更新前、移除前**，都请备份世界存档目录（`saves/<世界名>/`）。

---

## 界面

七个页面共用左侧导航栏：

`银行` · `转账` · `市场` · `统计` · `排行` · `经济` · `设置`

管理员额外看到第八项 `黑名单`，用于禁止特定物品上架（如刷怪笼、附魔金苹果）。
该入口对普通玩家不可见。

| 页面 | 内容 |
|---|---|
| **银行** | 存入 / 取出、快捷金额、每日收益预估、下次结息倒计时、最近五次结息 |
| **转账** | 搜索并选择收款人，排行榜可一键跳转 |
| **市场** | 弹窗式上架、**批量上架**、部分购买、我的挂单、分类与搜索 |
| **统计** | 收入 / 支出 / 净收益概览、比例条、**收支按用途拆分**、可滚动流水 |
| **排行** | **总资产 / 存款 / 总收入 / 总支出四种排序**，前三名金银铜标记 |
| **经济** | 货币存量、会话流水、金库余额、通胀提示（服主可拨款） |
| **设置** | 利率、手续费、HUD、按键、快捷金额 |

## 市场机制

**批量上架**：市场左栏的「批量」入口列出背包里可卖的物品（货币除外），
勾选多个后各自填写数量与单价，一次提交全部，不必逐个开关弹窗。
提交时合并为一个网络包，避免单条限流把后面的请求拒掉。

**挂单有效期由服主统一设定**（默认 120 分钟），玩家不能自行选择——
若交给玩家，所有人都会选最长档，市场会被长期挂单占满。

**价格完全由玩家决定**：物品提示显示两行——「最近成交」是最近一笔真实成交的单价，「在售」是当前挂单的**中位数**（避免单条离谱挂单污染显示）。

没有任何内置价目表。既没成交过、也没人挂单的物品不显示价格——没有交易就没有参考意义。

**上架流程**：点击背包物品 → 弹窗中选择数量（滑条 + 按价值自适应的步进）、填写单价、选择时长、可选设置每人限购 → 上架。

**部分购买**：买家可以只买一部分，挂单剩余数量会自动更新，卖光后移除。

**每人限购**：卖家可选择开启，默认不限。开启后单个买家累计购买量超限会被服务端拒绝。

**手续费**：从卖家实收中扣除，可选择销毁（默认，回收货币）或归入市场金库。

- **服务端权威**：所有金额与物品变更都在服务端二次校验，客户端无法伪造
- **增量同步**：挂单变更只推送变化项，带宽不随在线人数平方增长
- **可选 Mod Menu**：在模组列表里直接打开配置界面

## 环境要求

| 项 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | ≥ 0.16.0 |
| Fabric API | 必需 |
| Java | 21 |
| Mod Menu | 可选，仅提供配置入口 |

## 安装

**客户端与服务端都必须安装**（界面与网络包都在模组内，余额与市场数据由服务端持有）。

1. 安装 Fabric Loader
2. 将 `fabric-api` 与 `MarketBox-x.x.x.jar` 放入 `mods/`
3. 启动游戏，默认按 `K` 打开界面（可在「设置」或原版控制设置里重绑）
   - 若该键被其他模组占用，可用客户端命令 `/mb` 打开

## 命令

```
/marketbox help                              显示命令说明
/marketbox balance [玩家]                    查询余额（查他人需等级 2）
/marketbox economy                           经济概览（等级 2）
/marketbox treasury                          查看市场金库（所有人）
/marketbox treasury withdraw <玩家> <金额>    金库拨款（等级 4）
/marketbox listingexpiry [分钟]              查看或设置挂单有效期（等级 4）
/marketbox set <玩家> <金额>                 设置余额（等级 4）
/marketbox add <玩家> <金额>                 增加余额（等级 4）
/marketbox reset <玩家>                      清零余额与统计（等级 4）
/marketbox market list                       查看挂单数量（等级 2）
/marketbox market clear                      清空挂单并退还（等级 4）
/marketbox reload                            重新载入配置（等级 2）
```

- `/mb` 是**客户端命令**，用于在按键被占用时直接打开界面
- 玩家名支持 Tab 补全，金额参数会提示常用值（100 / 1000 / 10000 / 100000）
- 大部分功能也能在界面里完成：经济页可拨款，设置页可调利率与手续费

## 配置

配置文件位于 `config/`，首次启动自动生成。

**`marketbox-common.json`**（服务端权威，登录时同步给客户端）

| 字段 | 默认 | 说明 |
|---|---|---|
| `interestRate` | `0.05` | 每周期利息百分比 |
| `interestIntervalMinutes` | `1440` | 结息周期（分钟），默认一天 |
| `marketFeePercent` | `2.0` | 市场成交手续费（%），从卖家实收中扣除 |
| `listingExpiryMinutes` | `120` | 挂单有效期（分钟），服主统一设定，玩家不可选 |
| `maxListingsPerPlayer` | `20` | 单人同时挂单数量上限（1~200） |
| `maxListingPrice` | `1000000` | 单条挂单价格上限（总价） |
| `maxTransactionAmount` | `1000000000` | 单笔存入、取出、转账上限 |
| `transferEnabled` | `true` | 是否允许玩家之间互相转账 |
| `listingBlacklist` | `[]` | 禁止上架的物品 ID 列表 |
| `marketFeeMode` | `DESTROY` | 手续费去向：`DESTROY` 销毁 / `TREASURY` 进入金库 |

**`marketbox-client.json`**（仅本地生效）

| 字段 | 默认 | 说明 |
|---|---|---|
| `showHud` | `true` | 显示常驻 HUD |
| `showMarketPriceTooltip` | `true` | 物品提示显示市场价 |
| `hudPosition` / `hudTextStyle` / `hudScale` / `hudXOffset` / `hudYOffset` | — | HUD 外观 |
| `disableMenuBlur` | `false` | 是否把全局菜单模糊一并关闭（本模组界面自身已不模糊，默认不干预其他界面） |
| `quickAmount1/2/3` | `100 / 1000 / 10000` | 快捷金额档位 |

> ⚠️ 默认配置是 `0.05% / 1440 分钟`（每天一次），复利年化约 **20%**，适合长期运营。
> 早期版本用 `0.19% / 30 分钟`，复利会让货币一年膨胀上万倍，已不再推荐。
>
> **升级注意**：结息周期的字段已从 `interestIntervalSeconds`（秒）改为 `interestIntervalMinutes`（分钟）。

> 挂单有效期由服主统一设定，玩家不能自行选择。若交给玩家决定，所有人都会选最长档，
> 市场会被长期挂单占满，新挂单很难被看到。默认 120 分钟，可用 `/marketbox listingexpiry` 调整。
> 旧配置不会自动转换，会在启动时回落到默认值（1440 分钟）。可在设置页点「恢复默认」或手动修改。
>
> `marketFeeMode` 只能通过配置文件修改——不放进游戏内设置，是为了避免多个管理员互相把手续费改成给自己。

## 权限

按操作的可逆性分级：

| 操作 | 所需等级 |
|---|---|
| 查看自己余额、HUD 设置、快捷金额 | 无 |
| 上架黑名单管理（增删禁止上架的物品） | 2（普通 OP） |
| 调整利率、结息周期、手续费、市场限制项 | **4（服主）** |
| 改他人余额、金库拨款、查看经济页 | **4（服主）** |

等级 4 对应服务器的 `/op <玩家> 4`。这样普通管理员可以日常维护物品流通，
但动不了钱。界面按等级显示：权限 2 只多出黑名单入口，权限 4 才出现经济相关项。

金钱相关的设置项对普通玩家完全不渲染（不是禁用），服务端对每个请求仍会再次校验 `hasPermissions`——伪造网络包同样会被拒绝。

## 数据与存档

### 移除模组前请按这个顺序操作

1. `/marketbox market clear` 清空所有挂单（物品退还给卖家）
2. 提醒在线玩家领取待领取物品
3. 关闭服务器，备份存档
4. 再移除模组

### 移除后会发生什么

- 玩家数据里的余额以 NBT 形式**残留**在玩家文件中，不可见、不影响游戏
- 世界的 `marketbox_market.dat` 会被保留，可手动删除
- **不会因此损坏存档**

### 存档读写做了哪些容错

- 市场数据逐条解析，单条损坏只跳过该条并记日志，不会让整个世界加载失败
- 玩家余额使用 `long` 存「分」，无浮点误差
- 交易后合并落盘，最多每 10 秒写一次

## 性能与规模

- 市场增量同步，进服按 200 条分块拉取，不会因挂单数量突破网络包上限
- 单人挂单上限 20，单价上限 100 万，单笔交易上限 10 亿
- 全部客户端请求有 80ms 频率限制，防止恶意客户端高频刷包

**适配规模**：几十人服务器在功能上可用。但如前所述，**没有做过百人以上的负载验证**。

## 已知限制

- 专用服务器未测试
- 未做长时间挂机测试（内存泄漏、数据累积）
- 与其他经济 / 权限模组的兼容性未知
- 利率默认值偏激进，适合测试而非长期运营

## 二次开发与分发

本项目使用 [MIT](LICENSE) 许可，你可以自由修改、分发，包括商业用途。

**如果你修改后重新发布**，希望你能：

- 在说明中注明原项目地址
- 保留 `LICENSE` 文件与版权声明

**如果只是自己使用、不对外发布**，不需要任何标注。

## 反馈

遇到 Bug 请到 [Issues](https://github.com/koko101dev/MarketBox/issues) 提交，尽量附上：

- 游戏版本、Fabric Loader 与 Fabric API 版本
- 完整报错日志（`logs/latest.log`）
- 复现步骤

也欢迎直接 Fork 自己修——代码全部开源。

## 第三方资源

货币物品的部分贴图来自 **Utopia Custom**（作者：Limit 小火柴、OrzMiku，**MIT** 许可），
本项目仅复用贴图，未使用其代码。

原项目仓库当前已不可访问，但 MIT 授权不可撤销，本项目按许可要求保留了完整版权声明。
清单与许可原文见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 许可

[MIT](LICENSE) © 2026 koko101

---

## English

> 以下为英文摘要，供非中文用户参考。**中文内容以上方为准**，两者若有出入以中文版本为准。


**MarketBox** is a server-side economy mod for Fabric 1.21.1: bank interest with daily payouts, a player-driven marketplace with partial purchases and per-player limits, transfers, statistics, wealth ranking and a treasury/economy dashboard for server owners.

> ⚠️ **This project is AI-generated** (model: **DeepSeek V4.1 Flash**). If you are uncomfortable with AI-generated software, please do not use it.

**Testing status**

| Area | Status |
|---|---|
| Singleplayer (100+ mods loaded) | ✅ Tested |
| Multiplayer basics (integrated server) | ✅ Tested |
| Dedicated server startup & mod loading | ✅ Verified (44 mods loaded, `Done (0.882s)`) |
| Dedicated server with real players / long-running | ❌ Untested |
| **High load (hundreds of players)** | ❌ **Untested** |

If you are worried about crashes, save corruption, or issues after removing the mod, **please don't use it**. This is a personal project with no warranty. **Always back up your world before installing, updating, or removing it.**

**Requirements**: Minecraft 1.21.1, Fabric Loader ≥ 0.16.0, Fabric API, Java 21. Both client and server must install the mod.

**Pages**: Bank · Transfer · Market · Stats · Ranking · Economy · Settings — all sharing one sidebar.

**Market**: prices come entirely from player activity (no built-in price table) — tooltips show the last deal price and the median of current listings. Listing uses a dialog with a quantity slider, unit price, duration and an optional per-player purchase limit. Buyers can purchase partially; the remaining quantity updates for everyone.

**Permission levels**: level 2 for daily admin (view balances, adjust rate/fees); level 4 for money operations (set balances, clear market, treasury grants).

**Commands**: run `/marketbox help` in game. Most actions are also available in the UI (treasury grants on the Economy page).

**Config**: `config/marketbox-common.json` (server, synced) and `config/marketbox-client.json` (client). `marketFeeMode` (`DESTROY` / `TREASURY`) can only be changed in the file, not in-game.

**Third-party assets**: currency item textures are from **Utopia Custom** by Limit 小火柴 and OrzMiku, used under the **MIT** license. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details and the full license text.

Licensed under [MIT](LICENSE) — free to modify and redistribute. If you republish a modified version, please credit the original repository and keep the LICENSE file.
