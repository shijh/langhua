# OFBiz Hardware Assembly (MFG) Component

电子产品硬件组装生产流程管理组件，以 **Apache OFBiz 插件**形式提供。

面向"小批量、多品种、SMT 外协 + 自行组装测试"的模式：**产品/BOM/工艺 → 工单 → 工序报工 → SN 履历 → 不良与返修 → 车间看板**。
支持 OFBiz **24.09 及以上**版本，编译目标 **Java 17**。

## 功能

| 模块 | 内容 |
| --- | --- |
| 产品型号 | 型号、SN 前缀、单台标准工时、**开发阶段**（概念/设计/打样/试产/量产） |
| **附件（通用）** | 任意业务单据挂文件：产品照片、开发项目资料、工单照片、出货签收单、售后凭证；JPG/PNG/GIF（≤ 4 MB）与 PDF（≤ 8 MB），存数据库不丢；图片显示 200px 缩略图，点开看原图，可删除 |
| BOM | CSV 批量导入元件行：`料号,名称,用量,位号,是否关键件,单价`（单价可空） |
| **BOM 版本** | 型号可保留多个 BOM 版本（`型号-V1`、`-V2`…），每个版本独立存行；型号指向“当前使用”的版本，启用新版本时旧版本自动废止；工单创建时冻结所用版本 |
| **产品开发** | 开发项目立项（可选线路：**PCB / 固件 / 外壳**，带料号；目标成本/售价/计划上市日），五道阶段关卡（概念→设计→打样→试产→量产），评审通过自动进入下一阶段 |
| **三条开发线** | 每个项目按线路带自己的步骤清单，逐步打勾，每步可记负责人、供应商、金额、数量、单号与备注；外壳线「外壳入库」完成时自动生成物料批次 |
| **设计变更 ECO** | 变更单（BOM/工艺/设计/物料替代）走 草稿 → 批准 → 生效；生效时自动切换到指定 BOM 版本 |
| 工艺路线 | 工序模板（组装 / 老化 / 测试 / 包装…），含工位与标准工时 |
| 工单 | 型号 + 计划数量 + 交期 + 客户 + 外协 SMT 厂，编号 `WO-yyMMdd-NNN` |
| SN 管理 | 按工单批量生成 `前缀-yyMMdd-NNNN`，单板全生命周期状态 |
| 工序报工 | 按工单/工序列报工时与合格/不良数量，支持绑定到具体 SN |
| 不良记录 | 不良代码、责任归属（自制/外协/来料）、返修工时 |
| SN 履历 | 一块板：报工记录 + 不良记录 + 所用物料批次 |
| **看板** | 在制工单数/数量、今日工时、今日良率；**售后 KPI（出货台数、售后单、发生率、未结、成本）** + 每日出货/售后柱图 + 责任方成本横条，60 秒自动刷新 |
| **物料批次** | 采购到货台账：内部批次 `LOT-yyMMdd-NN`、供应商批号/DateCode、到货数量；支持 CSV 批量导入 |
| **来料检验（IQC）** | 批次可标待检 / 合格 / 让步接收 / 不合格并记录不良数与检验人；**不合格批次不能发料**，待检与让步接收仍可用 |
| **发料** | 按工单发料（料号 + 批次 + 数量 + 位号），形成“SN → 工单 → 批次”链路 |
| **成本核算** | 型号层：BOM 单价 × 用量 + 标准工时 × 工时费率 = 单台成本，可乘批量算总额，并与开发项目的目标成本 / 目标售价对比（超了、亏了分色标出）；工单层：标准成本 vs 实际成本（已发料按批次采购单价 + 报工工时 × 费率），给出差异 |
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
│   ├── entitymodel.xml                  # 25 个业务实体
│   └── entitygroup.xml
├── servicedef/
│   └── services.xml                     # 61 个服务
├── widget/
│   ├── MfgCommonScreens.xml             # 登录/错误页 decorator + 页面布局装饰器 mfg-decorator
│   └── MfgScreens.xml                   # 14 个页面（内容包在 mfg-decorator 里）
├── webapp/mfg/                          # controller.xml + web.xml + 15 个 FTL + decorator.ftl + attachmentBlock.ftl + css/ + js/
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
`MFG_WORK_ORDER`、`MFG_SN`、`MFG_REPORT`、`MFG_DEFECT`、`MFG_MATERIAL_LOT`、`MFG_MATERIAL_ISSUE`、`MFG_SHIPMENT`、`MFG_REPLACEMENT`、`MFG_DELIVERY`、`MFG_DELIVERY_SN`、`MFG_TEST_RESULT`、`MFG_VENDOR`、`MFG_WARRANTY_CLAIM`、`MFG_ATTACHMENT`）。
工单表会自动新增 `SMT_VENDOR_ID` 列（指向 `MfgVendor`），产品表会自动新增 `WARRANTY_MONTHS`（保修月数，空则按 12），
售后单表会自动新增淘宝侧列（`CLAIM_TYPE`、`TRADE_NO`、`BUYER_NICK`、`SIGNED_DATE`、`SEVEN_DAY_DEADLINE`、
`AFTER_SALE_DEADLINE`、`REFUND_AMOUNT`、`DEDUCT_AMOUNT`、`FREIGHT_PAYER`、`RETURN_TRACKING_NO`、
`RECEIVED_BACK_DATE`、`INSPECT_RESULT`、`VENDOR_CLAIM_STATUS`、`REPAIR_WORK_ORDER_ID`、`EXCHANGE_DELIVERY_ID`、`BUYER_RISK_LEVEL`）。

## 数据模型

| 实体 | 说明 |
| --- | --- |
| `MfgProduct` | 产品型号：`productCode`（主键）、`snPrefix`、`standardHours`、`laborRate`（工时费率，成本核算用）、`warrantyMonths`（保修月数，默认 12）、`devStageId`（开发阶段）、`currentBomVersionId`（当前 BOM 版本）、状态 |
| `MfgBomItem` | 旧版单表 BOM 行（`productCode` + `lineSeq`）。**已停用**，首次读取时自动冻结为 `型号-V1` 版本，新代码只用版���表 |
| `MfgBomVersion` | BOM 版本：`bomVersionId`（主键，`型号-Vn`）、`versionNo`、`statusId`（DRAFT/RELEASED/OBSOLETE）、`effectiveDate`、`basisVersionId`（从哪版复制）、`ecoId`（哪张变更单引入） |
| `MfgBomVersionItem` | 版本内 BOM 行：`bomVersionId` + `lineSeq`（联合主键）、`componentCode`、`qty`、`designators`、`isKeyPart`、`unitPrice`（单价，成本核算用，可空） |
| `MfgAttachment` | 通用附件：`ownerTypeId` + `ownerId` + `seq`（联合主键）、`fileName`、`contentType`、`fileSize`、`fileData`（`byte-array` → BLOB）、`note`；`ownerTypeId` 取值 `MFG_ATT_PRODUCT` / `MFG_ATT_DEV_PROJECT` / `MFG_ATT_WORK_ORDER` / `MFG_ATT_DELIVERY` / `MFG_ATT_CLAIM`，序号从 1 递增 |
| `MfgProductImage` | 旧版产品照片（`productCode` + `seq`）。**已停用**，首次读取产品时自动复制进 `MfgAttachment` |
| `MfgRouting` | 工序模板：`productCode` + `opSeq`（联合主键）、`opName`、`workCenter`、`stdHours` |
| `MfgWorkOrder` | 工单：`workOrderId`、`productCode`、`plannedQty`、`statusId`、`dueDate`、`customerName`、`smtVendor`、`bomVersionId`（建单时冻结的 BOM 版本） |
| `MfgSn` | 单板：`sn`（主键）、`workOrderId`、`productCode`、状态 |
| `MfgReport` | 报工：`reportId`、`workOrderId`、`opSeq`、`sn`（可选）、`operatorName`、`workHours`、`qtyOk`、`qtyNg` || `MfgDefect` | 不良：`defectId`、`workOrderId`、`sn`、`opSeq`、`defectCode`、`qty`、`responsibility`、`repairHours` |
| `MfgMaterialLot` | 物料批次：`lotId`（主键，LOT-yyMMdd-NN）、`componentCode`、`supplierName`、`supplierLot`（DateCode）、`receivedQty`、`receivedDate`、`unitPrice`（采购单价，算实际成本用）；来料检验：`inspectStatusId`（MFG_IQC_PENDING/PASS/CONCESSION/FAIL）、`rejectedQty`、`inspectDate`、`inspector`、`inspectNote` |
| `MfgMaterialIssue` | 发料：`issueId`、`workOrderId`、`componentCode`、`lotId`、`qty`、`issuedDate`、可选 `designators` / `snFrom` / `snTo` |
| `MfgShipment` | 外协收发：`shipmentId`、`workOrderId`、`directionId`（`MFG_SHIP_OUT` / `MFG_SHIP_IN`）、`qty`、`defectQty`、`carrier`、`trackingNo` |
| `MfgReplacement` | 返修换件：`replacementId`、`sn`、`componentCode`、`oldLotId` → `newLotId`、`qty`、`reason`、`hours` |
| `MfgDelivery` | 出货单：`deliveryId`（DO-yyMMdd-NN）、`customerName`、`customerPo`、`deliveryDate`、`carrier`、`trackingNo`、`workOrderId` |
| `MfgDeliverySn` | 出货明细：`deliveryId` + `sn`（联合主键），一个 SN 只能出一次货 |
| `MfgTestResult` | 测试结果：`resultId`、`sn`、`workOrderId`、`station`、`testItem`、`measureValue`、`unitOfMeasure`、`passFail`、`testDate` |
| `MfgVendor` | 外协厂家：`vendorId`（V-yyMMdd-NN）、`vendorName`、`vendorType`、`contactName`（收件人）、`contactMobile`、`addressText`、`customerCode`（厂家给我们的编号） |
| `MfgWarrantyClaim` | 售后单：`claimId`（RMA-yyMMdd-NN）、`sn`、`workOrderId`、`customerName`、`deliveryId` / `deliveryDate`（保修起算）、`warrantyEndDate`、`inWarranty`、`receivedDate`、`issueDesc`、`statusId`、`responsibility`、`repairHours`、`closedDate`；淘宝侧：`claimType`（RTN/EXCH/REPAIR/REFUND_ONLY）、`tradeNo`、`buyerNick`、`signedDate`、`sevenDayDeadline`、`afterSaleDeadline`、`refundAmount`、`deductAmount`、`freightPayer`、`returnTrackingNo`、`receivedBackDate`、`inspectResult`、`vendorClaimStatus`、`repairWorkOrderId`、`exchangeDeliveryId` |
| `MfgBuyerFlag` | 买家风险名单：`buyerKey`（`TB:手机号` 或 `TB:N:昵称`）、`channelId`、`buyerNick`、`receiverName`、`receiverMobile`、`addressText`、`riskLevel`（WATCH/BLOCK）、`reasonCode`、`evidenceNote`、`statusId`（ACTIVE/RELEASED）、`releasedDate` |
| `MfgDevProject` | 开发项目：`projectCode`（DEV-yyMMdd-NN）、`projectName`、`productCode`（可空，与型号解耦）、`trackId`（PCB/固件/外壳/空）、`partCode`（板号或外壳料号）、`stageId`（概念/设计/打样/试产/量产）、`statusId`、`ownerName`、`targetCost`、`targetPrice`、`plannedLaunchDate`、`actualLaunchDate` |
| `MfgDevTrackStep` | 线路步骤状态：`projectCode` + `stepId`（联合主键）、`statusId`（TODO/DOING/DONE/SKIPPED）、`plannedDate`、`doneDate`、`ownerName`、`vendorName`、`amount`、`qty`、`referenceNo`（合同号/付款凭证/快递单）、`lotId`（外壳入库生成的批次） |
| `MfgDevStageReview` | 阶段关卡评审：`projectCode` + `stageId`（联合主键）、`plannedDate`、`doneDate`、`resultId`（PASS/CONDITIONAL/FAIL）、`reviewer`、`note` |
| `MfgDevEco` | 设计变更单：`ecoId`（ECO-yyMMdd-NN）、`productCode`、`projectCode`、`ecoTypeId`（BOM/工艺/设计/物料）、`reason`、`changeSummary`、`statusId`（DRAFT/APPROVED/APPLIED/CANCELLED）、`fromBomVersionId` → `toBomVersionId`、`requestedBy`、`approvedBy`、`effectiveDate` |

## 服务

| 服务名 | 说明 |
| --- | --- |
| `mfgProductSave` / `mfgProductList` / `mfgProductDetail` | 型号维护与查询（含当前 BOM 版本的行、工艺路线、版本数） |
| `mfgBomImport` | 从 CSV 导入 BOM 到指定版本（缺省为当前版本），`replace=N` 时追加而不清空；第 6 列单价可空；**型号还没有版本时，导入的这个版本直接成为当前版本** |
| `mfgBomVersionCreate` / `mfgBomVersionActivate` / `mfgBomVersionList` | 新建下一版（可复制当前行）、启用某版（旧版转废止、型号指向新版）、列出各版本 |
| `mfgBomMigrateLegacy` | 把旧单表 BOM 冻结为版本（幂等，可重复调用） |
| `mfgAttachmentSave` / `mfgAttachmentList` / `mfgAttachmentDelete` | 任意单据的附件：存（base64，供脚本与测试用）、列表（不返回字节）、删除；页面上传走下面的 multipart 事件 |
| `mfgRoutingSave` | 新增/更新一道工序 |
| `mfgWorkOrderCreate` / `mfgWorkOrderList` / `mfgWorkOrderDetail` | 工单维护；详情含工序进度、SN、不良、累计工时 |
| `mfgSnRegister` / `mfgSnDetail` | 批量生成 SN；按 SN 查完整履历 |
| `mfgReportCreate` | 报工（工时 + 合格/不良数，可绑定 SN） |
| `mfgDefectCreate` | 登记不良与返修工时 |
| `mfgDashboard` | 看板数据 |
| `mfgLotSave` / `mfgLotList` | 物料批次台账（到货、已发、剩余）；新建批次默认 MFG_IQC_PENDING |
| `mfgLotInspect` | 记录来料检验结果（待检/合格/让步接收/不合格 + 不良数 + 检验人） |
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
| `mfgDevProjectSave` / `mfgDevProjectList` / `mfgDevProjectDetail` | 开发项目立项与查询（列表带阶段、关卡进度、未结变更数） |
| `mfgDevStageSave` | 登记关卡评审；通过/有条件通过时项目自动进入下一阶段，进到量产时填实际上市日并转为已量产 |
| `mfgDevStepSave` | 写入一个线路步骤的详情（状态、计划/完成日、负责人、供应商、金额、数量、单号、备注），空值即清空 |
| `mfgDevStepStatus` | 只改步骤状态（页面上的开始/完成按钮），首次完成时自动填完成日 |
| `mfgDevEcoSave` / `mfgDevEcoList` / `mfgDevEcoDetail` | 变更单登记与查询（详情带来源/目标 BOM 版本与可选版本列表） |
| `mfgDevEcoApprove` / `mfgDevEcoApply` / `mfgDevEcoCancel` | 批准、生效（可切换 BOM 版本）、取消；重复生效与已生效后取消都会被拒绝 |
| `mfgProductCost` | 型号成本：`qty` 台的材料 / 工时 / 合计，加上目标成本差、目标售价与毛利；带逐行金额与缺单价行数 |
| `mfgWorkOrderCost` | 工单成本：标准（BOM 单价 + 标准工时 × 费率）vs 实际（已发料按批次单价 + 报工工时 × 费率），给出差异与被排除的无单价笔数 |

示例（Web Tools → Run Service → `mfgBomImport`）：

```
productCode = B5X
csv         = R0402-10K,10K 0402 resistor,4,R1-R4,N,0.05
              C0402-100N,100nF 0402 capacitor,6,C1-C6,N,0.02
              U1-BT5-SOC,Main SoC,1,U1,Y,18.5
              BAT-18650,18650 cell,1,BT1,Y,12
```

## 页面

所有列表页（工单/产品/产品开发/设计变更/物料批次/齐套/出货/厂家/售后单/买家名单）都带 **关键词搜索 + 过滤 + 分页**：
默认每页 20 条，页脚显示“第 X / Y 页（共 N 条）”与首页/上一页/下一页/末页，翻页链接**保留当前搜索与过滤条件**。
分页与搜索都是内存内完成（数据量小），服务结果统一带 `totalCount` / `page` / `pageCount` / `pageSize` / `search`。

| 路径 | 说明 |
| --- | --- |
| `/mfg/control/main` | 工单列表 + 新建工单（含产品型号下拉） |
| `/mfg/control/products?productCode=...` | 产品型号、**照片上传与删改**、**BOM 版本（查看/新建下一版/启用）**、BOM 导入（带单价）与工艺路线维护、**成本（单台 / 批量）**——材料/工时/合计、目标成本对比与毛利，缺单价会提示 |
| `/mfg/control/attachmentUpload` | POST multipart，字段 `file` + `ownerTypeId` + `ownerId` + `back`（回跳页面）+ 可选 `note` |
| `/mfg/control/attachment?ownerTypeId=...&ownerId=...&seq=...` | 输出文件字节；`seq` 缺省取最新一件，`thumb=1` 对图片输出最长边 200px 的 JPEG 缩略图；PDF 直出 |
| `/mfg/control/attachmentDelete` | POST `ownerTypeId` + `ownerId` + `seq` + `back`，删除后回跳 |
| `/mfg/control/dev?projectCode=...` | 产品开发：立项与改动、线路与步骤进度、步骤台账与快速推进、五道关卡评审、项目下的变更单、**项目资料附件** |
| `/mfg/control/eco?ecoId=...` | 设计变更：新建变更单、按状态筛选、指定目标 BOM 版本、批准 / 生效 / 取消 |
| `/mfg/control/workOrder?workOrderId=...` | 工单详情：工序进度、报工、SN 生成、不良登记、**工单附件（首件/不良照片/外协快递单）**、**成本（标准 vs 实际，含差异）** |
| `/mfg/control/sn?sn=...` | 单板履历：报工、不良、所用物料批次 |
| `/mfg/control/dashboard` | 车间看板：生产卡片 + 售后 KPI + 每日柱图 + 责任方成本 + 最近报工，60 秒自动刷新 |
| `/mfg/control/materials` | 物料批次台账、收货登记、CSV 批量导入、按批次反向追溯 |
| `/mfg/control/shortage` | 齐套预警：按交期列出欠料工单与明细 |
| `/mfg/control/delivery` | 出货单：新建、列表、明细（**签收单 / 快递面单附件**）；出货后 SN 变为 `MFG_SHIPPED` |
| `/mfg/control/vendors` | 外协厂家：档案维护 + 可复制的邮寄标签（收件人/手机/地址/客户编号） |
| `/mfg/control/claims` | 售后单：登记（类型/订单号/买家/签收日）、按 SN/类型/状态过滤、7 天与 15 天窗口倒计时、处理（检测结论、退款、运费、索赔）、换货与开返修工单、**照片与检测报告附件** |
| `/mfg/control/buyers` | 买家名单：登记/解除、发货前查询命中、售后历史汇总与建议等级 |
| `/mfg/control/quality` | 质量指标：四率 + 成本率、钱花在哪、按型号/责任方/批次/失效时间/检测结论分解、红线告警 |

邮寄标签旁边有 **「复制标签」按钮**：脚本在静态文件 `webapp/mfg/js/copyLabel.js`，
页面只引 `<script src="/mfg/js/copyLabel.js"></script>`（`web.xml` 的 `allowedPaths` 里加了 `:/js`）。
优先用 `navigator.clipboard`（需要 HTTPS，本机 OFBiz 就是 HTTPS），失败时回退到 `document.execCommand('copy')`，
复制成功后按钮短暂显示「已复制」。

> 因为脚本是外部的，将来 OFBiz 把 `Content-Security-Policy` 从 `Report-Only` 改为强制模式也不需要改动（`default-src 'self'` 允许同源脚本）。

访问控制由 `ofbiz-component.xml` 的 `base-permission="OFBTOOLS"` 决定；
`data/MfgSecurityPermissionSeedData.xml` 定义 `MFG_VIEW` / `MFG_ADMIN` 并授予 `SUPER`、`FULLADMIN`。

## 页面布局：单一装饰器

页面外壳（`<html>`/`<head>`/样式表/顶部导航）只在一个地方定义，避免 14 个模板各写一遍：

- `widget/MfgCommonScreens.xml` 的 `mfg-decorator` 渲染 `webapp/mfg/decorator.ftl`；
- `widget/MfgScreens.xml` 的每个屏幕把自己的内容放进 `decorator-screen` 的 `body` 段：

```xml
<widgets>
    <decorator-screen name="mfg-decorator" location="component://mfg/widget/MfgCommonScreens.xml">
        <decorator-section name="body">
            ...原有内容...
        </decorator-section>
    </decorator-screen>
</widgets>
```

- 每个屏幕的 `actions` 里设两个上下文变量：`pageTitle`（页面标题）和 `activeMenu`（当前菜单键）；
- `decorator.ftl` 用 `<@navItem>` 宏输出导航，`activeMenu` 匹配的那一项加粗高亮，`<#if sections??>${sections.render("body")}</#if>` 输出页面内容；
- 样式集中在 `webapp/mfg/css/mfg.css`（`web.xml` 的 `allowedPaths` 加了 `:/css`），模板里不再有 `<style>` 块。

所以**加一个菜单项**或**改一次配色**都只改 `decorator.ftl` / `mfg.css` 两个文件，
模板只负责自己的表格和表单；模板里保留 `<h1>` 作为页面正文标题。

### 配色：跟随 VS Code Dark Modern

整天对着屏幕，白底确实刺眼，所以两个组件（本组件与学习曲线）统一用 VS Code 的深色配色：

| 用途 | 色值 | 用在哪 |
| --- | --- | --- |
| `--bg` | `#1f1f1f` | 页面底色（对应编辑器背景） |
| `--bg-bar` | `#181818` | 顶部导航条（对应标题栏/活动栏），`position: sticky` 常驻 |
| `--bg-widget` | `#252526` | 表头、卡片、宏区块（对应侧边栏/部件） |
| `--bg-input` | `#313131` | 输入框、下拉、文本域 |
| `--bg-hover` / `--bg-odd` | `#2a2d2e` / `#232323` | 行悬停、斑马纹 |
| `--fg` / `--fg-strong` / `--fg-muted` | `#cccccc` / `#e7e7e7` / `#9d9d9d` | 正文 / 标题 / 次要说明 |
| `--accent` | `#0078d4` | 主按钮、聚焦边框、当前菜单指示条 |
| `--link` | `#4daafc` | 链接 |
| `--ok` / `--warn` / `--error` | `#89d185` / `#cca700` / `#f14c4c` | 通过 / 注意 / 异常与红色提示条 |

细节：

- 所有颜色只出现在 `:root` 的变量里，换主题改这一段就够；`color-scheme: dark` 让原生控件（日期选择、下拉、滚动条）也跟着变深；
- **分隔线比 VS Code 原生的 `#2b2b2b` 亮一档**（`--border: #3a3a3a`）：页面底色是 `#1f1f1f`，照搬原生值几乎看不见；
  导航条下边缘、表格边框、分页脚、区块分隔线全用同一个值；
- **区块之间有一条分隔线**：`h2` / `h3` 自带 `border-top`（配 `padding-top`），长页面（产品与 BOM、售后单、开发项目）
  的各块不再糊成一片；模板里手写的 `style="font-size:1em;margin-top:1.2em"` 已删掉，交给样式表统一；
- 导航条是**一行**，放不下时横向滚动（`white-space: nowrap` + 细滚动条），不会换行成三排；
- **页面上只有一个蓝色按钮**：主操作（保存/创建/查询/上传）是 `#0078d4`，表格行内的小按钮（删除/启用/恢复）改成安静的深灰次要按钮；
- 表格斑马纹与悬停用背景差而不是描边，长时间看不容易累；
- 模板里原本写死的浅色（图片边框 `#ddd`、图表图例 `#b00000`、SVG 轴 `#999`）都已换成深色值；
  学习曲线趋势图的 SVG 文字默认是黑色，靠 `svg text { fill: var(--fg-muted) }` 才在深色底上可读；
- 窄屏（≤ 900px，车间平板）自动减小左右留白与表格字号。

> OFBiz 自带的登录页/错误页用的是框架主题（helveticus），仍为浅色——那属于 OFBiz 主题，不在本组件范围内。

### 网站图标与导航标志

- 源图 `F:\build-ofbiz\langhua-logo.png`（400×400，白底）。深色界面下白底很扎眼，所以生成时**把白色转成透明**
  （白色→透明的渐变，保留抗锯齿边缘），再按去白后的实际外框居中缩放；
- 四份尺寸，两个组件各存一套（`webapp/mfg/images/`、`webapp/learningcurve/images/`；
  `/images` 本来就在 `web.xml` 的 `allowedPaths` 里，不用改配置）：

| 文件 | 尺寸 | 用在哪 |
| --- | --- | --- |
| `logo-32.png` | 32 | 浏览器标签页图标（`<link rel="icon" sizes="32x32">`） |
| `logo-64.png` | 64 | 标签页高分屏 + 导航条左侧标志（`<img class="brand">`） |
| `logo-180.png` | 180 | `rel="apple-touch-icon"`（添加到桌面 / 书签） |
| `logo.png` | 512 | 备用大图，给以后做邮件抬头、导出 PDF 用 |

- `decorator.ftl` 的 `<head>` 里三行 `link`，`<body>` 里导航第一条 `<p class="nav">` 后紧跟
  `<img class="brand" src="/mfg/images/logo-64.png" alt="" width="26" height="26"/>`；
  CSS 用 `height: 1.7em` 跟着字号缩放（约 27px）、`vertical-align: -.42em` 视觉居中，`alt=""` 因为旁边就是菜单文字；
- 换 logo 只需跑一次：`powershell -ExecutionPolicy Bypass -File tools\make-icons.ps1 -Source <新图>`，
  脚本会同时写两个组件的 `images/` 目录。

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
           → bomVersionCreate（复制当前行）→ bomImport 到指定版本（追加）
           → bomVersionActivate → productDetail 用的是新版行 → bomMigrateLegacy 幂等
           → devProjectSave → devProjectList → devProjectDetail（五道关卡）
           → devStageSave（概念过关 → 自动进设计）→ 非法阶段被拒
           → devEcoSave（指定目标版本）→ 草稿不能直接生效
           → devEcoApprove → devEcoApply（切到目标 BOM）→ 重复生效被拒
           → PCB 项目：五步清单不含固件步骤 → 快速完成 → 步骤详情（负责人/供应商/单号）
           → 拿固件步骤去改 PCB 项目被拒
           → 外壳项目：入库不填数量被拒 → 填数量完成 → 自动生成物料批次（重复完成不重建）
           → 产品附件：base64 存入 → 列表带 contentType → 文件名去路径 → 拒绝 zip → 拒绝未知 owner 类型
           → 售后单挂 PDF（同一个服务、另一个 owner）
           → 删除后列表为空
           → 非法报工（workHours = 0）/ 错料发料（批次不属于该料号）/ 非法售后状态
           → 成本：带单价的 BOM + 工时费率 → productCost（材料 3.32 / 工时 20.00 / 合计 23.32，10 台 233.20）
           → 目标成本差 3.32、毛利 6.68 → 工单标准 23.32 / 总额 116.60 → 发 2 个 0.5 元的批次 → 实际材料 1.00
```

共 232 项断言，全部通过时退出码为 0。文件的字节输出（含缩略图）用页面脚本验证（见下方“页面巡检”），
因为它需要一个真正的浏览器会话。测试是**可重复执行**的：每次创建/更新产品型号 `ITMFG`、替换其 BOM、
新建一张工单、物料批次与出货单，以及相应的报工、不良、发料、收发、换件与测试数据；
BOM 版本与变更单会每次新增一版，所以编号会一直往上走，不影响断言。

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
以及买家匹配键、手机号归一化与打码、风险等级建议、名单到期判定，还有百分比、日期差与失效分档、售后成本计算、红线判定、看板坐标轴与柱高缩放、关键词过滤与分页切片，
以及 BOM 版本号生成与下一版号推导、开发阶段阶梯与关卡推进规则、变更单状态与类型校验、三条线路的步骤目录与首个未完成步骤推导、附件类型白名单（含 PDF）与 owner 类型白名单、文件名清洗、成本合计与目标差 / 毛利换算（含两位小数取整、目标售价为空时的处理）（共 58 个用例）。

## 页面巡检（PowerShell）

服务层的断言由集成测试覆盖，**页面渲染与二进制输出**用一组 PowerShell 脚本配真实登录会话来查
（`verify-m*.ps1`，日志写在 `F:\build-ofbiz\mfg-m*.txt`）：

| 脚本 | 覆盖 | 断言数 |
| --- | --- | --- |
| `verify-m11.ps1` | 8 个列表页的关键词搜索、过滤、翻页、编码、全站无模板报错 | 55 |
| `verify-m12.ps1` | 12 个页面的布局：一个 `<html>`、一条导航、一个高亮项、样式表、无内联 `<style>`、无 FTL 报错 | 102 |
| `verify-m13.ps1` | 产品开发的线路与步骤、变更单流程、BOM 版本块 | 49 |
| `verify-m28.ps1` | 通用附件：页面上的宏、multipart 上传 → 列表 → 原图（`image/png`）→ 缩略图（`image/jpeg`）→ 下载文件名 → `err=type` / `err=nofile` / `err=owner` → 删除 → 404 → PDF 直出 → 开发项目 / 工单 / 售后单 / 出货单四处挂附件 | 59 |
| `verify-m29.ps1` | 网站图标与导航标志：favicon、brand 图、四个尺寸、全站渲染 | 42 |
| `verify-m31.ps1` | 来料检验：四个状态、建批次 → 判不合格 → 发料被拒 → 让步接收 → 放行 | 21 |
| `verify-m32.ps1` | 成本核算：产品页成本块（材料/工时/合计/目标成本/毛利）、批量重算、BOM 单价与金额列、无单价时告警、工单成本块（标准 vs 实际、发料后实际材料跟着批次单价走） | 38 |

> 这些脚本在断言页面内容，不只是看 HTTP 200：OFBiz 的模板错误也是 200 + 一页堆栈，
> 所以每个页面都要扫 `FTL stack trace` / `FreeMarker template error`。

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

## 产品开发与 BOM 版本：规则说明

**附件（通用，代替原来的产品照片）**

- 附件存在数据库（`MfgAttachment.fileData` 是 `byte-array` → BLOB），**不写到 webapp 目录**，
  所以重新部署组件、清 `build` 都不会丢；备份跟数据库一起走；
- 一张表服务所有单据：`ownerTypeId` 说"挂在哪类记录上"（`MFG_ATT_PRODUCT` 产品 / `MFG_ATT_DEV_PROJECT` 开发项目 /
  `MFG_ATT_WORK_ORDER` 工单 / `MFG_ATT_DELIVERY` 出货单 / `MFG_ATT_CLAIM` 售后单），`ownerId` 是那条记录的主键，
  `seq` 在同一 `ownerTypeId + ownerId` 下从 1 递增。以后要给供应商、批次挂文件，只加一个 owner 类型常量；
- 页面只用一个宏：`webapp/mfg/attachmentBlock.ftl` 的 `<@attachmentBlock ownerTypeId ownerId back title attachments/>`，
  产品页、开发项目页、工单详情页、出货页、售后页各自 `<#include "attachmentBlock.ftl">` 后调用一次；
  列表数据来自 `mfgAttachmentList`（widget 的 result-map，如 `imageResult` / `attachmentResult`）；
- 页面上传表单是 `enctype="multipart/form-data"`，字段名 `file`，外加隐藏的 `ownerTypeId` / `ownerId` / `back`，
  由 `MfgAttachmentEvents.upload` 处理：OFBiz 的 `UtilHttp.getParameterMap` 已经把 multipart 解析好
  （文件字段是 `ByteBuffer`，另有 `_file_fileName` / `_file_contentType` / `_file_size` 三个伴生参数）；
- 校验：只收 JPG / PNG / GIF / PDF（浏览器给了 `application/octet-stream` 时按扩展名猜），图片 ≤ 4 MB、PDF ≤ 8 MB，
  文件名只保留最后一段并把怪字符换成 `_`；出错时不报 500，而是 `302` 回原页面并带 `?err=` 码
  （`type` 类型不支持 / `size` 超过大小 / `nofile` 没选文件 / `owner` 记录还不存在），宏里再翻成人话；
- 序号只增不减：删掉中间某件不会重排，最新一件 = 序号最大的一件；
- 查看/下载走 `/mfg/control/attachment`，**经过 controller 的 `auth="true"`**，不是公开目录；
  图片带 `thumb=1` 时用 `ImageIO` 现场缩放成 JPEG（最长边 200px），并带 `Cache-Control: private, max-age=86400` 与 ETag，
  列表里 20 行缩略图不会反复传原图；PDF 不缩放（`thumb=1` 直接返回原文），并带 `Content-Disposition: inline; filename=...`；
- 旧表 `MfgProductImage` 已停用：第一次读产品时把旧照片复制进 `MfgAttachment`（`ownerTypeId=MFG_ATT_PRODUCT`），
  幂等；旧的 `/mfg/control/productImage*` 地址保留为兼容别名，指向同一套事件。

**BOM 版本**

- 版本号从 1 开始，版本主键是 `型号-Vn`（如 `BT5-V2`），一个型号可以有多个版本；
- 新建版本默认**复制当前版本的行**，再在页面上改（改完可以一直保持草稿，不影响生产）；
- 「启用此版本」会把该版本设为 `MFG_BOM_RELEASED` 并把型号的 `currentBomVersionId` 指过去，
  同一型号之前处于 `RELEASED` 的版本自动转 `MFG_BOM_OBSOLETE`（历史版本仍可查、可再启用）；
- 工单创建时把当时的 `currentBomVersionId` 冻结到 `MfgWorkOrder.bomVersionId`，
  **齐套与发料按工单的版本算**；老工单不受新版本影响；
- 旧的单表 `MfgBomItem` 已停用：第一次读某个型号的 BOM 时，把旧行原样冻结成 `型号-V1` 并启用，
  之后代码只读版本表（`mfgBomMigrateLegacy` 可手动触发，幂等）。

**开发项目与阶段**

- 阶段阶梯固定五级：`概念 → 设计 → 打样 → 试产 → 量产`；
- 项目与型号**解耦**：立项时可以先不选型号（还只是个想法），量产前再关联；
- 型号自身也有 `devStageId`，用于列表上快速看出“这型号处在哪个阶段”，与项目的阶段互不覆盖；
- 关卡评审结论为 通过 / 有条件通过 时，项目自动进入下一阶段；进到 量产 时自动填实际上市日并置为已量产；
  评审结论为 不通过 时项目停在原阶段。

**三条开发线（线路 + 步骤）**

阶段是项目级的粗颗粒，线路步骤是执行层的细颗粒，两者独立：一个「外壳开发」项目同样要过一次「打样」关卡。

| 线路 | 步骤清单（有序，由代码里的目录决定） |
| --- | --- |
| PCB 开发 `MFG_TRACK_PCB` | 面包板验证 → 原理图 → PCB 布线 → 样板打样 → 手焊测试 |
| 固件开发 `MFG_TRACK_FIRMWARE` | 固件开发 → 烧录 → 功能测试 |
| 外壳开发 `MFG_TRACK_ENCLOSURE` | Gerber/PNP 导入 Blender 设计 → 3D 打印与装配确认 → 注塑厂家签合同 → 付款 → 生产外壳 → 外壳入库 → 产品组装 |
| （空） | 不带步骤，只用五级阶段管理 |

- 步骤目录写在代码里（`MfgServices.PCB_STEPS` / `FIRMWARE_STEPS` / `ENCLOSURE_STEPS`），表 `MfgDevTrackStep` 只存“走到哪了”，
  因此改流程只改代码、不用改数据；步骤 id 与线路不匹配会被直接拒绝；
- 每个步骤可记：状态（未开始/进行中/已完成/跳过）、计划日、完成日、负责人、供应商、金额、数量、单号（合同号/付款凭证/快递单）、备注；
- 页面每行有「开始 / 完成」快速按钮（只改状态，不动其它字段），细节用下面的「更新步骤详情」表单；
- 列表上直接显示 `步骤 3 / 5` 与**当前步骤**，哪条线卡在哪一步一眼能看出来；
- **外壳入库 → 物料批次**：把「外壳入库」置为已完成时，用项目的 `partCode` 和该步骤填的 `qty` 自动生成一个
  `MfgMaterialLot`（供应商、单号也带过去），批次号回填到步骤上，重复完成不会重复建批次；
  之后组装仍按正常流程人工开生产工单（`/mfg/control/main`），批次的领用与前面对 BOM/批次追溯完全一致。

**设计变更（ECO）**

- 状态流转只有一条路：`草稿 → 批准 → 生效`，`草稿`/`批准` 可以取消，`生效` 之后不能改也不能取消；
- 生效时如果指定了目标 BOM 版本，会做一次“启用该版本”的动作（旧版本转废止），
  变更单与版本双向可查：版本上有 `ecoId`，变更单上有 `fromBomVersionId → toBomVersionId`；
- 没指定目标版本的变更（比如工艺、外观）只记录说明与生效日，不动 BOM。

## 成本核算：标准成本与实际成本

小厂最想知道的就两句话：**这一台该花多少钱**、**这批实际花了多少，差在哪**。所以成本只算两项，
数据全部来自已经在用的表，不另建成本台账：

| 层 | 公式 | 数据来源 |
| --- | --- | --- |
| 材料（单台） | Σ BOM 行 `用量 × 单价` | `MfgBomVersionItem.unitPrice`（BOM 导入第 6 列或手工填） |
| 工时（单台） | `standardHours × laborRate` | `MfgProduct.standardHours` 与 `MfgProduct.laborRate` |
| 标准合计 | 材料 + 工时，乘批量得总额 | — |
| 目标对比 | `单台成本 − 开发项目.targetCost`；毛利 = `targetPrice − 单台成本` | 最新一个 `MfgDevProject`（同型号可能有好几个项目，取 `createdStamp` 最新的） |
| 实际材料 | Σ 发料 `数量 × 批次单价` | `MfgMaterialIssue` + `MfgMaterialLot.unitPrice` |
| 实际工时 | Σ 报工 `workHours × laborRate` | `MfgReport` |
| 差异 | `实际合计 − 标准单台成本 × 良品数` | — |

规则：

- **没填单价就说没填，不当成 0**：`unitPrice` 可空，缺价的 BOM 行 / 批次不计入金额，另给出
  `missingPriceCount` 与 `issuesWithoutPrice`，页面上黄条提示，避免“成本偏低却看不出来”；
- 数值统一两位小数（`round2`），乘法先算后舍入，避免逐行舍入误差累积；
- `targetPrice` 为空或 ≤ 0 时不算毛利（返回 `null`，页面显示「—」），不出现除零；
- 成本表在**产品页**（单台 / 批量，可改数量重算）与**工单详情页**（标准 vs 实际），
  两张表分别带 `id="productCost"` / `id="workOrderCost"`，页面巡检按 id 切片断言，不依赖中文；
- 服务 `mfgProductCost` / `mfgWorkOrderCost` 都是只读的（`use-transaction="false"`），
  也可以在 Web Tools → Run Service 里直接查。

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
- **装饰器（decorator）**：`<decorator-screen>` 放在 `widgets` 里，页面内容必须写在
  `<decorator-section name="body">` 中，装饰器模板用 `sections.render("body")` 输出；
  装饰器里读得到屏幕 `actions` 设的上下文变量（`pageTitle` / `activeMenu`），
  所以“当前菜单高亮”不用从 URL 猜。
- **静态资源放行**：`webapp/mfg/WEB-INF/web.xml` 的 `ControlFilter` 只放行 `allowedPaths` 里的前缀，
  新增 `css/` 目录就要加 `:/css`，否则样式表 404。
- **FreeMarker 的 `#if` 优先级**：`(x)?default(1) gt 1` 会被解析成 `(x)?default(1 gt 1)`，必须写成
  `((x)!0) gt 0` 或 `#if (x?default(1)) gt 1`；包含 `?url` 的模板要有
  `<#setting url_escaping_charset="UTF-8">`，否则分页链接直接报错。
- **可选入参**：屏幕里会**无条件**调用的服务（如“选中某个项目才显示详情”），IN 参数必须
  `optional="true"`——否则不带 `?projectCode=` 访问列表页时，服务参数校验抛
  `ServiceValidationException`，整页变 500。服务实现里对空值直接返回空结果即可。
- **文件上传**：`enctype="multipart/form-data"` 的表单，Java 事件里 `request.getParameter` 取不到值，
  要先 `UtilHttp.getParameterMap(request)`（或读 `request.getAttribute("multiPartMap")`）；
  文件字段是 `ByteBuffer`，名字/类型/大小在 `_字段名_fileName` / `_contentType` / `_size`。
  输出二进制用 `<response name="success" type="none"/>`，否则控制器会再渲一页盖在后面。
- **宏的位置参数与括号**：`<@m 'A' x.y ('p=' + x.y)/>` 会报
  `Expected a method or function, but this has evaluated to a string+extended_hash`——
  FreeMarker 把 `x.y (...)` 当成“以括号为参数的方法调用”。widget 传下来的字段是
  `StringHtmlWrapperForFtl`（字符串包装），正好踩这个坑；先 `<#assign back = ...>` 再传参即可。
- **删除源码文件要同步删部署目录**：`deploy` 脚本用 `robocopy` 覆盖拷贝，**不会删除**插件目录里
  已经删掉的 java 文件，残留的旧类会让 `gradle jar` 编译失败，手工删掉
  `plugins/mfg/src/main/java/...` 下同名文件即可。

## 后续想法

- 每个步骤挂问题清单（现在只在步骤上有备注）：打样/试产发现的问题单独立项、指派与关闭。
- 成本再细化：按工序分摊工时（现在用型号上的标准工时合计）、外协加工费单列。
- 淘宝订单号自动对单（开放平台/导出表导入），省掉手录订单号。
- 看板自动刷新与钉钉/邮件告警（欠料、测试通过率异常、售后即将超时）。
- 报表导出：良率趋势、工时趋势（可回接学习曲线组件）。
