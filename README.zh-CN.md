# London Climbing Finder（伦敦攀岩馆查询）

[English](README.md) | 简体中文

汇总伦敦 33 家攀岩馆的单次票价、非高峰优惠、学生价、会员价和交通时间。选一个日期和时间，
就能看到哪家最便宜、什么时候去最划算。

**网站：** https://london-climbing-finder.pages.dev/

## 使用方式

| | 需要什么 | 怎么用 |
|---|---|---|
| **网站** | 浏览器 | 打开上面的链接，手机上也能用。 |
| **iPhone App** | 一台装了 Xcode 的 Mac，以及任意 Apple ID | 按 [ios/README.md](ios/README.md#run-it-on-your-iphone-free-personal-use)（英文）操作，第一次大约十分钟；也可以让 AI 编程助手代劳（见 [AGENTS.md](AGENTS.md)）。 |
| **离线版面板** | 浏览器 | 下载后双击 `london_climbing_dashboard.html`。 |
| **命令行** | Python 3（无需安装任何包） | `python3 london_climbing_prices.py`（见下文）。 |
| **优惠日历** | 日历 App | 在网站上订阅，或导入 `london-climbing-deals-all.ics`（或不含学生专属优惠的 `-no-student.ics`）。 |

没有 Mac（比如用 Windows）也想在 iPhone 上用：在 iPhone 的 Safari 里打开上面的网站，
点 分享 → **添加到主屏幕**，就会像 App 一样有图标并全屏打开。

希望通过 TestFlight 或 App Store 安装，不用 Mac、也不用每 7 天重装一次？见[支持这个 App](#支持这个-app)。

### 网站和离线面板

- **最便宜（Cheapest）**：按日期给所有岩馆排序。默认按"当天任意时段能拿到的最低价"排，
  并告诉你该在哪个时间段去；切换 "Cheapest mode" 可以改为只看某个具体时间的价格。
- **地图（Map）**：标出所有岩馆，按离出发地的距离排序，并链接到 Google Maps 公共交通路线。
  **Travel** 一栏显示通过 TfL Journey Planner 实时查询的公共交通时间。
- **笔记和星级评分**：每家岩馆的笔记和评分保存在你的浏览器里。可选用 Google 账号登录，
  在多台设备间同步，并发表公开评价。
- 每家岩馆在 `gyms/` 下还有一个独立的纯文本页面，列出价格、优惠、营业时间和官网链接。

### 命令行

```
python3 london_climbing_prices.py                                   # 交互式菜单
python3 london_climbing_prices.py cheapest --date 2026-07-04        # 全天最便宜的岩馆 + 什么时候去
python3 london_climbing_prices.py cheapest --date 2026-07-04 --time 19:00 --concession
python3 london_climbing_prices.py price castle --time 14:00         # 单个岩馆，含路线和你的笔记
python3 london_climbing_prices.py directions "the arch"             # 公共交通 + Citymapper 链接
python3 london_climbing_prices.py travel --from "WC2N 5DU"          # 到每家岩馆的 TfL 交通时间
python3 london_climbing_prices.py offpeak                           # 所有非高峰 / 便宜时段
python3 london_climbing_prices.py students                          # 学生价和优惠价
python3 london_climbing_prices.py promos                            # 当前限时优惠（--all：包括已过期的）
python3 london_climbing_prices.py memberships                       # 月度会员价
python3 london_climbing_prices.py zones                             # 按地铁区（zone）分组
python3 london_climbing_prices.py all                               # 所有岩馆：价格 + 设施
python3 london_climbing_prices.py note castle great cafe, busy pm   # 保存笔记（不写内容即清除）
python3 london_climbing_prices.py rate castle 4                     # 评分 1-5（不写分数即清除）
```

不想每次都写 `--from`，可以在 shell 里设置默认出发地，例如在 `~/.zshrc` 里加
`export CLIMB_FROM="WC2N 5DU"`。只有给了出发地，`price` 才会显示交通时间。

## 价格规则

- **只算单次票。** 价格为单次入场（pay-as-you-go）的英镑价，以 `gyms.json` 里的
  `last_research_date` 为准。去之前请务必再看一眼岩馆官网。
- **高峰与非高峰。** 每家岩馆都有成人价和优惠价（concession）。高峰指晚上和周末，
  非高峰指工作日白天。如果岩馆没有公布非高峰时段，默认按工作日 16:00 之前计算，
  并标注 `~approx`。
- **超级非高峰（Super off-peak）** 指比高峰和非高峰都便宜的特殊时段：Bargain、Magic Time、
  Student Saver、super-off-peak 时段以及买一送一（BOGOF）。
- **银行假日。** 岩馆在银行假日不提供的优惠（Castle 非高峰、LCC Magic Time、Mile End
  超级非高峰、City Bouldering Monday Bargain 等）在这些日期会自动去掉。假日日期实时从
  gov.uk（英格兰和威尔士）获取，`bank_holidays.json` 是离线备份。
- **标记。** `verify` 表示调研时岩馆没有公开这个价格，请点进官网确认。

## 收录的岩馆

每个场馆单独一条记录，有各自的价格和非高峰时段：

- **独立岩馆：** The Castle、Mile End、Yonder、Sen、Westway、The Reach、Rise、Substation
  Brixton、Gantry（Canada Water）、Blocfit（Brixton）、Muro（Peckham）、Rhino Boulder
  （Bromley）、Mount Palace（Crystal Palace）、The Nest（Hayes）、Yellow Spider（Carshalton）
- **City Bouldering：** Aldgate、White City、Stratford，以及 Climbing Co Fulham
- **The Font：** Wandsworth、Borough、Hounslow
- **Climbing District：** Bermondsey（The Arch）、Tottenham Hale（Stronghold）、London Fields
- **London Climbing Centres：** VauxWall West 和 East、HarroWall、CroyWall、RavensWall、
  CanaryWall、BethWall、EustonWall

## 更新数据

`gyms.json` 是唯一的数据源。其他所有副本（网站的 `assets/data.js`、离线面板、各岩馆页面、
日历和 sitemap）都由它生成，不要手动修改这些文件。

每次修改 `gyms.json` 之后：

```
python3 tools/sync_data.py                       # 重新生成所有派生文件
python3 -m unittest discover -s tests            # 价格测试（网站、命令行和 iOS 的结果必须一致）
npm ci && python3 tools/accessibility_check.py   # 可选；需要 Playwright + Chromium
```

把 sync 脚本改动过的文件一起提交。CI 每次 push 都会运行 `sync_data.py --check`，
如果有生成文件过期就会失败，所以网站永远不会和数据不一致。推送到 `main` 会自动重新部署网站。

### 添加岩馆

1. 在 `gyms.json` 里复制一条现有记录，修改各字段。
2. `lat`/`lng` 从岩馆在 Google Maps 上的地点标记获取（地点页面 URL 里的 `!3d…!4d…` 数字），
   不要用邮编——邮编中心点可能偏差几百米。
3. 把岩馆 logo 存成 PNG（不超过 256 像素）放进 `assets/logos/`，并把 `logo` 设为该路径。
   测试会拒绝网络 URL，这样网站和 App 永远不依赖岩馆自己的网站。
4. 把 `_meta.last_research_date` 改成今天。iPhone App 会忽略不比自身更新的网站数据，
   不改的话 App 会一直显示旧的岩馆列表。
5. 运行上面的三条命令。

### 保持价格最新

**每周一**，GitHub Action `price-check.yml` 会重新读取每家岩馆的价格页面（用真实浏览器，
所以 JavaScript 预订组件也能读到），发现变化就开一个 Issue。也可以在仓库的 Actions 页面手动运行。
它从不直接修改数据，只报告两类情况：

- **TO REVIEW（待核对）：** 已存的主要价格（成人或优惠高峰价）在页面上找不到了，说明价格可能变了。
- **NEW OFFERS（新优惠）：** 页面上出现促销用语（"10% off"、"sale"、"promo"、"BOGOF"），
  旁边的英镑金额在 `gyms.json` 里都没有记录，说明可能新增了优惠。只核对价格是发现不了这种情况的，
  因为新优惠不会改动页面上的原价——City Bouldering 的 9 折夏季促销一开始就是这样漏掉的。

把报告里的优惠记进 `limited_promotions` 后，它的价格就算"已知"，不会再被报告。会员价不做这种核对
（大多数价格页不列会员价），但会员优惠仍会出现在促销扫描里。

**自己运行检查：**

```
python3 london_climbing_prices.py check             # 快速：只读纯文本页面
python3 london_climbing_prices.py check --browser   # 同时渲染 JavaScript 页面（pip install playwright; playwright install chromium）
python3 tools/weekly_offer_check.py                 # 检查，然后用 --draft / --apply 把结果写进 gyms.json
```

`weekly_offer_check.py --draft` 会生成 `offers-draft.json` 草稿供你补全；`--apply` 把它合并进
`gyms.json`，并运行同步和测试，但从不提交。每家岩馆的 `scrape` 字段（`needs_js`、URL）
决定它的页面是否需要浏览器渲染。

`cheapest --scrape` 是另一个独立的、尽力而为的实时刷新。只有当它能把某个英镑金额（£3–40）
和 "peak"/"off-peak" 关键词对应起来时才会替换已存价格，读不到的就保留原值。

### 限时优惠和截止日期

`limited_promotions` 里的每条优惠都有一个 `expires` 日期（`YYYY-MM-DD`）或 `null`。

- **`null` 表示"不知道截止日期"，** 而不是"永久有效"。大多数岩馆从不写明截止日期。
- **日期必须有来源。** 只要设置了 `expires`，`expires_source` 就必须写明出处：一个 URL，或者
  类似 `"in-gym poster, seen 2026-08-05"` 的说明。测试会强制检查这一点。官网以外的来源没问题；
  根据优惠名字猜（比如 "Summer 2026 sale"）不行。
- **别混淆两种日期。** "购买后 3 个月内有效" 是票买到手后能用多久，不是优惠什么时候停售。
  只有后者才是 `expires`。
- **过了 `expires` 的优惠在所有地方都会隐藏**（`promos --all` 仍能看到），它的价格也不再算"已知"。
  如果岩馆过了截止日期还在宣传，每周检查会再次报告它。

## 仓库结构

| 路径 | 说明 |
|---|---|
| `gyms.json` | 全部数据。只改这个文件。 |
| `index.html`、`assets/` | 网站：`styles.css`、`app.js`（逻辑）、`data.js`（生成的）、logo 和图标。 |
| `gyms/*/index.html` | 每家岩馆一个生成的页面。 |
| `london_climbing_dashboard.html` | 离线单文件版本（数据是生成的）。 |
| `london_climbing_prices.py` | 命令行工具和价格检查器。 |
| `london-climbing-deals-*.ics` | 生成的优惠日历，分含/不含学生专属优惠两版。 |
| `ios/` | 原生 SwiftUI iPhone App，内置 `gyms.json`。见 [ios/README.md](ios/README.md)（英文）。 |
| `tools/` | `sync_data.py`（生成所有文件）、`weekly_offer_check.py`、`generate_gym_pages.py`、`accessibility_check.py`。 |
| `tests/` | 价格测试，以及证明网站（JS）、命令行（Python）和 App（Swift）计价完全一致的对照测试。没有 `swiftc` 时跳过 Swift 部分。 |
| `.github/workflows/` | `ci.yml`（每次 push 运行测试和一致性检查）、`price-check.yml`（周一价格检查）。 |

## 搭建你自己的副本

想托管自己的网站版本并带上每周检查，请按 [SETUP.md](SETUP.md)（英文）操作（GitHub + Cloudflare
Pages，约十分钟）。用 Google 登录同步笔记和评价是可选的，见 [FIREBASE-SETUP.md](FIREBASE-SETUP.md)（英文）。

## 支持这个 App

iPhone App 是免费的，但要上架 TestFlight 或 App Store，需要加入 Apple 开发者计划，
每年 £79（US$99）。如果这个 App 对你有用、愿意支持一下，金额随意，多少都是帮助：
**[paypal.me/Quanyao](https://paypal.me/Quanyao)**。

## 许可证

代码采用 MIT 许可证（见 [LICENSE](LICENSE)）。岩馆名称和 logo 归各岩馆所有，不在此许可范围内。
价格来自岩馆官网的公开信息，可能已经过时。

如与英文版 [README.md](README.md) 不一致，以英文版为准。
