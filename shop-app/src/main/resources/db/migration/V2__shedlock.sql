-- ===========================================================================
-- V2 — ShedLock table.
--
-- Backs @SchedulerLock on the refresh-token purge (RefreshTokenCleanupService)
-- so that in a multi-instance deployment only one node runs the scheduled task
-- at a time. Column names/types are exactly what the ShedLock JDBC provider
-- expects; TIMESTAMP(3) gives millisecond precision, matching usingDbTime().
-- ===========================================================================

create table shedlock (
    name varchar(64) not null,
    lock_until timestamp(3) not null,
    locked_at timestamp(3) not null,
    locked_by varchar(255) not null,
    primary key (name)
) engine=InnoDB;
