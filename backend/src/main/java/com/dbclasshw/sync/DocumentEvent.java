package com.dbclasshw.sync;

import java.time.LocalDateTime;

/** Internal transaction event, never accepted from clients. */
public record DocumentEvent(String type, long documentId, Integer version,
                            LocalDateTime updatedAt, Long actorId, Long targetId) { }
