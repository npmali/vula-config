-- UCT Vula 12.x db conversion script: 11.x to 12.0
-- Source: https://github.com/sakaiproject/sakai-reference/blob/master/docs/conversion/sakai_12_mysql_conversion.sql

SET @uct_start = NOW();
SELECT @uct_start as 'Start';

-- SAM-3016
ALTER TABLE SAM_EVENTLOG_T ADD IPADDRESS varchar(99);

-- SAK-30207
CREATE TABLE IF NOT EXISTS CONTENTREVIEW_ITEM (
    ID                  BIGINT NOT NULL AUTO_INCREMENT,
    VERSION             INT NOT NULL,
    PROVIDERID          INT NOT NULL,
    CONTENTID           VARCHAR(255) NOT NULL,
    USERID              VARCHAR(255),
    SITEID              VARCHAR(255),
    TASKID              VARCHAR(255),
    EXTERNALID          VARCHAR(255),
    DATEQUEUED          DATETIME NOT NULL,
    DATESUBMITTED       DATETIME,
    DATEREPORTRECEIVED  DATETIME,
    STATUS              BIGINT,
    REVIEWSCORE         INT,
    LASTERROR           LONGTEXT,
    RETRYCOUNT          BIGINT,
    NEXTRETRYTIME       DATETIME NOT NULL,
    ERRORCODE           INT,
    PRIMARY KEY (ID),
    CONSTRAINT PROVIDERID UNIQUE (PROVIDERID, CONTENTID)
);
-- END SAK-30207

-- 
-- SAK-31641 Switch from INTs to VARCHARs in Oauth
select 'SAK-31641' as 'On';

ALTER TABLE OAUTH_ACCESSORS
CHANGE
  status status VARCHAR(255),
  CHANGE type type VARCHAR(255)
;

UPDATE OAUTH_ACCESSORS SET status = CASE
  WHEN status = 0 THEN "VALID"
  WHEN status = 1 THEN "REVOKED"
  WHEN status = 2 THEN "EXPIRED"
END;

UPDATE OAUTH_ACCESSORS SET type = CASE
  WHEN type = 0 THEN "REQUEST"
  WHEN type = 1 THEN "REQUEST_AUTHORISING"
  WHEN type = 2 THEN "REQUEST_AUTHORISED"
  WHEN type = 3 THEN "ACCESS"
END;

--
-- SAK-31636 Rename existing 'Home' tools
select 'SAK-31636' as 'On';

update SAKAI_SITE_PAGE set title = 'Overview' where title = 'Home';

--
-- SAK-31563
--
select 'SAK-31563' as 'On';

-- Add new user_id columns and their corresponding indexes

ALTER TABLE pasystem_popup_assign ADD user_id varchar(99);
ALTER TABLE pasystem_popup_dismissed ADD user_id varchar(99);
ALTER TABLE pasystem_banner_dismissed ADD user_id varchar(99);

CREATE INDEX popup_assign_lower_user_id on pasystem_popup_assign (user_id);
CREATE INDEX popup_dismissed_lower_user_id on pasystem_popup_dismissed (user_id);
CREATE INDEX banner_dismissed_user_id on pasystem_banner_dismissed (user_id);

-- Map existing EIDs to their corresponding user IDs
-- update pasystem_popup_assign popup set user_id = (select user_id from sakai_user_id_map map where popup.user_eid = map.eid);
update pasystem_popup_assign popup set user_id = (select `map`.`user_id` from SAKAI_USER_ID_MAP map where `popup`.`user_id` = `map`.`eid`);

-- update pasystem_popup_dismissed popup set user_id = (select user_id from sakai_user_id_map map where popup.user_eid = map.eid);
update pasystem_popup_dismissed popup set user_id = (select user_id from SAKAI_USER_ID_MAP map where popup.user_id = map.eid);

-- update pasystem_banner_dismissed banner set user_id = (select user_id from sakai_user_id_map map where banner.user_eid = map.eid);
update pasystem_banner_dismissed banner set user_id = (select user_id from SAKAI_USER_ID_MAP map where banner.user_id = map.eid);

-- Any rows that couldn't be mapped are dropped (there shouldn't
-- really be any, but if there are those users were already being
-- ignored when identified by EID)
DELETE FROM pasystem_popup_assign WHERE user_id is null;
DELETE FROM pasystem_popup_dismissed WHERE user_id is null;
DELETE FROM pasystem_banner_dismissed WHERE user_id is null;

-- Enforce NULL checks on the new columns
ALTER TABLE pasystem_popup_assign MODIFY user_id varchar(99) NOT NULL;
ALTER TABLE pasystem_popup_dismissed MODIFY user_id varchar(99) NOT NULL;
ALTER TABLE pasystem_banner_dismissed MODIFY user_id varchar(99) NOT NULL;

-- Reintroduce unique constraints for the new column
ALTER TABLE pasystem_popup_dismissed drop INDEX unique_popup_dismissed;
ALTER TABLE pasystem_popup_dismissed add UNIQUE INDEX unique_popup_dismissed (user_id, state, uuid);

ALTER TABLE pasystem_banner_dismissed drop INDEX unique_banner_dismissed;
ALTER TABLE pasystem_banner_dismissed add UNIQUE INDEX unique_banner_dismissed (user_id, state, uuid);

-- Drop the old columns
ALTER TABLE pasystem_popup_assign DROP COLUMN user_eid;
ALTER TABLE pasystem_popup_dismissed DROP COLUMN user_eid;
ALTER TABLE pasystem_banner_dismissed DROP COLUMN user_eid;

-- LSNBLDR-633 Restrict editing of Lessons pages and subpages to one person
ALTER TABLE lesson_builder_pages ADD owned bit default false not null;
-- END LSNBLDR-633
--
-- SAK-31840 update defaults as its now managed in the POJO
--
-- alter table GB_GRADABLE_OBJECT_T CHANGE column IS_EXTRA_CREDIT bit(1) DEFAULT NULL;
select 'SAK-31840' as 'On';

alter table GB_GRADABLE_OBJECT_T CHANGE COLUMN `IS_EXTRA_CREDIT` `IS_EXTRA_CREDIT` BIT(1) NULL DEFAULT NULL;

-- alter table GB_GRADABLE_OBJECT_T CHANGE column HIDE_IN_ALL_GRADES_TABLE bit(1) DEFAULT NULL;
alter table GB_GRADABLE_OBJECT_T CHANGE COLUMN `HIDE_IN_ALL_GRADES_TABLE` `HIDE_IN_ALL_GRADES_TABLE` BIT(1) NULL DEFAULT NULL;

-- BEGIN SAK-31819 Remove the old ScheduledInvocationManager job as it's not present in Sakai 12.
select 'SAK-31819' as 'On';

DELETE FROM QRTZ_SIMPLE_TRIGGERS WHERE TRIGGER_NAME='org.sakaiproject.component.app.scheduler.ScheduledInvocationManagerImpl.runner';
DELETE FROM QRTZ_TRIGGERS WHERE TRIGGER_NAME='org.sakaiproject.component.app.scheduler.ScheduledInvocationManagerImpl.runner';
-- This one is the actual job that the triggers were trying to run
DELETE FROM QRTZ_JOB_DETAILS WHERE JOB_NAME='org.sakaiproject.component.app.scheduler.ScheduledInvocationManagerImpl.runner';
-- END SAK-31819

-- BEGIN SAK-15708 avoid duplicate rows
select 'SAK-15708' as 'On';

CREATE TABLE SAKAI_POSTEM_STUDENT_DUPES (
  id bigint(20) NOT NULL,
  username varchar(99),
  surrogate_key bigint(20)
);

INSERT INTO SAKAI_POSTEM_STUDENT_DUPES SELECT MAX(id), username, surrogate_key FROM SAKAI_POSTEM_STUDENT GROUP BY username, surrogate_key HAVING count(id) > 1;

DELETE FROM SAKAI_POSTEM_STUDENT_GRADES WHERE student_id IN (SELECT id FROM SAKAI_POSTEM_STUDENT_DUPES);

DELETE FROM SAKAI_POSTEM_STUDENT WHERE id IN (SELECT id FROM SAKAI_POSTEM_STUDENT_DUPES);

DROP TABLE SAKAI_POSTEM_STUDENT_DUPES;

ALTER TABLE SAKAI_POSTEM_STUDENT MODIFY COLUMN username varchar(99), DROP INDEX POSTEM_STUDENT_USERNAME_I,
  ADD UNIQUE INDEX POSTEM_USERNAME_SURROGATE (username, surrogate_key);
-- END SAK-15708

-- BEGIN SAK-32083 TAGS
select 'SAK-32083' as 'On';
CREATE TABLE IF NOT EXISTS `tagservice_collection` (
  `tagcollectionid` CHAR(36) PRIMARY KEY,
  `description` TEXT,
  `externalsourcename` VARCHAR(255) UNIQUE,
  `externalsourcedescription` TEXT,
  `name` VARCHAR(255) UNIQUE,
  `createdby` VARCHAR(255),
  `creationdate` BIGINT,
  `lastmodifiedby` VARCHAR(255),
  `lastmodificationdate` BIGINT,
  `lastsynchronizationdate` BIGINT,
  `externalupdate` BOOLEAN,
  `externalcreation` BOOLEAN,
  `lastupdatedateinexternalsystem` BIGINT
);

CREATE TABLE IF NOT EXISTS `tagservice_tag` (
  `tagid` CHAR(36) PRIMARY KEY,
  `tagcollectionid` CHAR(36) NOT NULL,
  `externalid` VARCHAR(255),
  `taglabel` VARCHAR(255),
  `description` TEXT,
  `alternativelabels` TEXT,
  `createdby` VARCHAR(255),
  `creationdate` BIGINT,
  `externalcreation` BOOLEAN,
  `externalcreationDate` BIGINT,
  `externalupdate` BOOLEAN,
  `lastmodifiedby` VARCHAR(255),
  `lastmodificationdate` BIGINT,
  `lastupdatedateinexternalsystem` BIGINT,
  `parentid` VARCHAR(255),
  `externalhierarchycode` TEXT,
  `externaltype` VARCHAR(255),
  `data` TEXT,
  INDEX tagservice_tag_taglabel (taglabel),
  INDEX tagservice_tag_tagcollectionid (tagcollectionid),
  INDEX tagservice_tag_externalid (externalid),
  FOREIGN KEY (tagcollectionid)
  REFERENCES tagservice_collection(tagcollectionid)
    ON DELETE RESTRICT
);


-- KNL-1566
ALTER TABLE SAKAI_USER CHANGE MODIFIEDON MODIFIEDON DATETIME NOT NULL;

INSERT IGNORE INTO SAKAI_REALM_FUNCTION (FUNCTION_NAME) VALUES ('tagservice.manage');
-- END SAK-32083 TAGS

-- BEGIN 3432 Grade Points Grading Scale
-- add the new grading scale
INSERT INTO GB_GRADING_SCALE_T (object_type_id, version, scale_uid, name, unavailable)
VALUES (0, 0, 'GradePointsMapping', 'Grade Points', 0);

-- add the grade ordering
select '3432 - grade' as 'On';
INSERT INTO GB_GRADING_SCALE_GRADES_T (grading_scale_id, letter_grade, grade_idx)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 'A (4.0)', 0);

INSERT INTO GB_GRADING_SCALE_GRADES_T (grading_scale_id, letter_grade, grade_idx)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 'A- (3.67)', 1);

INSERT INTO GB_GRADING_SCALE_GRADES_T (grading_scale_id, letter_grade, grade_idx)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 'B+ (3.33)', 2);

INSERT INTO GB_GRADING_SCALE_GRADES_T (grading_scale_id, letter_grade, grade_idx)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 'B (3.0)', 3);

INSERT INTO GB_GRADING_SCALE_GRADES_T (grading_scale_id, letter_grade, grade_idx)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 'B- (2.67)', 4);

INSERT INTO GB_GRADING_SCALE_GRADES_T (grading_scale_id, letter_grade, grade_idx)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 'C+ (2.33)', 5);

INSERT INTO GB_GRADING_SCALE_GRADES_T (grading_scale_id, letter_grade, grade_idx)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 'C (2.0)', 6);

INSERT INTO GB_GRADING_SCALE_GRADES_T (grading_scale_id, letter_grade, grade_idx)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 'C- (1.67)', 7);

INSERT INTO GB_GRADING_SCALE_GRADES_T (grading_scale_id, letter_grade, grade_idx)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 'D (1.0)', 8);

INSERT INTO GB_GRADING_SCALE_GRADES_T (grading_scale_id, letter_grade, grade_idx)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 'F (0)', 9);

-- add the percent mapping
select '3432 - precent' as 'On';
INSERT INTO GB_GRADING_SCALE_PERCENTS_T (grading_scale_id, percent, letter_grade)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 100, 'A (4.0)');

INSERT INTO GB_GRADING_SCALE_PERCENTS_T (grading_scale_id, percent, letter_grade)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 90, 'A- (3.67)');

INSERT INTO GB_GRADING_SCALE_PERCENTS_T (grading_scale_id, percent, letter_grade)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 87, 'B+ (3.33)');

INSERT INTO GB_GRADING_SCALE_PERCENTS_T (grading_scale_id, percent, letter_grade)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 83, 'B (3.0)');

INSERT INTO GB_GRADING_SCALE_PERCENTS_T (grading_scale_id, percent, letter_grade)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 80, 'B- (2.67)');

INSERT INTO GB_GRADING_SCALE_PERCENTS_T (grading_scale_id, percent, letter_grade)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 77, 'C+ (2.33)');

INSERT INTO GB_GRADING_SCALE_PERCENTS_T (grading_scale_id, percent, letter_grade)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 73, 'C (2.0)');

INSERT INTO GB_GRADING_SCALE_PERCENTS_T (grading_scale_id, percent, letter_grade)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 70, 'C- (1.67)');

INSERT INTO GB_GRADING_SCALE_PERCENTS_T (grading_scale_id, percent, letter_grade)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 67, 'D (1.0)');

INSERT INTO GB_GRADING_SCALE_PERCENTS_T (grading_scale_id, percent, letter_grade)
VALUES(
(SELECT id FROM GB_GRADING_SCALE_T WHERE scale_uid = 'GradePointsMapping')
, 0, 'F (0)');

-- add the new scale to all existing gradebook sites
INSERT INTO GB_GRADE_MAP_T (object_type_id, version, gradebook_id, GB_GRADING_SCALE_T)
SELECT 
  0
, 0
, gb.id
, gs.id
FROM GB_GRADEBOOK_T gb
JOIN GB_GRADING_SCALE_T gs
  ON gs.scale_uid = 'GradePointsMapping';
-- END 3432

-- SAM-1129 Change the column DESCRIPTION of SAM_QUESTIONPOOL_T from VARCHAR(255) to longtext
select 'SAM-1129' as 'On';
ALTER TABLE SAM_QUESTIONPOOL_T MODIFY DESCRIPTION longtext;

-- SAK-30461 Portal bullhorns
-- added extra index SAK-33858

 CREATE TABLE `BULLHORN_ALERTS` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `ALERT_TYPE` varchar(8) NOT NULL,
  `FROM_USER` varchar(99) NOT NULL,
  `TO_USER` varchar(99) NOT NULL,
  `EVENT` varchar(32) NOT NULL,
  `REF` varchar(255) NOT NULL,
  `TITLE` varchar(255) DEFAULT NULL,
  `SITE_ID` varchar(99) DEFAULT NULL,
  `URL` text NOT NULL,
  `EVENT_DATE` datetime NOT NULL,
  PRIMARY KEY (`ID`),
  KEY `bullhorn_user_type_i` (`TO_USER`,`ALERT_TYPE`)
) ENGINE=InnoDB AUTO_INCREMENT=1238 DEFAULT CHARSET=utf8mb4

-- SAK-32417 Forums permission composite index
ALTER TABLE MFR_PERMISSION_LEVEL_T ADD INDEX MFR_COMPOSITE_PERM (TYPE_UUID, NAME);

-- SAK-32442 - LTI Column cleanup
-- These conversions may fail if you started Sakai at newer versions that didn't contain these columns/tables
select 'SAK-32442' as 'On';

alter table lti_tools drop column enabled_capability;
alter table lti_deploy drop column allowlori;
alter table lti_tools drop column allowlori;
drop table lti_mapping;
-- END SAK-32442

-- SAK-32572 Additional permission settings for Messages
select 'SAK-32572' as 'On';

INSERT INTO SAKAI_REALM_FUNCTION VALUES (DEFAULT, 'msg.permissions.allowToField.myGroupRoles');

-- The permission above is false for all users by default
-- if you want to turn this feature on for all "student/acces" type roles, then run 
-- the following conversion:

INSERT INTO SAKAI_REALM_RL_FN VALUES((select REALM_KEY from SAKAI_REALM where REALM_ID = '!site.template'), (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = 'access'), (select FUNCTION_KEY from SAKAI_REALM_FUNCTION where FUNCTION_NAME = 'msg.permissions.allowToField.myGroupRoles'));


INSERT INTO SAKAI_REALM_RL_FN VALUES((select REALM_KEY from SAKAI_REALM where REALM_ID = '!site.template'), (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = 'maintain'), (select FUNCTION_KEY from SAKAI_REALM_FUNCTION where FUNCTION_NAME = 'msg.permissions.allowToField.myGroupRoles'));


INSERT INTO SAKAI_REALM_RL_FN VALUES((select REALM_KEY from SAKAI_REALM where REALM_ID = '!site.template.course'), (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = 'Instructor'), (select FUNCTION_KEY from SAKAI_REALM_FUNCTION where FUNCTION_NAME = 'msg.permissions.allowToField.myGroupRoles'));


INSERT INTO SAKAI_REALM_RL_FN VALUES((select REALM_KEY from SAKAI_REALM where REALM_ID = '!site.template.course'), (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = 'Student'), (select FUNCTION_KEY from SAKAI_REALM_FUNCTION where FUNCTION_NAME = 'msg.permissions.allowToField.myGroupRoles'));


INSERT INTO SAKAI_REALM_RL_FN VALUES((select REALM_KEY from SAKAI_REALM where REALM_ID = '!site.template.course'), (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = 'Teaching Assistant'), (select FUNCTION_KEY from SAKAI_REALM_FUNCTION where FUNCTION_NAME = 'msg.permissions.allowToField.myGroupRoles'));


INSERT INTO SAKAI_REALM_RL_FN VALUES((select REALM_KEY from SAKAI_REALM where REALM_ID = '!site.template.portfolio'), (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = 'CIG Coordinator'), (select FUNCTION_KEY from SAKAI_REALM_FUNCTION where FUNCTION_NAME = 'msg.permissions.allowToField.myGroupRoles'));


INSERT INTO SAKAI_REALM_RL_FN VALUES((select REALM_KEY from SAKAI_REALM where REALM_ID = '!site.template.portfolio'), (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = 'CIG Participant'), (select FUNCTION_KEY from SAKAI_REALM_FUNCTION where FUNCTION_NAME = 'msg.permissions.allowToField.myGroupRoles'));


INSERT INTO SAKAI_REALM_RL_FN VALUES((select REALM_KEY from SAKAI_REALM where REALM_ID = '!site.template.portfolio'), (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = 'Reviewer'), (select FUNCTION_KEY from SAKAI_REALM_FUNCTION where FUNCTION_NAME = 'msg.permissions.allowToField.myGroupRoles'));


INSERT INTO SAKAI_REALM_RL_FN VALUES((select REALM_KEY from SAKAI_REALM where REALM_ID = '!site.template.portfolio'), (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = 'Evaluator'), (select FUNCTION_KEY from SAKAI_REALM_FUNCTION where FUNCTION_NAME = 'msg.permissions.allowToField.myGroupRoles'));


INSERT INTO SAKAI_REALM_RL_FN VALUES((select REALM_KEY from SAKAI_REALM where REALM_ID = '!site.template.portfolioAdmin'), (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = 'Program Admin'), (select FUNCTION_KEY from SAKAI_REALM_FUNCTION where FUNCTION_NAME = 'msg.permissions.allowToField.myGroupRoles'));


INSERT INTO SAKAI_REALM_RL_FN VALUES((select REALM_KEY from SAKAI_REALM where REALM_ID = '!site.template.portfolioAdmin'), (select ROLE_KEY from SAKAI_REALM_ROLE where ROLE_NAME = 'Program Coordinator'), (select FUNCTION_KEY from SAKAI_REALM_FUNCTION where FUNCTION_NAME = 'msg.permissions.allowToField.myGroupRoles'));

-- --------------------------------------------------------------------------------------------------------------------------------------
-- backfill new permission into existing realms
-- --------------------------------------------------------------------------------------------------------------------------------------

-- for each realm that has a role matching something in this table, we will add to that role the function from this table
CREATE TABLE PERMISSIONS_SRC_TEMP (ROLE_NAME VARCHAR(99), FUNCTION_NAME VARCHAR(99));


INSERT INTO PERMISSIONS_SRC_TEMP VALUES ('maintain','msg.permissions.allowToField.myGroupRoles');
INSERT INTO PERMISSIONS_SRC_TEMP VALUES ('access','msg.permissions.allowToField.myGroupRoles');
INSERT INTO PERMISSIONS_SRC_TEMP VALUES ('Instructor','msg.permissions.allowToField.myGroupRoles');
INSERT INTO PERMISSIONS_SRC_TEMP VALUES ('Teaching Assistant','msg.permissions.allowToField.myGroupRoles');
INSERT INTO PERMISSIONS_SRC_TEMP VALUES ('Student','msg.permissions.allowToField.myGroupRoles');
INSERT INTO PERMISSIONS_SRC_TEMP VALUES ('CIG Coordinator','msg.permissions.allowToField.myGroupRoles');
INSERT INTO PERMISSIONS_SRC_TEMP VALUES ('Evaluator','msg.permissions.allowToField.myGroupRoles');
INSERT INTO PERMISSIONS_SRC_TEMP VALUES ('Reviewer','msg.permissions.allowToField.myGroupRoles');
INSERT INTO PERMISSIONS_SRC_TEMP VALUES ('CIG Participant','msg.permissions.allowToField.myGroupRoles');

-- lookup the role and function number
CREATE TABLE PERMISSIONS_TEMP (ROLE_KEY INTEGER, FUNCTION_KEY INTEGER);
INSERT INTO PERMISSIONS_TEMP (ROLE_KEY, FUNCTION_KEY)
SELECT SRR.ROLE_KEY, SRF.FUNCTION_KEY
FROM PERMISSIONS_SRC_TEMP TMPSRC
JOIN SAKAI_REALM_ROLE SRR ON (TMPSRC.ROLE_NAME = SRR.ROLE_NAME)
JOIN SAKAI_REALM_FUNCTION SRF ON (TMPSRC.FUNCTION_NAME = SRF.FUNCTION_NAME);

-- insert the new function into the roles of any existing realm that has the role (don't convert the "!site.helper")
INSERT INTO SAKAI_REALM_RL_FN (REALM_KEY, ROLE_KEY, FUNCTION_KEY)
SELECT
    SRRFD.REALM_KEY, SRRFD.ROLE_KEY, TMP.FUNCTION_KEY
FROM
    (SELECT DISTINCT SRRF.REALM_KEY, SRRF.ROLE_KEY FROM SAKAI_REALM_RL_FN SRRF) SRRFD
    JOIN PERMISSIONS_TEMP TMP ON (SRRFD.ROLE_KEY = TMP.ROLE_KEY)
    JOIN SAKAI_REALM SR ON (SRRFD.REALM_KEY = SR.REALM_KEY)
    WHERE SR.REALM_ID != '!site.helper'
    AND NOT EXISTS (
        SELECT 1
            FROM SAKAI_REALM_RL_FN SRRFI
            WHERE SRRFI.REALM_KEY=SRRFD.REALM_KEY AND SRRFI.ROLE_KEY=SRRFD.ROLE_KEY AND SRRFI.FUNCTION_KEY=TMP.FUNCTION_KEY
    );

-- clean up the temp tables
DROP TABLE PERMISSIONS_TEMP;
DROP TABLE PERMISSIONS_SRC_TEMP;

-- END SAK-32572 Additional permission settings for Messages

-- SAK-33430 user_audits_log is queried against site_id
select 'SAK-33430' as 'On';
ALTER TABLE user_audits_log 
  MODIFY COLUMN site_id varchar(99),
  MODIFY COLUMN role_name varchar(99),
  DROP INDEX user_audits_log_index,
  ADD INDEX user_audits_log_index(site_id);
-- END SAK-33430

-- SAK-33406 - Allow reorder of LTI plugin tools

ALTER TABLE lti_tools ADD toolorder TINYINT DEFAULT '0';
ALTER TABLE lti_content ADD toolorder TINYINT DEFAULT '0';

-- END SAK-33406

-- BEGIN SAK-32045 -- Update My Workspace to My Home
select 'SAK-32045' as 'On';

UPDATE SAKAI_SITE
SET TITLE = 'Home', DESCRIPTION = 'Home'
WHERE SITE_ID LIKE '!user%';

UPDATE SAKAI_SITE
SET TITLE = 'Home', DESCRIPTION = 'Home'
WHERE TITLE = 'My Workspace'
AND SITE_ID LIKE '~%';

UPDATE SAKAI_SITE_TOOL
SET TITLE = 'Home' 
WHERE REGISTRATION = 'sakai.iframe.myworkspace';
-- END SAK-32045

-- UCT ========================================

-- VULA-3087: Remove chat messages that have been deleted so the new foreign__key contstraint works
delete from CHAT2_MESSAGE where CHANNEL_ID NOT IN (select distinct CHANNEL_ID FROM CHAT2_CHANNEL);

-- SAK-33869 / VULA-3123 Clean up assignments with no context (siteid)
DELETE FROM ASSIGNMENT_ASSIGNMENT WHERE CONTEXT IS NULL;

-- UCT ad-hoc

SET @uct_end = NOW();
SELECT @uct_start as 'Start', @uct_end as 'End', TIMEDIFF(@uct_end, @uct_start) as 'Duration';

-- SHOW WARNINGS;
