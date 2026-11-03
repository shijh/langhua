-- The gate result column has to hold MFG_REVIEW_CONDITIONAL (22 characters), and an OFBiz "id"
-- column is VARCHAR(20): signing a gate off with "conditional pass" failed with
-- "A truncation error was encountered trying to shrink VARCHAR 'MFG_REVIEW_CONDITIONAL' to length 20".
-- Run it with the instance stopped, then start the instance again:
--   java -cp "derby.jar;derbyshared.jar;derbytools.jar" org.apache.derby.tools.ij product-stage-review-result.sql
-- (the tables live in the OFBIZ schema, so connect with user=ofbiz)
connect 'jdbc:derby:F:\build-ofbiz\apache-ofbiz-24.09.07\runtime\data\derby\ofbiz;user=ofbiz';
ALTER TABLE OFBIZ.MFG_PRODUCT_STAGE_REVIEW ALTER COLUMN RESULT_ID SET DATA TYPE VARCHAR(100);
-- the old project level table is no longer written, but the same constant still fits there
ALTER TABLE OFBIZ.MFG_DEV_STAGE_REVIEW ALTER COLUMN RESULT_ID SET DATA TYPE VARCHAR(100);
SELECT COUNT(*) AS PRODUCT_GATES FROM OFBIZ.MFG_PRODUCT_STAGE_REVIEW;
exit;
