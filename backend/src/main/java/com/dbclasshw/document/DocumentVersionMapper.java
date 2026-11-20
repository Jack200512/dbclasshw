package com.dbclasshw.document;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DocumentVersionMapper {
    String METADATA="v.id,v.document_id,v.version_number,v.title,v.content_format,v.editor_id,u.username AS editor_name,v.created_at,v.change_type,v.source_version";

    @Select("SELECT "+METADATA+" FROM document_versions v JOIN users u ON u.id=v.editor_id WHERE v.document_id=#{id} ORDER BY v.version_number DESC")
    List<DocumentVersion> list(long id);

    @Select("SELECT "+METADATA+",v.content FROM document_versions v JOIN users u ON u.id=v.editor_id WHERE v.document_id=#{id} AND v.version_number=#{version}")
    DocumentVersion find(@Param("id") long id,@Param("version") int version);

    // There is intentionally no UPDATE/DELETE mapper for individual snapshots.
    @Insert("INSERT INTO document_versions(document_id,version_number,title,content,content_format,editor_id,created_at,change_type,source_version) VALUES(#{document.id},#{document.version},#{document.title},#{document.content},#{document.contentFormat},#{editorId},#{document.updatedAt},#{changeType},#{sourceVersion})")
    int insert(@Param("document") Document document,@Param("editorId") long editorId,
               @Param("changeType") String changeType,@Param("sourceVersion") Integer sourceVersion);
}
