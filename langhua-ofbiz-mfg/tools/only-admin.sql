-- Step 126: leave exactly one account that may log in (admin).
--
-- The demo data of OFBiz brings more than fifty logins (bizadmin, demoadmin, blog_admin,
-- imageAdmin, accountingadmin, bigal ...) and most of them are ENABLED='Y': a production database
-- with them is a row of doors nobody asked for. They cannot be deleted - applications/content and
-- applications/workeffort point at USER_LOGIN with foreign keys (Derby answers
-- "CONTENT_CB_ULGN" and rolls the whole delete back), and those applications are part of the
-- install - so they are switched off instead. The internal "system" login stays as well: it is the
-- account the framework itself uses and it is already disabled.
--
--   1. stop the instance (its embedded Derby locks the files)
--   2. java -cp "derby.jar;derbyshared.jar;derbytools.jar" org.apache.derby.tools.ij only-admin.sql
--   3. start the instance again
-- The jars sit in ~/.gradle/caches/modules-2/files-2.1/org.apache.derby/*/10.16.1.1/
-- and the connect string MUST carry user=ofbiz: the tables live in the OFBIZ schema.
--
-- A NEW database needs none of this: load it with the seed readers only, no demo data
--   gradle "ofbiz --load-data readers=seed,ext"
-- and admin (gradle loadAdminUserLogin) is the only login that exists.

connect 'jdbc:derby:F:\build-ofbiz\ofbiz-prod\runtime\data\derby\ofbiz;user=ofbiz';

UPDATE OFBIZ.USER_LOGIN
   SET ENABLED = 'N',
       DISABLED_DATE_TIME = NULL,
       SUCCESSIVE_FAILED_LOGINS = 0
 WHERE USER_LOGIN_ID NOT IN ('admin', 'system');

SELECT USER_LOGIN_ID, ENABLED FROM OFBIZ.USER_LOGIN WHERE COALESCE(ENABLED, 'Y') = 'Y'
 ORDER BY USER_LOGIN_ID;

exit;
