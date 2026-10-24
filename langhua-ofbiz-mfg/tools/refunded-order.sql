-- Step 85: the table behind the refunded / closed order list.
--
-- New databases get the table from loadAll; an existing database needs this file once.
--   1. stop the instance (its embedded Derby locks the files)
--   2. java -cp "derby.jar;derbyshared.jar;derbytools.jar" org.apache.derby.tools.ij refunded-order.sql
--   3. start the instance again
-- The jars sit in ~/.gradle/caches/modules-2/files-2.1/org.apache.derby/*/10.16.1.1/
-- and the connect string MUST carry user=ofbiz: the tables live in the OFBIZ schema,
-- ij else connects as APP and answers "table does not exist".

connect 'jdbc:derby:F:\build-ofbiz\apache-ofbiz-24.09.07\runtime\data\derby\ofbiz;user=ofbiz';

CREATE TABLE OFBIZ.MFG_REFUNDED_ORDER (
    ORDER_NO      VARCHAR(100) NOT NULL,
    SHOP          VARCHAR(100),
    NOTE          VARCHAR(255),
    RECORDED_DATE TIMESTAMP,
    CREATED_STAMP TIMESTAMP,
    CONSTRAINT MFG_REFUNDED_ORDER_PK PRIMARY KEY (ORDER_NO)
);

exit;
