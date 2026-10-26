# AGENTS.md — langhua-ofbiz-learningcurve

OFBiz **学习曲线**插件：按历史批次（数量 + 总额/工时）拟合 Wright / Crawford 学习曲线，输出斜率、截距、R²、
待投产批次估算与 CSV 导出；另有一支**软件报价**（功能点口径，参数可调，调 MES 的估算服务）。

- 仓库根 `F:\git\langhua`，本组件 `langhua-ofbiz-learningcurve`，兄弟组件 `langhua-ofbiz-mfg`（硬件组装 MES）
- 运行环境：OFBiz **24.09.07**（开发 `F:\build-ofbiz\apache-ofbiz-24.09.07`，HTTPS 8443；
  生产 `F:\build-ofbiz\ofbiz-prod`，HTTPS 8453，各自一个 Derby 库，可同时跑），
  Java 17 corretto，Gradle 8.6；服务端渲染 FTL + Screen widget，无前端框架
- 两个组件的通用约定（OFBiz 踩坑、装饰器、配色、图标、PowerShell 脚本坑）写在
  **`../langhua-ofbiz-mfg/AGENTS.md`**，动手前先看那一份，这里只记本组件特有的部分

## 命令（脚本在 `C:\Users\shi.jinghai-honor\AppData\Local\Temp\jup`）

| 脚本 | 作用 |
| --- | --- |
| `deploy-lc.bat` | 停 OFBiz → robocopy 到 `plugins/learningcurve` → `gradle jar` → 启动（日志 `F:\build-ofbiz\lc-deploy.txt`） |
| `deploy-prod.bat` | 把两个插件同步到生产实例 `F:\build-ofbiz\ofbiz-prod`（https 8453）并重启（日志 `prod-deploy.txt`） |
| `run-tests.bat` | 单元测试 40 个（需要 jama：`C:\Users\shi.jinghai-honor\.m2\repository\gov\nist\math\jama\1.0.3\jama-1.0.3.jar`） |
| `lc-it.bat` | 集成测试 52 项（日志 `lc-it.txt`） |
| `verify-lc.ps1` | 页面巡检 87 项（日志 `lc-verify.txt`） |
| `verify-lcq.ps1` | 软件报价页巡检 37 项（日志 `lc-quote.txt`） |

只改 `webapp/` 下的静态文件时，直接 `Copy-Item` 到 `plugins/learningcurve/...` 就生效，不必整包重发。

## 结构

- 屏幕：`main`（新建估算）/ `records`（记录列表）/ `recordDetail`（详情）/ `quote`（软件报价）
- 模板：`webapp/learningcurve/estimation.ftl` / `records.ftl` / `recordDetail.ftl` / `softwareQuote.ftl`，只写内容，保留自己的 `<h1>`
- 装饰器：`widget/LearningCurveCommonScreens.xml` 的 `lc-decorator` + `webapp/learningcurve/decorator.ftl`，
  屏幕 actions 设 `pageTitle`（学习曲线估算 / 估算记录 / 估算详情）与 `activeMenu`（estimate / records）
- 样式：`webapp/learningcurve/css/lc.css`（`/css` 与 `/images` 已在 `web.xml` 的 `allowedPaths` 里）
- **首次登录强制改密码复用 MES 的服务**：`lc-decorator` 调 `mfgPasswordStatus`（`ignore-error="true"`，MES 没部署时
  本组件页面照常），`decorator.ftl` 里 `mustChange` 为真只输出改密表单，POST 到
  `/learningcurve/control/changePasswordSave`（controller.xml 的 request-map 调 `mfgChangePassword`）。
  开关是系统属性 `org.langhua.mfg.requirePasswordChange=Y`（生产开着，开发实例设成 `N`，否则巡检用 `admin`/`ofbiz` 登录会全红）；
  巡检断言用 `action="/learningcurve/control/changePasswordSave"` / `action="/learningcurve/control/estimate"`，不要断言中文
- `recordDetail.ftl` 里除了结果表还有「记录不存在」分支和 CSV/返回链接那一段，别删
- **自己的登录页 / 改密页 / 报错页（Step 123）**：`controller.xml` 底部 `<view-map name="login">` /
  `name="requirePasswordChange"` 指向 `widget/LearningCurveLoginScreens.xml`（**同名 view-map 后来者胜**），
  顶部一行 `<errorpage>component://learningcurve/webapp/learningcurve/error.ftl</errorpage>`；
  页面本体 `webapp/learningcurve/login.ftl` / `changePassword.ftl` / `error.ftl`，样式在 `lc.css` 末尾的
  `body.signin` 段。细节与坑（门禁页在登录前渲染、不能调 `auth="true"` 服务、表单 POST 回 `/learningcurve/control/login`、
  上下文里的中文已是数字实体别再 `?html`、css 改了要把 `?v=N` 加一）见
  `../langhua-ofbiz-mfg/AGENTS.md` 的 Step 123 那一条。
  **本组件多一个坑**：`lc.css` 有全局 `label { display: inline-block; width: 11em }`，
  登录卡片的 `.signin-field` 必须自己写 `width: 100%`，否则输入框只有 11em 宽。
- **CSRF/话术脚本**：`script/signIn.groovy`（两个组件各一份，内容一致）给登录/改密表单补 CSRF 隐藏域，
  并把「密码错 / 没这个账号 / 锁号」以及找回密码的「密码提示 / 没有提示」翻成中文；
  stock 的 `csrf.defense.strategy` 是空的，所以现在页面不带隐藏域也不影响。
- **找回密码页（Step 124）**：`forgotPassword` 视图与 request-map 都在本组件里重写（`LearningCurveLoginScreens.xml#forgotPassword`
  + `webapp/learningcurve/forgotPassword.ftl`），只留“查看密码提示”；`LoginEvents` 找到提示时返回的是
  `auth`（文本在 `_EVENT_MESSAGE_`），所以自己那份 request-map 把 `auth` 接成 view `forgotPassword`。
  细节见 `../langhua-ofbiz-mfg/AGENTS.md` 的 Step 124。

## 注意

- **算式只在 README 的「计算公式一览」一节**（批次转绘图点 / 对数拟合与迭代剔除 / 待投产批次估算 / 耗时率预测），
  软件报价四步在 MES 那边；改算法时同步那一节与 MES 的同一节
- **与 MES 的联动**：`learningCurveMesBatches`（`LearningCurveServices.mesBatches`）直接读 MES 的
  `MfgWorkOrder` / `MfgReport` 实体，把「一个工单 = 一个批次」汇总成 `数量:工时` 串；估算页 `?mesProduct=<型号>` 会预填表单并自动切口径到 HOURS。
  规则：按 `dueDate` → `createdStamp` 排序；没合格数或没报工工时的工单跳过；数字用 `Locale.ROOT` 的 `0.###`（小数点必须是 `.`）；
  MES 没部署时返回空结果 + 提示，**不要抛异常**
- 两组件的关系图（mermaid）在 `langhua-ofbiz-mfg/README.md` 的「与学习曲线组件的关系」，
  本组件 README 的「与 MES 联动」里是孪生份；改联动口径时两处一起改
- **按期间取点**（`learningCurveMesPeriods`，`buildMesPeriods`）：按 `MfgReport.reportDate` 分日 / ISO 周 / 月
  （`periodKeyOf`，周用 `WeekFields.ISO` 出 `2026-W43`），一个期间一个批次（良品数:工时），跳过良品或工时为 0 的期间，
  用 `TreeMap` 保证时间正序；**估算页只有带了 `mesGroupBy` / `mesFrom` / `mesTo` 才用它**（`periodAsked`），
  否则直接打开页面仍是样例数据 `2:200;3:285;5:450`——巡检里「没有型号时用样例数据」那几项靠这个前提
- **模板里 `单台工时` 出现在两处**（MES 批次表头与按期间框的说明文字），巡检判断「不该有批次表」时要匹配
  `<th class="num">单台工时</th>` 而不是这四个字
- MES 报表页 `/mfg/control/reports` 的「喂进学习曲线」链接指向 `/learningcurve/control/main`，
  带 `mesGroupBy` + `mesFrom` + `mesTo`；两个 webapp 会话不共享，点过去要再登一次
- **屏幕里无条件调用的服务，IN 与 OUT 都要 `optional="true"`**：`learningCurveChartData` 的 `estimateId`
  与 `estimate` / `batches` / `productCode` / `measureType` 漏一个，打开 `/recordDetail`（不带 id）就整页 500
  （HTTP 200 但内容是 `ERROR MESSAGE` 页）——模板里那个「记录不存在」分支就是靠这个前提
- **标准工时回填**（`learningCurveStandardHours` 预览 / `learningCurveApplyStandardHours` 写库）：
  单台工时 = 曲线算的「下一批 n 台」合计 ÷ n（`unitHours`），写 `MfgProduct.standardHours` +
  `standardHoursSource='LEARNING_CURVE'` + `standardHoursBaseQty` + `standardHoursEstimateRef`，
  并记一行 `LearningCurveHoursApply`（旧值不丢）；**只有 `HOURS` 口径能回填**，型号不存在只提示不报错；
  `MfgProduct.standardHoursSource` 为 null 时两边都当作 `MANUAL`（老型号没记来源）
- 回填页的巡检靠 `data-source="..."`（LC 侧）与 `data-hours-source="..."`（MES 型号页）两个属性断言，不碰中文
- **MES 型号编码 ≤ 10 位**：`snPrefix`（`very-short`，10 字符）默认取型号编码，超了整个保存会失败；
  巡检里建型号用 `PSLC<HHmmss>` 这种 10 位码
- **软件报价**（`quote` 屏幕，`LearningCurveSoftwareQuote`）：公式不在这里，`learningCurveSoftwareQuote`
  只把参数转发给 MES 的 `mfgSoftwareEstimate`（跨组件服务调用）——所以 MES 没部署时报价会报
  “MES 组件未部署或服务不可用”，其余页面照旧能用；金额快照与参数一起存，改费率不改历史
- `learningCurvePdrForecast` 直接读 MES 的 `MfgSoftwareProject`（状态 `MFG_SW_DONE`，`ufp>0`、`actualHours>0`），
  一个交付一个点喂 Wright（`buildPdrSeries`），算下一个项目的**功能点耗时率**；样本 < 2 时返回 `message` 而不是报错；
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
