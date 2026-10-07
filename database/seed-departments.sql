-- Fictional Department seed data; no login accounts or credentials.
-- Rerunning this script preserves an existing department's active/inactive state.

INSERT INTO departments (name, description)
VALUES
    ('Front Desk', 'Front office and guest-services operations'),
    ('Housekeeping', 'Room cleaning, inspection, and turnover operations'),
    ('Maintenance', 'Property maintenance and repair operations'),
    ('Events', 'Hotel event planning and operational support'),
    ('Management', 'Hotel leadership and management operations'),
    ('Purchasing', 'Purchasing and supply coordination')
ON DUPLICATE KEY UPDATE
    description = VALUES(description);
