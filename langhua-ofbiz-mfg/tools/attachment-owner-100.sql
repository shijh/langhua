-- Step 206: the owner key of an attachment has to hold a part number.
--
-- MfgAttachment.ownerId was `id` (VARCHAR(20)), because every owner so far was a model, a project,
-- a work order, a delivery or a claim, and their keys are 20 characters. A part number is up to 100
-- characters (tools/part-code-100.sql widened the componentCode columns for the same reason), so the
-- key is widened to a `name` and the model in entitydef/entitymodel.xml follows.
--
-- Run it with the instance STOPPED (its embedded Derby holds the only lock on the store):
--   java -cp "derby.jar;derbyshared.jar;derbytools.jar" org.apache.derby.tools.ij attachment-owner-100.sql
--
-- The connect must carry user=ofbiz: the tables live in the OFBIZ schema, and ij connects to APP by
-- default, which then reports "ALTER TABLE cannot be performed on 'MFG_ATTACHMENT' because it does
-- not exist". Change the path for the instance you are preparing (development 24.09.07 / ofbiz-test /
-- ofbiz-prod each have their own store).
connect 'jdbc:derby:F:\build-ofbiz\ofbiz-prod\runtime\data\derby\ofbiz;user=ofbiz';
ALTER TABLE OFBIZ.MFG_ATTACHMENT ALTER COLUMN OWNER_ID SET DATA TYPE VARCHAR(100);
SELECT COUNT(*) AS ATTACHMENTS FROM OFBIZ.MFG_ATTACHMENT;
exit;
