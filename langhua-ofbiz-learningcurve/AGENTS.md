# AGENTS.md — langhua-ofbiz-learningcurve

OFBiz **学习曲线**插件：按历史批次（数量 + 总额/工时）拟合 Wright / Crawford 学习曲线，输出斜率、截距、R²、
待投产批次估算与 CSV 导出；另有一支**软件报价**（功能点口径，参数可调，调 MES 的估算服务）。

- 仓库根 `F:\git\langhua`，本组件 `langhua-ofbiz-learningcurve`，兄弟组件 `langhua-ofbiz-mfg`（硬件组装 MES）
- 运行环境：OFBiz **24.09.07**（`F:\build-ofbiz\apache-ofbiz-24.09.07`，Derby，HTTPS 8443，admin/ofbiz），
  Java 17 corretto，Gradle 8.6；服务端渲染 FTL + Screen widget，无前端框架
- 两个组件的通用约定（OFBiz 踩坑、装饰器、配色、图标、PowerShell 脚本坑）写在
  **`../langhua-ofbiz-mfg/AGENTS.md`**，动手前先看那一份，这里只记本组件特有的部分

## 命令（脚本在 `C:\Users\shi.jinghai-honor\AppData\Local\Temp\jup`）

| 脚本 | 作用 |
| --- | --- |
| `deploy-lc.bat` | 停 OFBiz → robocopy 到 `plugins/learningcurve` → `gradle jar` → 启动（日志 `F:\build-ofbiz\lc-deploy.txt`） |
| `run-tests.bat` | 单元测试 32 个（需要 jama：`C:\Users\shi.jinghai-honor\.m2\repository\gov\nist\math\jama\1.0.3\jama-1.0.3.jar`） |
| `lc-it.bat` | 集成测试 35 项（日志 `lc-it.txt`） |
| `verify-lc.ps1` | 页面巡检 56 项（日志 `lc-verify.txt`） |
| `verify-lcq.ps1` | 软件报价页巡检 37 项（日志 `lc-quote.txt`） |

只改 `webapp/` 下的静态文件时，直接 `Copy-Item` 到 `plugins/learningcurve/...` 就生效，不必整包重发。

## 结构

- 屏幕：`main`（新建估算）/ `records`（记录列表）/ `recordDetail`（详情）/ `quote`（软件报价）
- 模板：`webapp/learningcurve/estimation.ftl` / `records.ftl` / `recordDetail.ftl` / `softwareQuote.ftl`，只写内容，保留自己的 `<h1>`
- 装饰器：`widget/LearningCurveCommonScreens.xml` 的 `lc-decorator` + `webapp/learningcurve/decorator.ftl`，
  屏幕 actions 设 `pageTitle`（学习曲线估算 / 估算记录 / 估算详情）与 `activeMenu`（estimate / records）
- 样式：`webapp/learningcurve/css/lc.css`（`/css` 与 `/images` 已在 `web.xml` 的 `allowedPaths` 里）
- `recordDetail.ftl` 里除了结果表还有「记录不存在」分支和 CSV/返回链接那一段，别删

## 注意

- **与 MES 的联动**：`learningCurveMesBatches`（`LearningCurveServices.mesBatches`）直接读 MES 的
  `MfgWorkOrder` / `MfgReport` 实体，把「一个工单 = 一个批次」汇总成 `数量:工时` 串；估算页 `?mesProduct=<型号>` 会预填表单并自动切口径到 HOURS。
  规则：按 `dueDate` → `createdStamp` 排序；没合格数或没报工工时的工单跳过；数字用 `Locale.ROOT` 的 `0.###`（小数点必须是 `.`）；
  MES 没部署时返回空结果 + 提示，**不要抛异常**
- 两组件的关系图（mermaid）在 `langhua-ofbiz-mfg/README.md` 的「与学习曲线组件的关系」，
  本组件 README 的「与 MES 联动」里是孪生份；改联动口径时两处一起改
- **软件报价**（`quote` 屏幕，`LearningCurveSoftwareQuote`）：公式不在这里，`learningCurveSoftwareQuote`
  只把参数转发给 MES 的 `mfgSoftwareEstimate`（跨组件服务调用）——所以 MES 没部署时报价会报
  “MES 组件未部署或服务不可用”，其余页面照旧能用；金额快照与参数一起存，改费率不改历史
- `learningCurvePdrForecast` 直接读 MES 的 `MfgSoftwareProject`（状态 `MFG_SW_DONE`，`ufp>0`、`actualHours>0`），
  一个交付一个点喂 Wright（`buildPdrSeries`），样本 < 2 时返回 `message` 而不是报错；
  曲线算出的斜率可能是 `-0.0`，输出前要 `+ 0.0` 否则页面会显示 `-0`
- **样本全相同会让最小二乘奇异**（Jama 抛 `Matrix is rank deficient` 的 RuntimeException）：`pdrForecast`
  先判“全部一样”直接给同一个值，再 catch `RuntimeException` 兜底，不能让服务崩
- `learningCurveQuoteToProject` 把报价建成 MES 的软件项目（调 `mfgSoftwareProjectSave`），
  `LearningCurveSoftwareQuote.projectCode` 回写做幂等；报价 CSV 在 `LearningCurveEvents.exportQuotesCsv`
  （事件要 `catch (Exception)`，`queryList()` 抛的是受检的 `GenericEntityException`）
- **`numeric` 字段是 Long，`floating-point` 才是 Double**：`getDouble()` 对 `numeric` 字段会 `ClassCastException`，一律走 `Number`
- **服务返回的中文会被渲染成数字实体**（`&#x8fd9;`），页面断言要改用结构特征（如「没有哪个表头」）或实体形式
- **趋势图是手写 SVG**：`<text>` 默认黑色，深色主题下必须靠 `svg text { fill: var(--fg-muted) }` 才能看见；
  拟合线 `#4daafc`、实测点 `#f14c4c`、坐标轴 `#4b4b4b`
- 配色与图标和 MES 组件一致（VS Code Dark Modern；`logo-{32,64,180}.png` 由
  `../langhua-ofbiz-mfg/tools/make-icons.ps1` 一起生成）
- 数值格式化沿用服务端 `DecimalFormat`，页面只做展示，不要在模板里再算
