-- Step 305: a development project's 说明 has to hold a real description, not 255 characters.
--
-- MfgDevProject.note was a `description` (VARCHAR(255)); the same widening the software project note
-- got in Step 304 (tools/software-note-2000.sql). A `very-long` field is a CLOB, and Derby refuses to
-- change a VARCHAR column into a CLOB ("ERROR 42Z15: Invalid type specified for column ..."), so the
-- column is widened and the model names the stock 2000 character text type (`type="url"`). The save
-- service refuses anything longer than MfgConstants.MAX_NOTE_CHARS (2000).
--
-- Run it with the instance STOPPED (its embedded Derby holds the only lock on the store):
--   java -cp "derby.jar;derbyshared.jar;derbytools.jar" org.apache.derby.tools.ij dev-project-note-2000.sql
--
-- The connect must carry user=ofbiz: the tables live in the OFBIZ schema. Change the path for the
-- instance you are preparing (development 24.09.07 / ofbiz-test / ofbiz-prod each have their own store).
connect 'jdbc:derby:F:\build-ofbiz\ofbiz-prod\runtime\data\derby\ofbiz;user=ofbiz';
ALTER TABLE OFBIZ.MFG_DEV_PROJECT ALTER COLUMN NOTE SET DATA TYPE VARCHAR(2000);
SELECT COUNT(*) AS DEV_PROJECTS FROM OFBIZ.MFG_DEV_PROJECT;
exit;
