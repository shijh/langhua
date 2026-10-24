-- Adds the whole order discount (Taobao 红包 / coupon / 满减) to the purchase receipt.
-- 实付款 = goods + freight - discount, so an order paid with a red packet no longer looks wrong.
--
-- Stop the instance first (an embedded Derby only allows one connection), then:
--   java -cp "derby.jar;derbyshared.jar;derbytools.jar" org.apache.derby.tools.ij receipt-discount.sql
-- The jars are under ~/.gradle/caches/modules-2/files-2.1/org.apache.derby/*/10.16.1.1/
-- The connect line must carry user=ofbiz: the tables live in the OFBIZ schema, ij defaults to APP
-- ("ALTER TABLE cannot be performed ... because it does not exist" means the schema is wrong).

connect 'jdbc:derby://localhost:1527/ofbiz;create=false;user=ofbiz;password=ofbiz';

ALTER TABLE OFBIZ.MFG_PURCHASE_RECEIPT
    ADD COLUMN DISCOUNT_AMOUNT NUMERIC(18,2) DEFAULT 0 NOT NULL;

UPDATE OFBIZ.MFG_PURCHASE_RECEIPT SET DISCOUNT_AMOUNT = 0 WHERE DISCOUNT_AMOUNT IS NULL;

disconnect;
exit;
