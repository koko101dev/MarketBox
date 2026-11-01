# MarketBox

面向 Minecraft 的服务器经济模组：**存款生息、玩家市场、转账、收支统计、财富排行**。

> Minecraft 1.21.1 · Fabric · Java 21

[GitHub](https://github.com/chen-0917/MarketBox) · [问题反馈](https://github.com/chen-0917/MarketBox/issues) · [最新版本](https://github.com/chen-0917/MarketBox/releases)

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

## 特性

| 模块 | 说明 |
|---|---|
| **银行** | 存入 / 取出，按周期结算利息；快捷金额可配置；含 14 天复利收益曲线 |
| **市场** | 玩家自由上架、购买、下架；分类筛选与关键词搜索；到期自动下架退还 |
| **转账** | 独立页面选择收款人，排行榜可点击跳转；支持按名字搜索 |
| **收支统计** | 收入 / 支出 / 净收益概览、比例条、可滚动流水 |
| **财富排行** | 总资产排序，前三名金银铜标记，自己所在行高亮 |
| **统一导航** | 五个页面共用左侧边栏 |

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
3. 启动游戏，默认按 `Tab` 打开界面（可在设置页内重绑）

## 命令

```
/marketbox help                  显示命令说明
/marketbox balance [玩家]        查询余额（查他人需管理员）
/marketbox set <玩家> <金额>     设置余额（管理员）
/marketbox add <玩家> <金额>     增加余额（管理员）
/marketbox reset <玩家>          清零余额与统计（管理员）
/marketbox market list           查看挂单数量（管理员）
/marketbox market clear          清空挂单并退还物品（管理员）
/marketbox reload                重新载入配置文件（管理员）
```

- 玩家名参数支持 Tab 补全，金额参数会提示常用值
- 权限判定使用 `hasPermission(2)`

## 配置

配置文件位于 `config/`，首次启动自动生成。

**`marketbox-common.json`**（服务端权威，登录时同步给客户端）

| 字段 | 默认 | 说明 |
|---|---|---|
| `interestRate` | `0.19` | 每周期利息百分比 |
| `interestIntervalSeconds` | `1800` | 结息周期（秒） |
| `marketFeePercent` | `2.0` | 市场成交手续费（%），从卖家实收中扣除并销毁 |

**`marketbox-client.json`**（仅本地生效）

| 字段 | 默认 | 说明 |
|---|---|---|
| `showHud` | `true` | 显示常驻 HUD |
| `showMarketPriceTooltip` | `true` | 物品提示显示市场价 |
| `hudPosition` / `hudTextStyle` / `hudScale` / `hudXOffset` / `hudYOffset` | — | HUD 外观 |
| `disableMenuBlur` | `true` | 关闭菜单背景模糊 |
| `quickAmount1/2/3` | `100 / 1000 / 10000` | 快捷金额档位 |

> ⚠️ 默认利率 `0.19% / 30 分钟` 复利非常快（14 天约 2.5 倍）。长期运营请调低，例如 `0.02%`。

## 权限

金钱相关配置**仅管理员（权限等级 ≥ 2）可见可改**，普通玩家的设置界面只显示 HUD 与快捷金额。

界面隐藏只是入口层，服务端对每个请求都会再次校验——伪造网络包同样会被拒绝。

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

遇到 Bug 请到 [Issues](https://github.com/chen-0917/MarketBox/issues) 提交，尽量附上：

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


**MarketBox** is a server-side economy mod for Fabric 1.21.1: bank interest, a player-driven marketplace, transfers, statistics and wealth ranking.

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

**Commands**: run `/marketbox help` in game.

**Config**: `config/marketbox-common.json` (server, synced) and `config/marketbox-client.json` (client).

**Third-party assets**: currency item textures are from **Utopia Custom** by Limit 小火柴 and OrzMiku, used under the **MIT** license. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details and the full license text.

Licensed under [MIT](LICENSE) — free to modify and redistribute. If you republish a modified version, please credit the original repository and keep the LICENSE file.
