-- Step 66: 料号列从 20 字符加宽到 100 字符（id 类型 → name 类型）。
--
-- 新库不用跑：实体定义已经改过，OFBiz 建表时直接是 VARCHAR(100)。
-- 已有库要跑一次，且必须**先停实例**（Derby 是嵌入式的，实例占着锁）。
--
-- 用法（把第一行的库路径换成自己的实例）：
--   1) 停：stop-ofbiz.bat（开发 8443）或 stop-prod.bat（生产 8453）
--   2) 跑：java -cp "<derby.jar>;<derbyshared.jar>;<derbytools.jar>" org.apache.derby.tools.ij part-code-100.sql
--      三个 jar 在 Gradle 缓存里：~/.gradle/caches/modules-2/files-2.1/org.apache.derby/*/10.16.1.1/*.jar
--   3) 校验最后那条 select：8 行都应是 VARCHAR(100)
--   4) 起实例
--
-- 注意 `user=ofbiz`：表在 OFBIZ schema 下，ij 默认连到 APP，不加就报
-- "ALTER TABLE cannot be performed on 'MFG_...' because it does not exist"（实际是 schema 不对）。

connect 'jdbc:derby:F:/build-ofbiz/apache-ofbiz-24.09.07/runtime/data/derby/ofbiz;user=ofbiz';

ALTER TABLE MFG_BOM_ITEM ALTER COLUMN COMPONENT_CODE SET DATA TYPE VARCHAR(100);
ALTER TABLE MFG_BOM_VERSION_ITEM ALTER COLUMN COMPONENT_CODE SET DATA TYPE VARCHAR(100);
ALTER TABLE MFG_MATERIAL_LOT ALTER COLUMN COMPONENT_CODE SET DATA TYPE VARCHAR(100);
ALTER TABLE MFG_MATERIAL_ISSUE ALTER COLUMN COMPONENT_CODE SET DATA TYPE VARCHAR(100);
ALTER TABLE MFG_REPLACEMENT ALTER COLUMN COMPONENT_CODE SET DATA TYPE VARCHAR(100);
ALTER TABLE MFG_PURCHASE_RECEIPT_LINE ALTER COLUMN COMPONENT_CODE SET DATA TYPE VARCHAR(100);
ALTER TABLE MFG_STOCK_SETTING ALTER COLUMN COMPONENT_CODE SET DATA TYPE VARCHAR(100);
-- 旧表，实体模型里已经不用了，存在就一起加宽
ALTER TABLE MFG_MATERIAL_TRACE ALTER COLUMN COMPONENT_CODE SET DATA TYPE VARCHAR(100);

select t.tablename || ' | ' || c.columndatatype as detail
  from sys.systables t join sys.syscolumns c on c.referenceid = t.tableid
 where t.tablename like 'MFG%' and c.columnname = 'COMPONENT_CODE' order by t.tablename;

exit;
