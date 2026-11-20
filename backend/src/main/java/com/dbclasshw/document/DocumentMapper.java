package com.dbclasshw.document;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentMapper {
    String COLUMNS = "id, owner_id, title, content, content_format, version, created_at, updated_at";
    String ACCESS_COLUMNS = "d.id,d.owner_id,d.title,d.content,d.content_format,d.version,d.created_at,d.updated_at,CASE WHEN d.owner_id=#{userId} THEN 'OWNER' ELSE p.role END AS current_role";
    String ACCESS_JOIN = " FROM documents d LEFT JOIN document_permissions p ON p.document_id=d.id AND p.user_id=#{userId} ";

    @Insert("INSERT INTO documents (owner_id, title, content, content_format) VALUES (#{ownerId}, #{title}, #{content}, 'TIPTAP_JSON')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Document document);

    @Select("SELECT " + ACCESS_COLUMNS + ACCESS_JOIN + " WHERE (d.owner_id=#{userId} OR p.id IS NOT NULL) AND d.title LIKE CONCAT('%',#{keyword},'%') ESCAPE '!' ORDER BY d.id")
    List<Document> findAll(@org.apache.ibatis.annotations.Param("userId") long userId,
                          @org.apache.ibatis.annotations.Param("keyword") String keyword);

    @Select("SELECT " + ACCESS_COLUMNS + ACCESS_JOIN + " WHERE d.id=#{id} AND (d.owner_id=#{userId} OR p.id IS NOT NULL)")
    Document findById(@org.apache.ibatis.annotations.Param("id") long id,
                      @org.apache.ibatis.annotations.Param("userId") long userId);

    @org.apache.ibatis.annotations.Update("UPDATE documents SET title=#{document.title},content=#{document.content},content_format='TIPTAP_JSON',version=version+1 WHERE id=#{document.id} AND version=#{document.version} AND (owner_id=#{userId} OR EXISTS (SELECT 1 FROM document_permissions p WHERE p.document_id=documents.id AND p.user_id=#{userId} AND p.role='EDITOR'))")
    int update(@org.apache.ibatis.annotations.Param("document") Document document,
               @org.apache.ibatis.annotations.Param("userId") long userId);

    @Select("SELECT " + COLUMNS + " FROM documents WHERE id=#{id} FOR UPDATE")
    Document lockById(long id);

    @org.apache.ibatis.annotations.Delete("DELETE FROM documents WHERE id=#{id} AND owner_id=#{ownerId}")
    int delete(@org.apache.ibatis.annotations.Param("id") long id,
               @org.apache.ibatis.annotations.Param("ownerId") long ownerId);
}
