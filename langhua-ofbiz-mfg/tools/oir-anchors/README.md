# oir-anchors —— 量“橙色按钮检测”对不对

`oir` 的切点靠“订单块底部的橙色按钮”（`OirScan.blockAnchors`），所以**先确认按钮数对不对**，
再谈切得准不准。这两个小程序只看图，不联网、不问模型：

| 文件 | 作用 |
| --- | --- |
| `AnchorAudit.java` | 反射调**实例里的** `OirScan`：打印块高、段高、锚点数、切点、每段完整块数 |
| `LongestOrange.java` | 打印每一条“长段连续橙色”的 y / 高度 / 带宽 / 起始 x（不需要实例的类） |
| `JpegEye.java` | 同一条带子裁两遍：原图 PNG 与“过一遍 q0.9 JPEG”之后，并排看字有没有糊 |
| `Digits.java` | 量一条带子的墨水盒子（字高、19 位单号每位几像素），也能证明某块区域是空白 |
| `run.bat` | 先编再连着跑前两个（`run.bat "D:\path\shot.png"`）；后两个按需手敲请参看文件头的注释 |
| `run-fifty.bat` | 同一张图连跑 N 次，可顺手落成基线：`run-fifty.bat "shot.png" 50 F:\build-ofbiz\runs t12 "Step 158 build"` |
| `make-baseline.ps1` | 把 `run-N.json` 收成 `baseline/batch-<id>.json`（`-From <目录> -Id <批次号> [-Note "哪版代码"]`） |
| `baseline/` | 四批 50 次连测的**原始回答**（每批一个 JSON），见下文 |
| `analyze-baseline.ps1` | 读 `baseline/` 打印每批的准确率与各类提示的数量（`powershell -File analyze-baseline.ps1`） |

## 基线：四批 50 连测（2026-10-27，同一张 23 单整页截图）

```
Batch Runs Right Percent Orders ExtraRow Hint Copy Twice RowsOff Money
----- ---- ----- ------- ------ -------- ---- ---- ----- ------- -----
t8     50    44      88     23        3    3   50     0      19    18
t9     50    47      94     23        3    3   50     1      25     8
t10    50    48      96     23        2    2   50     3      18    14
t11    50    49      98     23        1    1    1     0      14    23
```

- `Right` = 整页的 23 个单号全对且无多余行的次数；`ExtraRow` = 多出一张单的次数；
  `Hint` = 那几次页面上有没有点出“图上有 23 个「再买一单」”；
  `Copy/Twice/RowsOff/Money` = 四类 note 各出现了多少次。
- 四批的代码差别：
  - **t8**：“单号读两遍、放大那次为准”——错的 3 次里，整页读对、带子读错，却被改错（88%）；
  - **t9**：改成“按位数判定”（94%）；
  - **t10**：与 t9 同一份代码（当时 dev 实例还没跟上最近一次改动，96%）；
  - **t11**：“一模一样”提示只在多出一行时才说（98%，误报从 50 次 50 条降到 1 条）。
- **150 次里只有一种错**：多出一张带“页面上没有的单号”的行（每 50 次 1~3 次），每一次都被
  按钮数提示抓到（`Hint` 列与 `ExtraRow` 列完全对上）。
- 换算法后要对照：改完先 `run-fifty.bat` 跑 50 次，再用 `analyze-baseline.ps1` 的口径算一遍。
  **跑之前先核 `ofbiz.jar` 的时间戳早不早于源码**（t10 就是旧 jar 跑的，差点当结论）。

一条命令走完一轮对照：

```bat
rem 1) 确认实例的 jar 不比源码旧
dir /T:W F:\build-ofbiz\apache-ofbiz-24.09.07\build\libs\ofbiz.jar
dir /T:W F:\git\langhua\langhua-ofbiz-mfg\src\main\java\org\langhua\ofbiz\mfg\oir\OirScan.java

rem 2) 跑 50 次并落成批次 t12
tools\oir-anchors\run-fifty.bat "D:\Downloads\shot.png" 50 F:\build-ofbiz\runs t12 "Step 158 build"

rem 3) 与历史批次对照
powershell -File tools\oir-anchors\analyze-baseline.ps1
```

### 基线文件里有什么

`baseline/batch-t8.json` 等四个文件，每个是：

```json
{"batch":"t11","note":"哪一版代码","screenshot":"…3434x16384，23 单","runs":[{"run":1,"response":{…服务端原样返回…}}]}
```

`response` 就是 `/mfg/control/orderImage` 的原话（包含 message、count、orders[] 与每单的 note），
**没有改过一个字**；每题约 430 KB，四个批次合计约 1.7 MB。

## 实测（2026-10-27，淘宝“我的订单”整页 3434×16384，23 单）

```
AnchorAudit   blockHeight 689  sliceTarget 1378  anchors 25  slices 12
LongestOrange 23 个块按钮：x=2431~2448，带宽 187~204 px
              页头按钮 y≈118  x≈2533  带宽 153
              页尾推荐 y≈13276 x≈1666 带宽 204
```

- 旧规则（一行里橙色**总量** ≥ 宽度的 4%）→ **32** 个锚点：23 个真按钮 + **9 行橙色文字**
  （`订单详情 >` / `交易成功` / 券条，一行就能凑出 200 px）。
- 新规则（一行里**最长一段连续**橙色 ≥ 宽度的 4%）→ **25** 个：23 个块按钮 + 页头 + 页尾推荐。
  换成新规则后同一张图 10 连测仍是 **10/10 都是 23 张、23 个不重复单号**。
- 想用它**数块**（从而发现“模型凭空多编的一张”），还差一步：去掉页头/页尾那两个
  （它们的起始 x 与 23 个块按钮差 60 px 以上）。见 `src/main/java/org/langhua/ofbiz/mfg/oir/README.md` §6 问题 1。

## 注意

- `run.bat` 里的 `OFBIZ` 指向开发实例；换实例改那一行即可（只要 `build/libs/ofbiz.jar` 里有插件类）。
- 这两个文件**不在构建里**（`tools/` 下不会被 `gradle jar` 编译），编出来的 `.class` 也别提交。

## 顺带量过的两件事（2026-10-27）

- **q0.9 的 JPEG 没有把数字弄糊**：同一条带子（`1500,560 1100×200` 与 `590,540 700×64`）在
  `JpegEye` 的两张图里笔画一样完整，最暗像素 JPEG 反而更深（12 / 13 vs PNG 31 / 20）。
  整图 14.9 MB → JPEG **5.8 MB**。
- **单号只有 16.4 px 宽 / 21 px 高**（`Digits` 量出来的墨水盒子：312 px 装 19 位）。
  接口若把 3434 宽压到 1568（×0.46），每位只剩 **7.5 px**、字高 **9.6 px**——长串数字出错多半在这里，
  不在压缩算法上。
- **左侧 60 px、右侧约 814 px 是空白**（`Digits 2700 400 734 11000` 只量到一条 65 px 宽的状态条，
  `Digits 0 400 60 11000` 一个墨点都没有）：裁掉这两条边，有效像素能多 25% 左右。
