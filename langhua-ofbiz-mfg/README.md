# OFBiz Hardware Assembly (MFG) Component

电子产品硬件组装生产流程管理组件，以 **Apache OFBiz 插件**形式提供。

面向"小批量、多品种、SMT 外协 + 自行组装测试"的模式：**产品/BOM/工艺 → 工单 → 工序报工 → SN 履历 → 不良与返修 → 车间看板**。
支持 OFBiz **24.09 及以上**版本，编译目标 **Java 17**。

## 功能

| 模块 | 内容 |
| --- | --- |
| 产品型号 | 型号、SN 前缀、单台标准工时 |
| BOM | CSV 批量导入元件行：`料号,名称,用量,位号,是否关键件` |
| 工艺路线 | 工序模板（组装 / 老化 / 测试 / 包装…），含工位与标准工时 |
| 工单 | 型号 + 计划数量 + 交期 + 客户 + 外协 SMT 厂，编号 `WO-yyMMdd-NNN` |
| SN 管理 | 按工单批量生成 `前缀-yyMMdd-NNNN`，单板全生命周期状态 |
| 工序报工 | 按工单/工序列报工时与合格/不良数量，支持绑定到具体 SN |
| 不良记录 | 不良代码、责任归属（自制/外协/来料）、返修工时 |
| SN 履历 | 一块板：报工记录 + 不良记录 + 所用物料批次 |
| **看板** | 在制工单数/数量、今日工时、今日良率；**售后 KPI（出货台数、售后单、发生率、未结、成本）** + 每日出货/售后柱图 + 责任方成本横条，60 秒自动刷新 |
| **物料批次** | 采购到货台账：内部批次 `LOT-yyMMdd-NN`、供应商批号/DateCode、到货数量；支持 CSV 批量导入 |
| **发料** | 按工单发料（料号 + 批次 + 数量 + 位号），形成“SN → 工单 → 批次”链路 |
| **齐套** | BOM 需求（用量 × 计划数）vs 已发料，自动算出欠料行 |
| **齐套预警** | 按交期列出所有在制工单的欠料明细（欠料行数 / 欠料总量） |
| **外协收发** | 发往 SMT 的套数、回厂板数与不良数、快递单号 |
| **批次追溯** | 反向：批次 → 工单 → SN 数量，用于供应商报批后的召回范围 |
| **FIFO 推定** | 同工单用了多批次时，按发料先后推定各批次覆盖的 SN 区间（仅估算，界面有标注） |
| **返修换件** | 单板级精确记录：SN + 位号/料号 + 原批次 → 新批次 + 原因 + 工时 |
| **出货单** | 出货到客户（可手填 SN 或按工单+数量自动取），出货日即保修起算 |
| **测试数据** | 测试台 CSV 导入（SN,工位,测试项,测量值,单位,判定），工单自动算一次通过率 |
| **外协厂家** | 厂家档案：收件人、手机号、收货地址、**厂家给我们的客户编号**，一键生成可直接复制的邮寄标签 |
| **售后单** | 客户退回登记：SN → 出货日自动算保修到期与保内/保外，责任归属、返修工时、处理状态，SN 页同步显示售后历史 |
| **淘宝售后合规** | 售后类型（退货退款/换货/维修/仅退款）、淘宝订单号、买家签收日；**7 天无理由（168 小时）与 15 天售后窗口自动计算**，列表标出即将到期；换货自动出出货单，维修自动开返修工单 |
| **买家名单（风控）** | 内部风险名单：手机号为主键、昵称次之，观察 / 人工复核两级，带原因与证据；售后单登记时自动继承风险等级并在页面警示；名单只提示、**不拒绝任何法定售后** |
## 目录结构

```
langhua-ofbiz-mfg/
├── ofbiz-component.xml                  # 组件描述符
├── build.gradle                         # 无第三方依赖（仅用于纳入 Gradle 构建）
├── config/
│   └── MfgUiLabels.xml                  # 状态多语言标签
├── data/
│   └── MfgSecurityPermissionSeedData.xml # 权限与权限组种子数据
├── entitydef/
│   ├── entitymodel.xml                  # 17 个业务实体
│   └── entitygroup.xml
├── servicedef/
│   └── services.xml                     # 42 个服务
├── widget/
│   ├── MfgCommonScreens.xml             # 登录/错误页 decorator
│   └── MfgScreens.xml                   # 12 个页面
├── webapp/mfg/                          # controller.xml + web.xml + 12 个 FTL + js/
├── src/main/java/org/langhua/ofbiz/mfg/
└── src/test/java/org/langhua/ofbiz/mfg/
```

## 部署

组件源码由 OFBiz 的 Gradle 构建编译，必须放在 **`plugins/`** 目录下：

```bash
cp -r langhua-ofbiz-mfg $OFBIZ_HOME/plugins/mfg
cd $OFBIZ_HOME && ./gradlew jar
cd $OFBIZ_HOME && ./gradlew ofbiz
```

启动后访问（需登录，OFBiz 管理员即可）：

```
https://localhost:8443/mfg/control/main
```

首次启动会根据 `entitydef/entitymodel.xml` 自动建表（Derby 下为 `MFG_PRODUCT`、`MFG_BOM_ITEM`、`MFG_ROUTING`、
`MFG_WORK_ORDER`、`MFG_SN`、`MFG_REPORT`、`MFG_DEFECT`、`MFG_MATERIAL_LOT`、`MFG_MATERIAL_ISSUE`、`MFG_SHIPMENT`、`MFG_REPLACEMENT`、`MFG_DELIVERY`、`MFG_DELIVERY_SN`、`MFG_TEST_RESULT`、`MFG_VENDOR`、`MFG_WARRANTY_CLAIM`）。
工单表会自动新增 `SMT_VENDOR_ID` 列（指向 `MfgVendor`），产品表会自动新增 `WARRANTY_MONTHS`（保修月数，空则按 12），
售后单表会自动新增淘宝侧列（`CLAIM_TYPE`、`TRADE_NO`、`BUYER_NICK`、`SIGNED_DATE`、`SEVEN_DAY_DEADLINE`、
`AFTER_SALE_DEADLINE`、`REFUND_AMOUNT`、`DEDUCT_AMOUNT`、`FREIGHT_PAYER`、`RETURN_TRACKING_NO`、
`RECEIVED_BACK_DATE`、`INSPECT_RESULT`、`VENDOR_CLAIM_STATUS`、`REPAIR_WORK_ORDER_ID`、`EXCHANGE_DELIVERY_ID`、`BUYER_RISK_LEVEL`）。

## 数据模型

| 实体 | 说明 |
| --- | --- |
| `MfgProduct` | 产品型号：`productCode`（主键）、`snPrefix`、`standardHours`、`warrantyMonths`（保修月数，默认 12）、状态 |
| `MfgBomItem` | BOM 行：`productCode` + `lineSeq`（联合主键）、`componentCode`、`qty`、`designators`（位号）、`isKeyPart` |
| `MfgRouting` | 工序模板：`productCode` + `opSeq`（联合主键）、`opName`、`workCenter`、`stdHours` |
| `MfgWorkOrder` | 工单：`workOrderId`、`productCode`、`plannedQty`、`statusId`、`dueDate`、`customerName`、`smtVendor` |
| `MfgSn` | 单板：`sn`（主键）、`workOrderId`、`productCode`、状态 |
| `MfgReport` | 报工：`reportId`、`workOrderId`、`opSeq`、`sn`（可选）、`operatorName`、`workHours`、`qtyOk`、`qtyNg` || `MfgDefect` | 不良：`defectId`、`workOrderId`、`sn`、`opSeq`、`defectCode`、`qty`、`responsibility`、`repairHours` |
| `MfgMaterialLot` | 物料批次：`lotId`（主键，LOT-yyMMdd-NN）、`componentCode`、`supplierName`、`supplierLot`（DateCode）、`receivedQty`、`receivedDate` |
| `MfgMaterialIssue` | 发料：`issueId`、`workOrderId`、`componentCode`、`lotId`、`qty`、`issuedDate`、可选 `designators` / `snFrom` / `snTo` |
| `MfgShipment` | 外协收发：`shipmentId`、`workOrderId`、`directionId`（`MFG_SHIP_OUT` / `MFG_SHIP_IN`）、`qty`、`defectQty`、`carrier`、`trackingNo` |
| `MfgReplacement` | 返修换件：`replacementId`、`sn`、`componentCode`、`oldLotId` → `newLotId`、`qty`、`reason`、`hours` |
| `MfgDelivery` | 出货单：`deliveryId`（DO-yyMMdd-NN）、`customerName`、`customerPo`、`deliveryDate`、`carrier`、`trackingNo`、`workOrderId` |
| `MfgDeliverySn` | 出货明细：`deliveryId` + `sn`（联合主键），一个 SN 只能出一次货 |
| `MfgTestResult` | 测试结果：`resultId`、`sn`、`workOrderId`、`station`、`testItem`、`measureValue`、`unitOfMeasure`、`passFail`、`testDate` |
| `MfgVendor` | 外协厂家：`vendorId`（V-yyMMdd-NN）、`vendorName`、`vendorType`、`contactName`（收件人）、`contactMobile`、`addressText`、`customerCode`（厂家给我们的编号） |
| `MfgWarrantyClaim` | 售后单：`claimId`（RMA-yyMMdd-NN）、`sn`、`workOrderId`、`customerName`、`deliveryId` / `deliveryDate`（保修起算）、`warrantyEndDate`、`inWarranty`、`receivedDate`、`issueDesc`、`statusId`、`responsibility`、`repairHours`、`closedDate`；淘宝侧：`claimType`（RTN/EXCH/REPAIR/REFUND_ONLY）、`tradeNo`、`buyerNick`、`signedDate`、`sevenDayDeadline`、`afterSaleDeadline`、`refundAmount`、`deductAmount`、`freightPayer`、`returnTrackingNo`、`receivedBackDate`、`inspectResult`、`vendorClaimStatus`、`repairWorkOrderId`、`exchangeDeliveryId` |
| `MfgBuyerFlag` | 买家风险名单：`buyerKey`（`TB:手机号` 或 `TB:N:昵称`）、`channelId`、`buyerNick`、`receiverName`、`receiverMobile`、`addressText`、`riskLevel`（WATCH/BLOCK）、`reasonCode`、`evidenceNote`、`statusId`（ACTIVE/RELEASED）、`releasedDate` |

## 服务

| 服务名 | 说明 |
| --- | --- |
| `mfgProductSave` / `mfgProductList` / `mfgProductDetail` | 型号维护与查询（含 BOM、工艺路线） |
| `mfgBomImport` | 从 CSV 导入 BOM，`replace=N` 时追加而不清空 |
| `mfgRoutingSave` | 新增/更新一道工序 |
| `mfgWorkOrderCreate` / `mfgWorkOrderList` / `mfgWorkOrderDetail` | 工单维护；详情含工序进度、SN、不良、累计工时 |
| `mfgSnRegister` / `mfgSnDetail` | 批量生成 SN；按 SN 查完整履历 |
| `mfgReportCreate` | 报工（工时 + 合格/不良数，可绑定 SN） |
| `mfgDefectCreate` | 登记不良与返修工时 |
| `mfgDashboard` | 看板数据 |
| `mfgLotSave` / `mfgLotList` | 物料批次台账（到货、已发、剩余） |
| `mfgIssueSave` | 向工单发料（校验批次属于该料号） |
| `mfgWorkOrderMaterials` | 齐套（BOM 需求 vs 已发）、发料清单、外协收发汇总 |
| `mfgShipmentSave` | 记录发往 SMT（`MFG_SHIP_OUT`）与回厂收货（`MFG_SHIP_IN`） |
| `mfgTraceByLot` | 反向追溯：批次 → 工单 → SN 数（召回范围），并给出 FIFO 推定 SN 区间 |
| `mfgLotImportCsv` | 从 CSV 批量导入到货批次 |
| `mfgShortageList` | 在制工单的欠料清单（按交期排序） |
| `mfgReplacementSave` | 登记单板级换件（原批次 → 新批次） |
| `mfgDeliverySave` / `mfgDeliveryList` / `mfgDeliveryDetail` | 出货单（SN 绑定、防重复出货、保修起算） |
| `mfgTestImportCsv` / `mfgTestResultList` | 测试结果 CSV 导入与查询 |
| `mfgVendorSave` / `mfgVendorList` | 外协厂家档案（含邮寄标签） |
| `mfgWorkOrderVendor` | 给工单指定外协厂家（收货信息随之显示在工单页） |
| `mfgClaimCreate` | 登记售后单：按 SN 找出货单与产品保修月数，自动算 `warrantyEndDate` 与保内/保外；再按买家签收日算 7 天（168 小时）与 15 天窗口 |
| `mfgClaimUpdate` | 处理售后单：状态、类型、检测结论、退款/扣款、运费承担、退回单号与回库日、索赔状态、返修工时；修正签收日会重算两个窗口 |
| `mfgClaimList` / `mfgClaimDetail` | 售后单列表（按 SN / 类型 / 状态过滤，带剩余小时与即将到期计数）与详情（含换货出货单、换出新机、返修工单、换件与不良数） |
| `mfgClaimExchange` | 换货：自动取同型号未出货的一台（或指定 `newSn`），生成出货单并置为已出货，售后单转 `MFG_RMA_REPLACED` |
| `mfgClaimRepair` | 维修：为退回的单板开一张返修工单，返工工时与换件照常记录 |
| `mfgBuyerFlagSave` / `mfgBuyerFlagList` | 买家风险名单维护与列表（手机号打码显示，可解除/恢复） |
| `mfgBuyerFlagCheck` | 发货前按手机号（自动去格式）或昵称查名单，返回命中项与最高风险等级 |
| `mfgBuyerRiskList` | 按买家昵称聚合售后历史（单数、退款合计、最后收件日）并给出建议等级，仅建议不自动拉黑 |
| `mfgQualityReport` | 售后质量报表：退货/换货/返修/成本率 + 金额 + 按型号、责任方、批次、失效天数、检测结论分解 + 红线判定 |
| `mfgDashboardTrends` | 看板数据：近 N 天每日出货/售后序列（已按最大值缩放到百分比）+ 售后 KPI + 责任方成本 |

示例（Web Tools → Run Service → `mfgBomImport`）：

```
productCode = B5X
csv         = R0402-10K,10K 0402 resistor,4,R1-R4,N
              C0402-100N,100nF 0402 capacitor,6,C1-C6,N
              U1-BT5-SOC,Main SoC,1,U1,Y
              BAT-18650,18650 cell,1,BT1,Y
```

## 页面

所有列表页（工单/产品/物料批次/齐套/出货/厂家/售后单/买家名单）都带 **关键词搜索 + 过滤 + 分页**：
默认每页 20 条，页脚显示“第 X / Y 页（共 N 条）”与首页/上一页/下一页/末页，翻页链接**保留当前搜索与过滤条件**。
分页与搜索都是内存内完成（数据量小），服务结果统一带 `totalCount` / `page` / `pageCount` / `pageSize` / `search`。

| 路径 | 说明 |
| --- | --- |
| `/mfg/control/main` | 工单列表 + 新建工单（含产品型号下拉） |
| `/mfg/control/products?productCode=...` | 产品型号、BOM 导入与工艺路线维护 |
| `/mfg/control/workOrder?workOrderId=...` | 工单详情：工序进度、报工、SN 生成、不良登记 |
| `/mfg/control/sn?sn=...` | 单板履历：报工、不良、所用物料批次 |
| `/mfg/control/dashboard` | 车间看板：生产卡片 + 售后 KPI + 每日柱图 + 责任方成本 + 最近报工，60 秒自动刷新 |
| `/mfg/control/materials` | 物料批次台账、收货登记、CSV 批量导入、按批次反向追溯 |
| `/mfg/control/shortage` | 齐套预警：按交期列出欠料工单与明细 |
| `/mfg/control/delivery` | 出货单：新建、列表、明细；出货后 SN 变为 `MFG_SHIPPED` |
| `/mfg/control/vendors` | 外协厂家：档案维护 + 可复制的邮寄标签（收件人/手机/地址/客户编号） |
| `/mfg/control/claims` | 售后单：登记（类型/订单号/买家/签收日）、按 SN/类型/状态过滤、7 天与 15 天窗口倒计时、处理（检测结论、退款、运费、索赔）、换货与开返修工单 |
| `/mfg/control/buyers` | 买家名单：登记/解除、发货前查询命中、售后历史汇总与建议等级 |
| `/mfg/control/quality` | 质量指标：四率 + 成本率、钱花在哪、按型号/责任方/批次/失效时间/检测结论分解、红线告警 |

邮寄标签旁边有 **「复制标签」按钮**：脚本在静态文件 `webapp/mfg/js/copyLabel.js`，
页面只引 `<script src="/mfg/js/copyLabel.js"></script>`（`web.xml` 的 `allowedPaths` 里加了 `:/js`）。
优先用 `navigator.clipboard`（需要 HTTPS，本机 OFBiz 就是 HTTPS），失败时回退到 `document.execCommand('copy')`，
复制成功后按钮短暂显示「已复制」。

> 因为脚本是外部的，将来 OFBiz 把 `Content-Security-Policy` 从 `Report-Only` 改为强制模式也不需要改动（`default-src 'self'` 允许同源脚本）。

访问控制由 `ofbiz-component.xml` 的 `base-permission="OFBTOOLS"` 决定；
`data/MfgSecurityPermissionSeedData.xml` 定义 `MFG_VIEW` / `MFG_ADMIN` 并授予 `SUPER`、`FULLADMIN`。

## 与学习曲线组件的关系

组装报工产生**真实工时**，正是学习曲线需要的输入：

```
工单(WorkOrder) → 工序报工(MfgReport: workHours/qtyOk) → 学习曲线拟合 → 报价/标准工时修正
```

因此 `langhua-ofbiz-learningcurve` 的手工录入 `historyData` 可以逐步被"按批次导出的实际工时"替代。

## 集成测试

`integration-test/MfgServiceIT.java` 通过 OFBiz 服务引擎（Web Tools 的 `scheduleServiceSync` + `_RUN_SYNC_=Y`）
真正调用服务，覆盖 M1 全链路：

```
productSave → bomImport → routingSave → productDetail
           → workOrderCreate → snRegister → reportCreate → defectCreate
           → workOrderDetail → dashboard
           → lotSave → issueSave → shipmentSave → workOrderMaterials → traceByLot
           → shortageList → lotImportCsv → FIFO 推定 → replacementSave
           → testImportCsv → deliverySave → 重复出货拒绝
           → vendorSave → workOrderVendor
           → claimCreate → claimDetail → claimList → snDetail（含售后）→ claimUpdate（关闭）
           → claimRepair → claimExchange → 重复开单拒绝
           → buyerFlagSave → buyerFlagCheck → 售后单继承风险等级 → 解除后不再命中
           → 脱敏手机号被拒绝 → qualityReport（四率 + 成本 + 红线）
           → dashboardTrends（序列 + KPI）
           → 列表分页（totalCount / pageCount）+ 关键词搜索
           → 非法报工（workHours = 0）/ 错料发料（批次不属于该料号）/ 非法售后状态
```

共 139 项断言，全部通过时退出码为 0。测试是**可重复执行**的：每次创建/更新产品型号 `ITMFG`、替换其 BOM、
新建一张工单、物料批次与出货单，以及相应的报工、不良、发料、收发、换件与测试数据。

前置条件：OFBiz 已启动、已执行 `loadAll`、mfg 组件已部署。

```bash
javac --release 17 -encoding UTF-8 -d build/it-classes integration-test/MfgServiceIT.java
java -cp build/it-classes MfgServiceIT
# 可选参数：-Dofbiz.url=... -Dofbiz.user=... -Dofbiz.password=...
```

## 单元测试

```bash
javac --release 17 -encoding UTF-8 -cp "ofbiz.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar" \
      -d build/test-classes src/test/java/org/langhua/ofbiz/mfg/MfgServicesTest.java \
      src/main/java/org/langhua/ofbiz/mfg/MfgServices.java
java -cp "build/test-classes:ofbiz.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar" \
      org.junit.runner.JUnitCore org.langhua.ofbiz.mfg.MfgServicesTest
```

覆盖 BOM / 批次 / 测试结果 CSV 解析（引号、缺列、CRLF、注释行、大小写归一）、SN 列表拆分、FIFO 推定切片、
邮寄标签格式、SN / 工单编号格式，保修到期日、7 天 / 15 天窗口、剩余小时与运费承担，
以及买家匹配键、手机号归一化与打码、风险等级建议、名单到期判定，还有百分比、日期差与失效分档、售后成本计算、红线判定、看板坐标轴与柱高缩放、关键词过滤与分页切片（共 39 个用例）。

## 追溯精度说明（重要）

SMT 为外协，且对方**不提供上料 / 换料记录**，因此追溯精度分三层：

| 层级 | 精度 | 数据来源 |
| --- | --- | --- |
| 工单级 → 批次集合 | **准确** | 物料全部自采，发料由我方登记（`MfgMaterialIssue`） |
| FIFO 推定 SN 区间 | **估算** | 同工单多批次时，按发料先后与数量比例拆分 SN 序列 |
| 单板换件 | **准确** | 组装 / 返修环节由我方操作，登记 `MfgReplacement` |
| 出厂 → 客户 | **准确** | 出货单把 SN 绑定到客户与出货日（保修起算），一个 SN 只能出一次 |
| 保修判定 | **准确** | 售后单按出货日 + 型号保修月数算到期日；未出货的 SN 一律判为保外 |

所以“召回排查”的推荐做法：先用批次反向追溯圈定工单与推定 SN 区间，再到 SN 页看是否有换件记录确认。

## 电商售后规则对应（淘宝）

依据《淘宝网七天无理由退货规范》（ruleId 5507，2025-07-28 修订）与《淘宝平台争议处理规则》（ruleId 99），
售后单里把平台要求落成了字段与校验：

| 平台要求 | 系统中的落点 |
| --- | --- |
| 7 天无理由 = 签收**次日零时**起满 **168 小时** | `MfgServices.sevenDayDeadline()`：签收日 + 8 天 00:00，存入 `SEVEN_DAY_DEADLINE` |
| 售后入口：确认收货后 15 天内 | `MfgServices.afterSaleDeadline()`：签收日 + 15 天 23:59:59 |
| 数码/家电类**必须**支持 7 天无理由；智能商品（激活即贬值）卖家可选 | `claimType` 区分 `RTN`（退货退款）与 `REFUND_ONLY`，登记时必选 |
| 商品完好：配件/说明书/三包卡齐全，无激活、无难以恢复的使用痕迹 | `deductAmount`（缺件/赠品折价扣款）+ `inspectResult` 检测结论 |
| 运费“谁过错谁承担”：卖家责任来回运费我方；无理由退回买家承担 | `freightPayer`（登记时按类型给默认值，处理时可改） |
| 质量问题举证：肉眼不可识别时卖家要出合格证/执行标准等，送检费卖家承担 | `inspectResult=QUALITY` 后可将检测成本记入 `repairHours` / `note`，`vendorClaimStatus` 向 SMT/供应商索赔 |
| 质量问题属实须承担退货、更换、维修 | `mfgClaimExchange`（换货自动出出货单）/ `mfgClaimRepair`（开返修工单） |
| 判定后需主动召回 | 已有的“批次 → 工单 → SN 区间”反向追溯 + `mfgTraceByLot` |

> 规则会变，落地前建议再核对一次商家中心的最新文本；本组件的期限常量集中在
> `MfgServices.SEVEN_DAY_HOURS` / `AFTER_SALE_DAYS` / `DEADLINE_WARNING_HOURS`，改一处即可。

## 买家名单（内部风控，不是拒卖清单）

`/mfg/control/buyers` 维护一份内部风险名单，解决“同一个买家反复无理由退货 / 空包 / 调包”的问题，
但**刻意不阻断业务**：

| 项目 | 说明 |
| --- | --- |
| 匹配键 | **买家昵称优先**（订单上一定有）`TB:N:nil`；没有昵称时才用手机号 `TB:13800002222`；手机号必须 11 位真号，**脱敏号（138****2222）直接拒绝**，否则不同买家会撞同一个键 |
| 等级 | `WATCH` 观察（只提示）/ `BLOCK` 人工复核（发货前必看） |
| 原因 | `FREQ_RETURN` 高频无理由退货、`EMPTY_BOX` 空包少件、`SWAP` 调包、`ABUSE_7DAY` 滥用 7 天无理由、`FAKE_CLAIM` 虚假质量问题、`THREAT` 差评勒索 |
| 证据 | `evidenceNote` 存聊天截图/开箱视频文件名/平台申诉单号，日后申诉用 |
| 生效与失效 | `statusId` + `releasedDate` 可手动解除；`expiresDate` 默认 12 个月自动失效，**不做永久拉黑** |
| 效果 | 售后单登记时自动把当时的风险等级快照到 `MfgClaim.buyerRiskLevel`，售后单页显示警示条；名单**不拦截**登记、换货或出货 |
| 隐私 | 列表只显示打码号码（`139****6666`），原始号码仅用于表单回填；不保存身份证等实名信息 |
| 建议名单 | 售后历史汇总按昵称聚合：单数 ≥ 3 或退款 ≥ 500 元建议观察，≥ 5 或 ≥ 1000 元建议人工复核（阈值是 `MfgServices.RISK_WATCH_*` / `RISK_BLOCK_*` 常量） |

> **为什么昵称优先**：淘宝订单里收件人姓名/手机号经常是脱敏或平台隐私号（`张**`、`138****2222`、临时号码），
> 拿不到可靠的真实号码；买家昵称与订单号则是明文。若将来通过打单 ERP 拿到真实号码，
> 存进 `receiverMobile` 即可参与匹配，`buyerKey` 仍以昵称为主，不需改结构。

> **合规红线**：质量问题三包与 7 天无理由是法定义务，名单只能改变“怎么发/怎么留证”，不能改变“是否服务”。
> 若买家投诉歧视，有 `evidenceNote` 与解除机制才说得清。

## 前端选型：为什么看板不上框架

看板一律用**服务端渲染 + 原生 CSS/JS**，不引入 React/Vue，也不引图表库：

| 顾虑 | 现状做法 |
| --- | --- |
| 构建链 | OFBiz 只有 Gradle；前端零构建，改模板即生效 |
| 内容安全策略 | CSP `default-src 'self'`，外部 CDN 会被拦；本环境也下载不到 CDN 资源 |
| 图表 | 柱状图用 `div` 高度百分比，横条用 `width` 百分比（`mfgDashboardTrends` 已把数值缩放到 0–100） |
| 自动刷新 | `webapp/mfg/js/dashboardRefresh.js` 每 60 秒 `location.reload()`，保留 URL 上的筛选参数 |
| 降级 | 关掉 JS 看板照样能看，只是不自动刷新 |

只有三种情况才值得单独立项选型，而且应做成**独立应用**而不是混进 OFBiz 视图：
PDA/手机扫码作业、车间大屏、对外客户门户。

## 开发说明（OFBiz 24.09 踩坑记录）

- **模板编码**：`.ftl` 首行必须是 `<#ftl encoding="UTF-8"/>`，否则 FreeMarker 按 JVM 默认字符集
  （中文 Windows + JDK 17 = GBK）解码，中文变乱码。
- **重定向参数**：`<redirect-parameter>` 只接受 **String** 类型的请求属性，Integer 会抛
  `ClassCastException`，因此 `mfgBomImport.importedCount` 返回 String。
- **字段类型**：24.09 没有 `id-ne`，主键用 `id`；`numeric` 对应 Java `Long`；`indicator` 为 `Y/N`。
- **页面错误提示**：屏幕渲染的 FTL 取不到 `requestAttributes["_ERROR_MESSAGE_"]`，因为
  `ScreenRenderer` 会把它取走并从 request 上移除；模板里要用上下文中的 `errorMessageList`
  （列表时用 `<#list>` 展开）。
- **插件编译**：`plugins/` 下的目录必须先有 `build.gradle`（哪怕没有依赖），否则不会被编译，
  运行期报 `ClassNotFoundException`。
- **`<classpath type="dir" location="config"/>`**：需要在组件里真的存在 `config/` 目录，
  因此放入 `MfgUiLabels.xml`。
- **屏幕目录结构**：`<screen>` 下**只能有一个** `<section>`；需要按条件分支时，在 `widgets` 里嵌套多个
  `<section>`，条件写成 `<condition><if-empty field="..."/></condition>`（“非空”用 `<not><if-empty/></not>`，
  `if-not-empty` 不是合法元素）。

## 后续想法

- 来料检验（IQC）：到货批次标记合格/不合格，并影响可发料性。
- 售后成本统计：按型号/责任方汇总退款、返修工时与换件数量，出品质退款率。
- 淘宝订单号自动对单（开放平台/导出表导入），省掉手录订单号。
- 看板自动刷新与钉钉/邮件告警（欠料、测试通过率异常、售后即将超时）。
- 报表导出：良率趋势、工时趋势（可回接学习曲线组件）。
