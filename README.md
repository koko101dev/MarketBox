# MarketBox

面向多人服务器的经济模组：**存款生息、玩家市场、转账、收支统计、财富排行**。

> Minecraft 1.21.1 · Fabric · Java 21

[GitHub](https://github.com/chen-0917/MarketBox) · [问题反馈](https://github.com/chen-0917/MarketBox/issues) · [最新版本](https://github.com/chen-0917/MarketBox/releases)

---

## 特性

| 模块 | 说明 |
|---|---|
| **银行** | 存入 / 取出，按固定周期结算利息；支持快捷金额（100 / 1千 / 1万 / 全部，数值可配置） |
| **市场** | 玩家自由上架、购买、下架；支持分类筛选与关键词搜索；挂单到期自动下架并退还 |
| **转账** | 在独立页面选择收款人并直接转账，排行榜中点击玩家可一键跳转 |
| **收支统计** | 收入 / 支出 / 净收益概览、收支比例条、可滚动的交易流水 |
| **财富排行** | 按总资产排序，前三名金银铜标记，自己所在行高亮 |
| **统一导航** | 五个页面共用左侧边栏，一处切换 |

- 服务端权威：所有金额与物品变更都在服务端二次校验
- 增量同步：挂单变更只推送变化项，带宽不随在线人数平方增长
- 客户端可选 Mod Menu：在模组列表里直接打开配置界面

## 环境要求

| 项 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | ≥ 0.16.0 |
| Fabric API | 必需 |
| Java | 21 |
| Mod Menu | 可选，仅用于提供配置入口 |

## 安装

**客户端必须安装**（界面与网络包都在模组内），**服务端也必须安装**（余额与市场数据由服务端持有）。

1. 安装 Fabric Loader
2. 将 `fabric-api` 与 `MarketBox-x.x.x.jar` 放入 `mods/`
3. 启动游戏，默认按键 `Tab` 打开界面（可在设置页内重绑）

## 命令

```
/marketbox balance [玩家]        查询余额（查他人需管理员权限）
/marketbox set <玩家> <金额>     设置余额
/marketbox add <玩家> <金额>     增加余额
/marketbox reset <玩家>          清零余额与统计数据
/marketbox market list           查看当前挂单数量
/marketbox market clear          清空所有挂单并退还物品
/marketbox reload                重新载入服务端配置
```

- `balance` 不带参数查询自己，对所有人开放；其余子命令需要权限等级 ≥ 2
- 玩家名参数支持 Tab 补全，金额参数会提示常用值（100 / 1000 / 10000 / 100000）
- `market clear` 会把物品退还给卖家（离线则进入待领取），并逐个通知客户端移除

## 配置

配置文件位于 `config/` 下，首次启动自动生成。

**`marketbox-common.json`**（服务端权威，改动会同步给客户端）

| 字段 | 默认 | 说明 |
|---|---|---|
| `interestRate` | `0.19` | 每周期利息百分比 |
| `interestIntervalSeconds` | `1800` | 结息周期（秒） |
| `marketFeePercent` | `2.0` | 市场成交手续费（%），从卖家实收中扣除并销毁 |

**`marketbox-client.json`**（仅本地生效）

| 字段 | 默认 | 说明 |
|---|---|---|
| `showHud` | `true` | 显示常驻 HUD |
| `showMarketPriceTooltip` | `true` | 物品提示里显示市场价 |
| `hudPosition` / `hudTextStyle` / `hudScale` / `hudXOffset` / `hudYOffset` | — | HUD 外观 |
| `disableMenuBlur` | `true` | 关闭 1.20.2+ 的菜单背景模糊 |
| `quickAmount1/2/3` | `100 / 1000 / 10000` | 银行与转账页的快捷金额 |

界面内修改即时生效，无需重启。

## 权限

金钱相关的配置**仅管理员（权限等级 ≥ 2）可见可改**，普通玩家打开设置界面只能看到 HUD 与快捷金额：

| 项 | 可见范围 |
|---|---|
| 银行利率、结息周期 | 仅管理员 |
| 市场成交手续费 | 仅管理员 |
| 管理员操作（调整他人余额） | 仅管理员 |
| HUD 外观、快捷金额、按键 | 所有人 |

权限由服务端下发（登录时同步），界面只是隐藏入口；即使伪造请求，服务端仍会二次校验 `hasPermissions(2)`。

管理员在设置界面可直接对在线玩家**设置 / 增加 / 清零**余额，等价于 `/marketbox set|add|reset`。

## 性能与规模

- **市场同步**：新增 / 移除单条推送（约 16–300 字节/条），进服时按 200 条分块拉取全量；不会因为挂单多而突破网络包体积上限
- **挂单上限**：单人 20 条，价格上限 100 万，单笔交易上限 10 亿
- **玩家数据落盘**：交易后合并保存，最多每 10 秒写一次磁盘（避免每笔交易写全服文件）
- **适配规模**：几十人服务器开箱即用；百人以上建议关注市场挂单总量与存档体积

## 构建

```bash
./gradlew bumpVersion   # 递增补丁版本号
./gradlew build         # 产物：build/libs/MarketBox-<version>.jar
```

需要 JDK 21。首次构建会自动下载 Minecraft 与映射。

## 许可

[MIT](LICENSE)

---

## English

**MarketBox** is a server-side economy mod for Fabric 1.21.1: earn interest on deposits, trade items on a player-driven marketplace, transfer money between players, and track your wealth with statistics and leaderboards.

- Server-authoritative: all balance and item changes are validated server-side
- Incremental market sync: only changed listings are broadcast, so bandwidth does not scale with `players × listings`
- Optional Mod Menu integration for the config screen

**Requirements**: Minecraft 1.21.1, Fabric Loader ≥ 0.16.0, Fabric API, Java 21. Mod Menu is optional.
Both client and server must install the mod.

**Commands**: `/marketbox set|add|reset|reload` (OP only).

**Config**: `config/marketbox-common.json` (server, synced) and `config/marketbox-client.json` (client).

**Build**: `./gradlew bumpVersion && ./gradlew build`

Licensed under [MIT](LICENSE).
