-- Adds the reason of an inventory movement (Step 96).
--
-- The reason comes from OFBiz's own list, the enumeration type IID_REASON (Inventory Item Detail
-- Reason): VAR_LOST / VAR_STOLEN / VAR_FOUND / VAR_DAMAGED / VAR_SAMPLE / VAR_INTEGR /
-- VAR_MISSHIP_ORDERED / VAR_MISSHIP_SHIPPED. It answers "why did the quantity change", which the
-- movement type (报废 / 退料 / 补入库 / 调整) alone cannot.
--
-- How to run (the instance must be stopped, otherwise the database is booted by the server):
--   java -cp "derby.jar;derbyshared.jar;derbytools.jar" org.apache.derby.tools.ij inventory-reason.sql
-- The jars are under
--   %USERPROFILE%\.gradle\caches\modules-2\files-2.1\org.apache.derby\<artifact>\10.16.1.1\
-- The connect must carry user=ofbiz, the tables live in the OFBIZ schema:
--   connect 'jdbc:derby:<instance>\runtime\data\derby\ofbiz;user=ofbiz';
-- New databases need nothing: the column is declared in entitydef/entitymodel.xml.

connect 'jdbc:derby:${instance}\runtime\data\derby\ofbiz;user=ofbiz';

ALTER TABLE OFBIZ.MFG_INVENTORY_DETAIL ADD COLUMN REASON_ENUM_ID VARCHAR(20);

CREATE INDEX MFG_INVDET_REASON ON OFBIZ.MFG_INVENTORY_DETAIL (REASON_ENUM_ID);

select count(*) as movements from OFBIZ.MFG_INVENTORY_DETAIL;
