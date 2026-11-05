-- Step 304: a software project's note has to hold a real description, not 255 characters.
--
-- MfgSoftwareProject.note was a `description` (VARCHAR(255)). A project description - what the thing
-- is for, what it talks to, links to the repository - runs to a few hundred characters, and saving one
-- answered with "A truncation error was encountered trying to shrink VARCHAR ... to length 255".
--
-- The obvious OFBiz answer is a `very-long` field, which is a CLOB, but Derby refuses to change a
-- VARCHAR column into a CLOB ("ERROR 42Z15: Invalid type specified for column ... The type of a column
-- may not be changed"). So the column is widened instead: `type="url"` in entitydef/entitymodel.xml,
-- which is the stock 2000 character text type, and the service refuses anything longer than
-- MfgConstants.MAX_NOTE_CHARS (2000).
--
-- Run it with the instance STOPPED (its embedded Derby holds the only lock on the store):
--   java -cp "derby.jar;derbyshared.jar;derbytools.jar" org.apache.derby.tools.ij software-note-2000.sql
--
-- The connect must carry user=ofbiz: the tables live in the OFBIZ schema. Change the path for the
-- instance you are preparing (development 24.09.07 / ofbiz-test / ofbiz-prod each have their own store).
connect 'jdbc:derby:F:\build-ofbiz\ofbiz-prod\runtime\data\derby\ofbiz;user=ofbiz';
ALTER TABLE OFBIZ.MFG_SOFTWARE_PROJECT ALTER COLUMN NOTE SET DATA TYPE VARCHAR(2000);
SELECT COUNT(*) AS SOFTWARE_PROJECTS FROM OFBIZ.MFG_SOFTWARE_PROJECT;
exit;
