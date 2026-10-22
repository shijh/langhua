# AGENTS.md — langhua-ofbiz-mfg

OFBiz **硬件组装生产管理**插件（产品/BOM/工艺 → 工单 → 报工 → SN 履历 → 不良返修 → 看板 → 出货/售后）。
供编码助手在本仓库工作时参考：命令、约定、踩过的坑、关键设计决策。

- 仓库根 `F:\git\langhua`，本组件 `langhua-ofbiz-mfg`，同仓库还有兄弟组件 `langhua-ofbiz-learningcurve`
- 运行环境：OFBiz **24.09.07**（开发 `F:\build-ofbiz\apache-ofbiz-24.09.07`，https 8443；
  生产 `F:\build-ofbiz\ofbiz-prod`，https 8453，两套各一个 Derby 库，可同时跑），
  Java 17 corretto，Gradle 8.6；服务端渲染 FTL + Screen widget，无前端框架

## 命令（脚本都在 `C:\Users\shi.jinghai-honor\AppData\Local\Temp\jup`）

| 脚本 | 作用 |
| --- | --- |
| `deploy-mfg.bat` | **开发实例（8443）**：停 OFBiz → robocopy 到 `plugins/mfg` → `gradle jar` → 启动（日志 `F:\build-ofbiz\mfg-deploy.txt`） |
| `deploy-lc.bat` | 同上，学习曲线插件（日志 `lc-deploy.txt`） |
| `stop-ofbiz.bat` | 停所有 `org.apache.ofbiz.base.start.Start`（会把两套都停掉） |
| `deploy-prod.bat` | **生产实例（8453）**：停生产 → robocopy 两个插件到 `ofbiz-prod\plugins` → `gradle jar` → 启动（日志 `prod-deploy.txt`） |
| `start-prod.bat` / `stop-prod.bat` | 只启/停生产实例（按命令行里的 `ofbiz-prod` 匹配进程） |
| `mfg-tests.bat` | 单元测试（JUnit 4，离线，依赖 `F:\build-ofbiz\lib` 的 jar） |
| `mfg-it.bat` | 集成测试 `integration-test/MfgServiceIT.java`，走 `/webtools/control/scheduleServiceSync` + `_RUN_SYNC_=Y`（日志 `mfg-it.txt`） |
| `verify-m*.ps1` | 页面巡检（真实登录会话），日志 `F:\build-ofbiz\mfg-m*.txt` |

只改 `webapp/` 下的静态文件（css / 图片 / js）时，直接 `Copy-Item` 到 `plugins/mfg/...` 就能生效；
**`.ftl` 不行**（模板缓存命中后不看 mtime），要重新 `deploy-mfg.bat`。

全模块的数据流图在 `README.md` 的「总体流程」（mermaid）；增删实体或服务时顺手更新那张图。
**算式只写在 README 的「计算公式一览」一节**（齐套欠料/FIFO/保修窗口/售后质量/看板/成本/软件报价/学习曲线，
带常量名）；其它小节与页面文案不要另起一份公式，改口径时同步那一节。

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
- **服务的 OUT 属性必须在 services.xml 里声明**，否则 Java 返回的键会被静默丢弃（`mfgDevProjectDetail` 的 `steps` 曾中招）；
  **屏幕里无条件调用的服务，OUT 还要 `optional="true"`**，否则「没有记录」的分支会整页 500（学习曲线 `/recordDetail` 不带 id 曾中招）
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
- **每个 webapp 一个会话**：`/mfg` 与 `/learningcurve`（还有 `/webtools`）的 `auth="true"` **不共用登录**，
  同一个 PS 脚本要分别 POST `/mfg/control/login` 与 `/learningcurve/control/login`；集成测试是登录 `/webtools` 跑服务，
  所以**页面/CSV 下载这类 GET 断言只能放巡检脚本**（IT 里 GET `/mfg/control/*` 会 401）
- **新增一个页面要改三处**：`widget/MfgScreens.xml` 的 `<screen>` + `controller.xml` 的 `<request-map>` **和 `<view-map>`**
  （只加 request-map 会报 `No definition found for view with name [...]`）；导航项在 `decorator.ftl`
- **OFBiz 的错误页也是 HTTP 200**：巡检的 `NoTemplateError` 必须同时扫 `ERROR MESSAGE`、`FTL stack trace`、
  `FreeMarker template error`、`Error rendering`，否则错误页会被当成通过
- **FreeMarker 默认值运算符 `!` 的优先级低于比较**：`<#if (x)!0 gt 0>` 会解析成 `x!(0 gt 0)` 而报
  `Expected a boolean, but this has evaluated to a number+extended_hash`；要写 `<#if ((x)!0) gt 0>`
- **`${x?url}` 要求同一模板里有 `<#setting url_escaping_charset="UTF-8">`**，否则渲染抛
  `To do URL encoding, the framework that encloses FreeMarker must specify the "output_encoding" setting ...`；
  `workOrderDetail.ftl` 没有这个 setting，所以在那里用 `?url` 会让整页报错，而且 POST 后的 302 目标页报错时
  巡检看到的是「200 + template error」；批次号这类安全字符直接拼，不要 `?url`

## 测试与巡检

| 脚本 | 覆盖 | 断言数 |
| --- | --- | --- |
| `MfgServicesTest` | 解析/规则/格式化（CSV、SN、FIFO、窗口、编号、附件类型、来料检验规则、成本取整与毛利、CSV 单元格转义、软件报价公式与耗时率中位数、趋势期间标签与良率/单台工时、库存流水符号与不合格批次可用量、预留对可发量的影响、成品批次命名与单位成本分摊、订单行欠货与待排产量、订单行文本解析、库位类型、外协费计价与单台分摊、问题清单排序与校验…） | 80 |
| `MfgServiceIT` | 全链路服务调用（含 BOM 版本、三条开发线、ECO、附件、来料检验、成本、告警、软件报价与台账、成本细化与外协费明细、步骤问题清单、良率/工时趋势、库存流水与盘点、工单预留/发料释放、完工入库与出货扣减、销售订单两级预留与核销、库位与移库） | 380 |
| `verify-m11.ps1` | 列表页搜索/过滤/翻页/编码 | 55 |
| `verify-m12.ps1` | 装饰器布局（1 个 html、1 条导航、1 个高亮、样式表、无内联 style、无 FTL 报错） | 102 |
| `verify-m13.ps1` | 产品开发 + BOM 版本 + 三条线路 | 49 |
| `verify-m28b.ps1` | 通用附件（上传/列表/原图/缩略图/错误码/删除/PDF，覆盖产品/开发项目/工单/售后单/出货单） | 59 |
| `verify-m29b.ps1` | 网站图标与导航标志（favicon、brand 图、四个尺寸、全站渲染，两个 webapp 各登一次） | 43 |
| `verify-m31.ps1` | 来料检验：四个状态、建批次 → 判不合格 → 发料被拒 → 让步接收 → 放行 | 21 |
| `verify-m32.ps1` | 成本核算：产品页成本块（材料/工时/合计/目标成本/毛利）、标准工时来源标记、批量重算、BOM 单价与金额列、无单价告警、工单成本块（标准 vs 实际，发料后实际材料跟批次单价走） | 39 |
| `verify-m33.ps1` | 看板告警与 CSV 导出（告警区、三个导出链接、`text/csv` + 下载头、表头列数、行内容） | 30 |
| `verify-m34.ps1` | 软件项目：估算试算、保存与编号、台账计划 vs 实际、实测耗时率、超期进告警 | 30 |
| `verify-m35.ps1` | 报表与成本细化：报表页（卡片/趋势表/日周月切换/未知口径回落/喂进学习曲线的链接）、两份趋势 CSV（BOM + 列数 + 合计行）、产品页按工序分摊、工单页工序成本与外协费、开发页问题清单登记→关闭→重开 | 57 |
| `verify-m36.ps1` | 库存：结存页与卡片、流水页与余额、报废入账与超量被拒、盘点单号与差异、安全库存低于阈值标红与建议采购、告警 CSV、工单预留按钮与两次 POST、成品链路（完工入库 → 成品批次 → 出货扣减 → 重复发货被拒） | 63 |
| `verify-m37.ps1` | 销售订单：列表与新建表单、详情（行明细/三个按钮/出货表单）、先锁在库、排产生工单、关单转 CLOSED | 26 |
| `verify-m38.ps1` | 库位与移库：库位页与两个表单、建库位、到货指定库位、按库位看结存、移库两边余额、流水同单号、超量被拒 | 24 |
| `verify-m39.ps1` | 外协费按单分摊：工单页三个表单、加一条按台明细、金额与对账单号、删除后消失、非法计价方式被拒 | 16 |

## 设计决策

- **BOM 版本化**：`MfgBomVersion`（`型号-Vn`，DRAFT/RELEASED/OBSOLETE，`basisVersionId`、`ecoId`）+ `MfgBomVersionItem`；
  `MfgProduct.currentBomVersionId` 指向在用版本；启用新版本会把上一个 RELEASED 转 OBSOLETE；
  工单创建时把版本冻结到 `MfgWorkOrder.bomVersionId`，齐套/发料按工单的版本算。
  旧单表 `MfgBomItem` 行在首次读取时冻结为 `-V1`（幂等），表保留但不再使用
- **软件开发阶段阶梯**：`MFG_STAGE_CONCEPT → DESIGN → PROTOTYPE → TRIAL → MASS`；`MfgProduct.devStageId` 与
  `MfgDevProject.stageId` 相互独立（项目可以先不关联型号）；关卡 PASS/CONDITIONAL 才推进阶段，到 MASS 时写 `actualLaunchDate` 并置 `MFG_DEV_DONE`
- **步骤问题清单**：`MfgDevStepIssue`（`issueId` = `ISSUE-yyMMdd-NN`，`projectCode` + 可选 `stepId`），
  三态 `MFG_ISSUE_OPEN/DOING/CLOSED`、三档严重度 `LOW/MEDIUM/HIGH`；服务 `mfgDevStepIssueSave/List/Status`，
  页面在开发项目页（`id="issueTable"`，锚点 `#issues`），项目列表与步骤表各显示一个未结数（`openIssueCount`）；
  步骤必须属于项目的线路（`projectOfStep`），关闭时写 `closedDate`、重开清空；列表按「未结 → 严重 → 最新」排序
- **良率/工时趋势报表**：`mfgTrendReport` 按 `MfgReport.reportDate` 分桶（`periodKey`：日/ISO 周/月），
  纯函数 `yieldRate` / `hoursPerUnit`（分母 0 记 0，页面显示「—」）；页面 `/mfg/control/reports`
  带 `id="trendTable"`，两份 CSV 走 `MfgExportEvents.yieldCsv/hoursCsv`（同一服务、同一份数字）；
  单台工时可以拿去与学习曲线的实测点对照
- **库存：流水 + 重算（照 OFBiz 的做法）**：`MfgInventoryDetail`（`lotId`+`seqId` 主键、`quantityDiff` 带符号、
  `movementTypeId` = RECEIPT/ISSUE/RETURN/SCRAP/COUNT/ADJUST/RESERVE/RELEASE）**只增不改**；
  `MfgMaterialLot.onHandQty` / `reservedQty` / `availableQty` 永远由 `recalcLotStock()` 用流水 SUM 覆盖写回，不做加减；
  **预留也是流水**：RESERVE/RELEASE 写 `reservedDiff`（±qty），`quantityDiff` 恒为 0，所以在库不动、可发量变；
  `availableOf(onHand, reserved, inspect)`：`MFG_IQC_FAIL` 的批次为 0，否则 `max(0, onHand − reserved)`；
  `ensureLotLedger()` 给旧批次补到货 + 历史发料（幂等），页面/汇总/告警里第一次访问时顺手补；
  发料（`issueSave`）与通用流水（`inventoryMove`）都先校验「不超过该批次可用量」，超了报 “Only X left in lot”；
  **自己的预留不算抢占**：校验用 `available + 本工单在该批次上的预留`，发完再 `releaseReservation` 掉相应数量（`kittingLines` 用局部 `reservedMap`）；
  盘点写 `MfgStockCount` + 一条 COUNT 流水，**不直接改数**；安全库存 `MfgStockSetting.minimumStock` 低于时进 `addStockAlerts`（料号为 refId）
- **工单自动预留**：`workOrderCreate` 建完单就调 `reserveMaterial`（服务 `mfgWorkOrderReserve`）——按批次先后（FIFO）在可发量里挑，
  一次能占多少占多少（`min(欠料, 该批次可发)`），写一条 `MFG_MOVE_RESERVE`；`mfgWorkOrderRelease` 把该工单的预留整体释放（反向 RESERVE–RELEASE 之和）；
  齐套欠料改为 `max(0, 需求 − 已发 − 已预留)`，所以“开工单不占料”会让两张工单都以为料是自己的；
  IT 里库存/预留那几项要用**每次唯一的料号与型号**（否则结存会在多次运行之间累计）
- **成品库存（Step 42，与元件共用批次表）**：`MfgMaterialLot.lotKindId` = `MFG_LOT_COMPONENT`（空值等同）/ `MFG_LOT_PRODUCT`，
  成品批次 `lotId = FG-<工单号>`、`componentCode` = 型号、`workOrderId` 记来源工单、`unitPrice` = 成品单位成本；
  `mfgWorkOrderComplete` 按「已报工良品数 − 已入库量」补 `MFG_MOVE_RECEIPT`（幂等），成本 = `workOrderCost().actualTotal ÷ 良品数`，
  同时把工单置 `MFG_COMPLETED`、SN 置 `MFG_COMPLETED`（**此前 SN 永远停在 IN_PROGRESS**）；
  `deliverySave` 只收 `MFG_COMPLETED` 的 SN，按工单分组从 `FG-<工单号>` 扣 `MFG_MOVE_ISSUE`（`referenceNo` = 出货单号），不够则拒；
  `mfgProductStock` 按型号汇总成品；**所有元件视角都要排除成品批次**（`isProductLot` / `componentLots`：结存、安全库存告警、BOM 预留、来料检验、发料），
  `mfgLotList` 默认只看元件，盘点用 `kind=ALL`
- **销售订单两级预留（Step 43）**：`MfgSalesOrder`（`SO-yyMMdd-NN`，OPEN/PLANNED/SHIPPED/CLOSED/CANCELLED）+ `MfgSalesOrderItem`（`lineSeq` 主键之一，带 `shippedQty`、`workOrderId`、`lineDueDate`）；
  预留向 `MfgInventoryDetail` 多写两列 `salesOrderId` + `salesOrderItemSeq`，所以“订单预留”和“工单预留”是同一张流水表；
  行欠货 `max(0, qty − 已预留 − 已出货)`，`salesOrderPlan` 只为 `欠货 − 在制余量` 开工单（已有工单就加量），完工入库时自动把新成品锁回同行；
  **出货校验看“可发 = 已预留 + 欠货”**，并且 `deliverySave` 校验成品批次可用量时要加回“本订单在该批次上的预留”，否则自己的预留会把自己拦住；
  出货核销 = 行 `shippedQty += n` + 释放同等预留，所有行出完才转 `MFG_SO_SHIPPED`
- **库位与移库（Step 44）**：`MfgStockLocation`（`locationId` = 货架编码，类型 WAREHOUSE/ZONE/BIN，`parentLocationId` 可空）；
  **库位记在流水上**（`MfgInventoryDetail.locationId`），所以“某库位有多少”= 汇总该批次在该库位的流水，批次总量仍是所有库位之和；
  移库 `mfgStockTransfer` 写一对 `MFG_MOVE_TRANSFER`（源 $-qty$ / 目标 $+qty$，同一 `referenceNo` = `TR-yyMMdd-NN`），先校验来源库位余额；
  `locationId` 为空的旧流水归「未分配库位」，可作为来源移出；预留仍然只到批次层（不按库位拆），文档里要说清楚
- **外协费按单分摊（Step 45）**：`MfgOutsourceFee`（`workOrderId` + `seqId` 主键，`feeTypeId` = SMT/TOOLING/ASSEMBLY/TEST/OTHER，`basisId` = PER_UNIT / PER_POINT / LUMP，`rate` `qty` `amount`）；
  金额纯函数 `outsourceAmount(basis, rate, qty, qtyOk, plannedQty)`（按台数量留空取良品数、再取计划数）、`outsourcePerUnit(amount, qtyOk, plannedQty)`；
  `MfgWorkOrder.outsourceAmount` 降为**明细合计的缓存**（`storeOutsourceTotal` 写回），
  所以 `mfgWorkOrderOutsourceSave`（整单直填）在**已有明细时必须报错**，否则两套数字会打架；成本块 `outsourcePerUnit` 直接拿来展示单台分摊
- **`LOT-yyMMdd-NN` 的序号不一定是两位**：同一天超过 99 个批次后会变成 3 位，巡检脚本里的正则是 `LOT-[0-9]{6}-[0-9]+`（踩过：写成 `{2}` 会截断成别的批次并写到错的批次上）
- **所有 `XX-yyMMdd-NN` 编号都会越过 99**（`DEV-` / `ECO-` / `ISSUE-` / `DO-` / `V-` / `RMA-` / `CNT-`）：服务端用 `%02d`，到 100 自然变 3 位，
  所以**断言里的正则一律写 `[0-9]+`**。踩过：IT 里 `DEV-\d{6}-\d{2}` 把 `DEV-261021-103` 截成 `DEV-261021-10`（一个不存在的项目）→
  `devStepStatus` 走了不存在项目的分支，连带 5 项断言假失败；修法是改正则，不是改服务
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
  **标准工时优先按工序模板汇总**（`MfgRouting.stdHours`，页面注“按工序分摊”），没模板才用型号 `standardHours`；
  **型号标准工时有来源**（`standardHoursSource` = MANUAL / LEARNING_CURVE + `standardHoursBaseQty` + `standardHoursEstimateRef`，
  学习曲线组件回填时写，型号页 `data-hours-source="..."` 渲染，手改数值时 `productSave` 退回 MANUAL）；
  工单另记 `MfgWorkOrder.outsourceAmount`（整单外协加工费），实际合计 = 材料 + 工时 + 外协，
  差异给“含外协 / 不含外协”两个口径；逐工序表（`operationCosts`）列出标准/实际/差异，
  报工里出现模板外的 `opSeq` 单列 `inRouting=false`，工时不能丢；
  服务 `mfgProductCost`（qty 台的总价 + 目标成本差与毛利）/ `mfgWorkOrderCost`（标准 vs 实际 + 差异 + operations），
  公用纯函数 `costSummary()`（两位数取整、`targetPrice<=0` 时毛利返回 null）；
  **无单价不当 0**：`missingPriceCount` / `issuesWithoutPrice` 单独计数、页面黄条提示；
  目标成本/售价取该型号**最新一个** `MfgDevProject`（`orderBy("-createdStamp").queryFirst()`，同型号可能有多个项目）；
  两张表分别带 `id="productCost"` / `id="workOrderCost"`，工序成本表 `id="operationCost"`，巡检按 id 切片断言，不碰中文
- **BOM 导入的第一个版本就是当前版本**：`bomImport` 在型号还没有 `currentBomVersionId` 时，把新建的版本直接置 RELEASED 并指给型号，
  否则导完 BOM 页面上还是「未建 BOM」（成本、齐套都读不到）
- **看板告警 + CSV 导出**：服务 `mfgAlerts` 收集「欠料工单」（`shortageRows()`，一行一个缺料元件）与「售后窗口」  （未结售后单的 `sevenDayDeadline` / `afterSaleDeadline`，已过期=ERROR、≤24h=WARN；`hoursLeft` 为 0 表示没设截止，
  所以**判断有没有截止要看 Timestamp 是否为 null**）；排序 ERROR 优先、再按剩余小时升序，页面只列前 20 条；
  三个下载 `dashboardCsv` / `alertsCsv` / `shortageCsv` 在 `MfgExportEvents`（事件 + `<response type="none"/>`），
  文件带 UTF-8 BOM（中文 Excel 否则按 ANSI 读），数字用 `.` 小数点；CSV 转义用 `MfgServices.csvCell/csvLine`（有单测）
- **软件项目（功能点口径）**：`MfgSoftwareProject` 一台账两用——自用记实际工时、对外报价算价格。
  纯函数 `softwareCost(ilf, eif, ufp, swf四项, rdf, pdr, dayRate, taxRate, dncRate)`：
  未调整功能点=35×内部逻辑文件+15×外部接口文件、工作量=耗时率×功能点×软件因素×开发因素、
  工期=1.277×(工作量/176)^0.404、金额=人天×日费率+税+直接非人力成本；
  默认值（2019 西部云谷案例，日费率已从 880 加倍到 1760）：耗时率 **40 人时/功能点**（预估功能点口径）、日费率 1760、税 6%、直接非人力成本 5%、人月折算 176；
  软件因素调整因子 = 基础 1.5 − 复用 0.5 + 前期投入 0.1 + 质量 0.05 这类加减项相加，页面四项可填；
  `plannedDays` 在保存时冻结成快照（改参数不改历史），实测耗时率 = actualHours/功能点，列表返回中位数做基准；
  项目超人天/超期进 `mfgAlerts`（`MFG_ALERT_SOFTWARE`）。**40 人时/功能点与明细功能点的 6~7 人时/功能点 差约六倍，不能混用**；
  页面上一律用中文术语（内部逻辑文件 / 外部接口文件 / 未调整功能点 / 功能点耗时率 / 软件因素调整因子 / 开发因素调整因子），不写英文缩写
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

- 部署到 `plugins/learningcurve`；`deploy-lc.bat`（`lc-deploy.txt`）、`run-tests.bat`（40 个单元测试，需要 jama
  `C:\Users\...\.m2\repository\gov\nist\math\jama\1.0.3\jama-1.0.3.jar`）、`lc-it.bat`（52 项）、
  `verify-lc.ps1`（84 项页面巡检）
- **它与本组件有数据联动（两个服务）**：`learningCurveMesBatches` 读本组件的 `MfgWorkOrder` + `MfgReport`，
  把每个工单折成一个批次（数量 = 报工合格数之和，数值 = 报工工时之和）喂给学习曲线；
  `learningCurveMesPeriods` 按 `reportDate` 分日/ISO 周/月，一个期间一个批次——与本组件报表页同一口径；
  因此改 `MfgReport` 的字段或语义时要同步检查那边
- 联动入口：报表页 `/mfg/control/reports` 尾部的「喂进学习曲线」链接带 `mesGroupBy` + `mesFrom` + `mesTo`，
  目标 `/learningcurve/control/main`（两个 webapp 会话不共享）；学习曲线那边只有带了这些参数才用期间取点，
  否则直接打开仍是样例数据
- **学习曲线能把工时写回型号**（Step 47）：那边 `learningCurveApplyStandardHours` 直接改
  `MfgProduct.standardHours` + `standardHoursSource='LEARNING_CURVE'` + `standardHoursBaseQty` + `standardHoursEstimateRef`，
  所以改 `MfgProduct` 工时字段时要同步检查那边；`productSave` 发现数值变了会把来源退回 `MANUAL`
- **`snPrefix` 是 `very-short`（10 字符）且默认取型号编码**：型号编码超过 10 位又不给 `snPrefix` 时，
  过去是 Derby 报错→错误页（HTTP 200，巡检看不出来），现在 `productSave` 直接返回中文错误；
  巡检脚本建型号用 `PSLC<HHmmss>` 这种 10 位码
- 两个组件的关系图画在 `langhua-ofbiz-mfg/README.md` 的「与学习曲线组件的关系」（mermaid，
  两组件结构一致时的单一出处；孪生份在学习曲线 README 的「与 MES 联动」）
- 屏幕 `main` / `records` / `recordDetail`，模板 `estimation.ftl` / `records.ftl` / `recordDetail.ftl`
- 同一套装饰器做法：`lc-decorator` + `webapp/learningcurve/decorator.ftl` + `css/lc.css`，
  `pageTitle`（学习曲线估算 / 估算记录 / 估算详情）、`activeMenu`（estimate / records）
- 三个模板保留自己的 `<h1>`；`recordDetail.ftl` 里还有「记录不存在」分支和 CSV/返回链接那一段，别删
- 趋势图是手写 SVG：`<text>` 默认黑色，必须 `svg text { fill: var(--fg-muted) }` 才能在深色底上看见
