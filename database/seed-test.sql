-- Testing fixtures only. Never use --force: mysql batch execution must stop on the first error.
-- Set @seed_worker_hash and @seed_manager_hash privately in the SAME connection before this file.
-- No ADMIN is created. No known password/hash is shipped. Empty disposable four-table schema only.
CREATE TEMPORARY TABLE rockey_seed_guard (allowed TINYINT NOT NULL CHECK (allowed = 1));
INSERT INTO rockey_seed_guard SELECT CASE WHEN DATABASE() = 'rockey_hospitality_hardening'
    AND @seed_worker_hash REGEXP '^[$]2[aby][$](1[0-6])[$][./A-Za-z0-9]{53}$'
    AND @seed_manager_hash REGEXP '^[$]2[aby][$](1[0-6])[$][./A-Za-z0-9]{53}$'
    THEN 1 ELSE 0 END;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
START TRANSACTION;
-- Empty-range locks prevent a concurrent insert between the empty guard and fixture insertion.
SELECT id FROM users FOR UPDATE;
SELECT id FROM rooms FOR UPDATE;
SELECT id FROM tasks FOR UPDATE;
SELECT id FROM inspections FOR UPDATE;
INSERT INTO rockey_seed_guard SELECT CASE WHEN
    (SELECT COUNT(*) FROM users) + (SELECT COUNT(*) FROM rooms) +
    (SELECT COUNT(*) FROM tasks) + (SELECT COUNT(*) FROM inspections) = 0 THEN 1 ELSE 0 END;
SET @seed_now = CURRENT_TIMESTAMP(6);
INSERT INTO users(id,name,email,password_hash,role,active,created_at,updated_at) VALUES
(1,'Synthetic supervisor','seed-manager@example.test',@seed_manager_hash,'MANAGER',TRUE,@seed_now,@seed_now),
(2,'Synthetic worker','seed-worker@example.test',@seed_worker_hash,'USER',TRUE,@seed_now,@seed_now);
INSERT INTO rooms(id,room_number,floor,status,active,created_at,updated_at) VALUES
(1,'T101',1,'DIRTY',TRUE,@seed_now,@seed_now),
(2,'T102',1,'READY',TRUE,@seed_now,@seed_now),
(3,'T103',1,'DIRTY',TRUE,@seed_now,@seed_now);
INSERT INTO tasks(id,title,description,status,priority,assigned_user_id,room_id,due_at,completed_at,created_at,updated_at) VALUES
(1,'Clean T101','Synthetic assignment','ASSIGNED','HIGH',2,1,NULL,NULL,@seed_now,@seed_now),
(2,'Clean T102','Synthetic completed cycle','COMPLETED','MEDIUM',2,2,NULL,@seed_now,@seed_now,@seed_now),
(3,'Clean T103','Synthetic rejected inspection cycle','COMPLETED','MEDIUM',2,3,NULL,@seed_now,@seed_now,@seed_now);
INSERT INTO inspections(id,room_id,task_id,inspected_by_user_id,result,notes,inspected_at) VALUES
(1,2,2,1,'PASS','Synthetic pass history',@seed_now),
(2,3,3,1,'FAIL','Synthetic rework needed',@seed_now);
COMMIT;
-- Connection close discards guard and private inputs. Batch failure before COMMIT rolls back.
