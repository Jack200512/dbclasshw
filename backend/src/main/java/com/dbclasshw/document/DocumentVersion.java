package com.dbclasshw.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentVersion {
    public Long id;
    public Long documentId;
    public Integer versionNumber;
    public String title;
    public String content;
    public String contentFormat;
    public Long editorId;
    public String editorName;
    public LocalDateTime createdAt;
    public String changeType;
    public Integer sourceVersion;
}
