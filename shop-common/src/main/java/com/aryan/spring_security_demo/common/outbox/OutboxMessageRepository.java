package com.aryan.spring_security_demo.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * Up to {@code limit} messages due by {@code dueBy}, oldest first, row-locked until
     * the claiming transaction ends. {@code SKIP LOCKED} passes over the rows another
     * relay is claiming instead of waiting for them, so two relays never queue on
     * each other or claim the same message. Native: JPQL can't say {@code SKIP LOCKED}.
     */
    @Query(value = "select * from outbox_message where processed_at is null and available_at <= :dueBy"
            + " order by available_at, id limit :limit for update skip locked", nativeQuery = true)
    List<OutboxMessage> lockDue(@Param("dueBy") Instant dueBy, @Param("limit") int limit);

    /** The same, for the given messages only: those just written, delivered right after their commit. */
    @Query(value = "select * from outbox_message where id in (:ids) and processed_at is null"
            + " and available_at <= :dueBy order by id for update skip locked", nativeQuery = true)
    List<OutboxMessage> lockDueByIds(@Param("ids") Collection<Long> ids, @Param("dueBy") Instant dueBy);

    @Modifying
    @Query("delete from OutboxMessage m where m.processedAt < :before")
    int deleteProcessedBefore(@Param("before") Instant before);
}
