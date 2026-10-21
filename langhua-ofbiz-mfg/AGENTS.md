# AGENTS.md — langhua-ofbiz-mfg

OFBiz **硬件组装生产管理**插件（产品/BOM/工艺 → 工单 → 报工 → SN 履历 → 不良返修 → 看板 → 出货/售后）。
供编码助手在本仓库工作时参考：命令、约定、踩过的坑、关键设计决策。

- 仓库根 `F:\git\langhua`，本组件 `langhua-ofbiz-mfg`，同仓库还有兄弟组件 `langhua-ofbiz-learningcurve`
- 运行环境：OFBiz **24.09.07**（`F:\build-ofbiz\apache-ofbiz-24.09.07`，Derby 库 `OFBIZ`，HTTPS 8443，admin/ofbiz），
  Java 17 corretto，Gradle 8.6；服务端渲染 FTL + Screen widget，无前端框架

## 命令（脚本都在 `C:\Users\shi.jinghai-honor\AppData\Local\Temp\jup`）

| 脚本 | 作用 |
| --- | --- |
| `deploy-mfg.bat` | 停 OFBiz → robocopy 到 `plugins/mfg` → `gradle jar` → 启动 → 等 8443（日志 `F:\build-ofbiz\mfg-deploy.txt`） |
| `mfg-tests.bat` | 单元测试（JUnit 4，离线，依赖 `F:\build-ofbiz\lib` 的 jar） |
| `mfg-it.bat` | 集成测试 `integration-test/MfgServiceIT.java`，走 `/webtools/control/scheduleServiceSync` + `_RUN_SYNC_=Y`（日志 `mfg-it.txt`） |
| `verify-m*.ps1` | 页面巡检（真实登录会话），日志 `F:\build-ofbiz\mfg-m*.txt` |

只改 `webapp/` 下的静态文件（css/图片/ftl）时，直接 `Copy-Item` 到 `plugins/mfg/...` 就能生效，不必整包重发。

## 约定与坑（都踩过）

- `.ftl` 首行必须 `<#ftl encoding="UTF-8">`；FreeMarker 的默认值比较要加括号 `(x!'v') == 'y'`
- `<screen>` 下**只能有一个** `<section>`；条件分支用嵌套 `<section>` + `<condition><if-empty/></condition>`
  （`if-not-empty` 不是合法元素，取反写 `<not><if-empty/></not>`）
- `<redirect-parameter>` 只接受 **String** 请求属性（Integer 会 `ClassCastException`）
- `ScreenRenderer` 会把 `_ERROR_MESSAGE_` 取走 → 模板里用上下文里的 `errorMessageList`
- 静态目录要在 `webapp/mfg/WEB-INF/web.xml` 的 `allowedPaths` 放行（现有 `:/js`、`:/css`、`:/images`）
- 渲染出来的中文/`*` 可能是 HTML 数字实体（`&#x5f20;`、`&#x2a;`），页面断言要按实体形式匹配；
  OFBiz 的错误页也返回 HTTP 200，所以巡检必须扫 `FTL stack trace` / `FreeMarker template error`
- 改完描述符用 `(New-Object System.Xml.XmlDocument).Load(<path>)` 校验
  （`[xml](Get-Content -Raw)` 在这里不可靠，出错时会把整个文件打出来）
- **布局**：每个屏幕把内容放进 `<decorator-screen name="mfg-decorator" location="component://mfg/widget/MfgCommonScreens.xml">`
  的 `<decorator-section name="body">`；`webapp/mfg/decorator.ftl` 用 `sections.render("body")` 输出，
  并读取屏幕 actions 里设的 `pageTitle` / `activeMenu`；页面模板只管自己的内容（保留自己的 `<h1>`）
- **FreeMarker 宏的位置参数**：`<@m 'A' x.y ('p=' + x.y)/>` 会报
  `Expected a method or function, but this has evaluated to a string+extended_hash` ——
  widget 传下来的字段是 `StringHtmlWrapperForFtl`，后面跟 `(` 被当成方法调用；先 `<#assign back = ...>` 再传
- **文件上传**：multipart 表单的 Java 事件要先 `UtilHttp.getParameterMap(request)`（或读
  `request.getAttribute("multiPartMap")`）；文件字段是 `ByteBuffer`，另有 `_<字段>_fileName` / `_contentType` / `_size`；
  输出二进制用 `<response name="success" type="none"/>`
- **服务的 OUT 属性必须在 services.xml 里声明**，否则 Java 返回的键会被静默丢弃（`mfgDevProjectDetail` 的 `steps` 曾中招）
- 屏幕里**无条件调用**的服务，所有 IN 参数要 `optional="true"`，否则不带参数打开列表页会抛
  `ServiceValidationException` → 整页 500；实现里对 null 直接返回空结果
- 集成测试的错误断言看**消息文本**（如 `"Unknown development stage"`），不看 `responseMessage=error`
- `deploy-mfg.bat` 的 robocopy **不删除**目标端多余文件：仓库里删掉的 java 类会留在 `plugins/mfg` 里让 `gradle jar` 失败，
  要手工删一次
- PowerShell 5.1：脚本里有中文会被按 ANSI 读成乱码（新写的 `.ps1` 没有 BOM），巡检脚本尽量只写 ASCII；
  没有 `Invoke-WebRequest -Form`，multipart 要手拼；不要用 `&&`，用 `;`
- 提交前先 `git checkout -- langhua-ofbiz-*/.vscode/tasks.json`（跑任务会让它变脏）；提交信息用文件 + `git commit -F`
- **FreeMarker 没有 `ge` / `le`**，比较只有 `gt` / `gte` / `lt` / `lte`；写成 `ge` 是解析期 `ParseException`，
  而且报的是**后一行的列号**，很难看出是哪一行（曾把 products.ftl:229 报成条件里的 `ge`）
- 负数零会渲染成 `-0.00`：`${(-x)?string("0.00")}` 在 x=0 时就是它，用 `${((x)?abs)?string("0.00")}`
- **改 `.ftl` 后直接 `Copy-Item` 不生效**：OFBiz 的模板缓存命中后不会因为 mtime 变化重载（只有上次加载**失败**时才会重试），
  所以模板改动必须重新 `deploy-mfg.bat`（重启）；Java 改动本来就要重启
- `EntityQuery...queryOne()` 在可能多行时抛 `Passed List had more than one value`（一个型号可以有多个开发项目），
  要排序取一条就用 `queryFirst()`

## 测试与巡检

| 脚本 | 覆盖 | 断言数 |
| --- | --- | --- |
| `MfgServicesTest` | 解析/规则/格式化（CSV、SN、FIFO、窗口、编号、附件类型、来料检验规则、成本取整与毛利…） | 58 |
| `MfgServiceIT` | 全链路服务调用（含 BOM 版本、三条开发线、ECO、附件、来料检验、成本） | 232 |
| `verify-m11.ps1` | 列表页搜索/过滤/翻页/编码 | 55 |
| `verify-m12.ps1` | 装饰器布局（1 个 html、1 条导航、1 个高亮、样式表、无内联 style、无 FTL 报错） | 102 |
| `verify-m13.ps1` | 产品开发 + BOM 版本 + 三条线路 | 49 |
| `verify-m28b.ps1` | 通用附件（上传/列表/原图/缩略图/错误码/删除/PDF，覆盖产品/开发项目/工单/售后单/出货单） | 59 |
| `verify-m29b.ps1` | 网站图标与导航标志（favicon、brand 图、四个尺寸、全站渲染） | 42 |
| `verify-m31.ps1` | 来料检验：四个状态、建批次 → 判不合格 → 发料被拒 → 让步接收 → 放行 | 21 |
| `verify-m32.ps1` | 成本核算：产品页成本块（材料/工时/合计/目标成本/毛利）、批量重算、BOM 单价与金额列、无单价告警、工单成本块（标准 vs 实际，发料后实际材料跟批次单价走） | 38 |

## 设计决策

- **BOM 版本化**：`MfgBomVersion`（`型号-Vn`，DRAFT/RELEASED/OBSOLETE，`basisVersionId`、`ecoId`）+ `MfgBomVersionItem`；
  `MfgProduct.currentBomVersionId` 指向在用版本；启用新版本会把上一个 RELEASED 转 OBSOLETE；
  工单创建时把版本冻结到 `MfgWorkOrder.bomVersionId`，齐套/发料按工单的版本算。
  旧单表 `MfgBomItem` 行在首次读取时冻结为 `-V1`（幂等），表保留但不再使用
- **开发阶段阶梯**：`MFG_STAGE_CONCEPT → DESIGN → PROTOTYPE → TRIAL → MASS`；`MfgProduct.devStageId` 与
  `MfgDevProject.stageId` 相互独立（项目可以先不关联型号）；关卡 PASS/CONDITIONAL 才推进阶段，到 MASS 时写 `actualLaunchDate` 并置 `MFG_DEV_DONE`
- **三条开发线**：项目有 `trackId`（TRACK_PCB / FIRMWARE / ENCLOSURE）+ `partCode`；步骤目录写在代码里
  （`PCB_STEPS` 5、`FIRMWARE_STEPS` 3、`ENCLOSURE_STEPS` 7，最后两步是 外壳入库 → 产品组装），
  状态存 `MfgDevTrackStep`（status/planned/done/owner/vendor/amount/qty/referenceNo/lotId）；
  拿别的线路的步骤来改会被拒绝；`MFG_ENC_STOCK_IN` 完成时按 partCode+qty 生成 `MfgMaterialLot`（用 `step.lotId` 保证幂等），
  产品组装仍是手工开工单
- **ECO**：DRAFT → APPROVED → APPLIED（或 CANCELLED）；只有 APPROVED 能生效，生效时切到 `toBomVersionId`
- **附件（通用）**：`MfgAttachment`（`ownerTypeId` + `ownerId` + `seq` 联合主键，`fileData` 存 BLOB）服务
  PRODUCT / DEV_PROJECT / WORK_ORDER / DELIVERY / CLAIM；服务 `mfgAttachmentSave/List/Delete`，
  事件 `MfgAttachmentEvents.upload/delete/serve`（`/mfg/control/attachmentUpload|attachment|attachmentDelete`，
  旧的 `productImage*` 保留为兼容别名）；页面共用一个宏 `webapp/mfg/attachmentBlock.ftl`，
  产品页/开发项目页/工单详情页/出货页/售后页各调用一次；JPG/PNG/GIF ≤ 4 MB、PDF ≤ 8 MB，
  出错回原页带 `?err=type|size|nofile|owner`，图片 `thumb=1` 现场缩放成 200px JPEG；
  旧表 `MfgProductImage` 首次读产品时复制进来（幂等）
- **来料检验（IQC）**：`MfgMaterialLot.inspectStatusId` 四态 `MFG_IQC_PENDING / PASS / CONCESSION / FAIL`，
  服务 `mfgLotInspect` 记录状态 + `rejectedQty` + `inspector` + `inspectNote` + `inspectDate`；
  **只有 FAIL 挡住发料**（`issueSave` 里 `isLotUsable`），待检和让步接收照常可发——小厂不逐盘全检；
  新建批次默认 PENDING；状态是英文常量，页面断言用 `value="MFG_IQC_*" selected`，别用中文
- **成本核算**：只算材料 + 工时两项，数据全在现有表里——`MfgBomVersionItem.unitPrice`（BOM 导入第 6 列）、
  `MfgProduct.laborRate` + `standardHours`、`MfgMaterialLot.unitPrice`（采购价）+ `MfgIssue`、`MfgReport.workHours`；
  服务 `mfgProductCost`（qty 台的总价 + 目标成本差与毛利）/ `mfgWorkOrderCost`（标准 vs 实际 + 差异），
  公用纯函数 `costSummary()`（两位数取整、`targetPrice<=0` 时毛利返回 null）；
  **无单价不当 0**：`missingPriceCount` / `issuesWithoutPrice` 单独计数、页面黄条提示；
  目标成本/售价取该型号**最新一个** `MfgDevProject`（`orderBy("-createdStamp").queryFirst()`，同型号可能有多个项目）；
  两张表分别带 `id="productCost"` / `id="workOrderCost"`，巡检按 id 切片断言，不碰中文
- **BOM 导入的第一个版本就是当前版本**：`bomImport` 在型号还没有 `currentBomVersionId` 时，把新建的版本直接置 RELEASED 并指给型号，
  否则导完 BOM 页面上还是「未建 BOM」（成本、齐套都读不到）
- **保修**：从出货日开始算，月数在产品上（`warrantyMonths`，默认 12）- **淘宝窗口**：7 天无理由 = 签收日 + 8 天 00:00（即签收次日起 168 小时）；15 天售后 = 签收 + 15 天 23:59:59
- **买家名单**：匹配键优先买家昵称（`TB:N:nick`），因为淘宝收件手机号常被隐私号掩码；手机号必须是 11 位真号、
  带 `*` 一律拒绝；名单 12 个月自动失效；**只提示，不拦截任何法定售后**
- **UI 配色**：两个组件统一 VS Code "Dark Modern"（`:root` 变量：底 `#1f1f1f`、条 `#181818`、部件 `#252526`、
  输入 `#313131`、主色 `#0078d4`、链接 `#4daafc`、ok/warn/error `#89d185`/`#cca700`/`#f14c4c`）；
  `--border: #3a3a3a` 比 VS Code 原生 `#2b2b2b` 亮一档；`h2`/`h3` 带 `border-top` 做区块分隔；
  导航条是一行 sticky、放不下横向滚动；**一页只有一个蓝色主按钮**，表格行内按钮用深灰次要样式
- **网站图标**：源图 `F:\build-ofbiz\langhua-logo.png`（400×400 白底）→ `tools/make-icons.ps1` 把白色转透明并生成
  `logo.png`(512) / `logo-180` / `logo-64` / `logo-32` 到两个组件的 `webapp/<组件>/images/`；
  `decorator.ftl` 里 `rel="icon"` ×2 + `apple-touch-icon`，导航条第一项是 `<img class="brand">`（`alt=""`，CSS `height:1.7em`）

## 兄弟组件 langhua-ofbiz-learningcurve

- 部署到 `plugins/learningcurve`；`deploy-lc.bat`（`lc-deploy.txt`）、`run-tests.bat`（30 个单元测试，需要 jama
  `C:\Users\...\.m2\repository\gov\nist\math\jama\1.0.3\jama-1.0.3.jar`）、`lc-it.bat`（18 项）、
  `verify-lc.ps1`（56 项页面巡检）
- **它与本组件有数据联动**：`learningCurveMesBatches` 读本组件的 `MfgWorkOrder` + `MfgReport`，
  把每个工单折成一个批次（数量 = 报工合格数之和，数值 = 报工工时之和）喂给学习曲线；
  因此改 `MfgReport` 的字段或语义时要同步检查那边
- 屏幕 `main` / `records` / `recordDetail`，模板 `estimation.ftl` / `records.ftl` / `recordDetail.ftl`
- 同一套装饰器做法：`lc-decorator` + `webapp/learningcurve/decorator.ftl` + `css/lc.css`，
  `pageTitle`（学习曲线估算 / 估算记录 / 估算详情）、`activeMenu`（estimate / records）
- 三个模板保留自己的 `<h1>`；`recordDetail.ftl` 里还有「记录不存在」分支和 CSV/返回链接那一段，别删
- 趋势图是手写 SVG：`<text>` 默认黑色，必须 `svg text { fill: var(--fg-muted) }` 才能在深色底上看见
