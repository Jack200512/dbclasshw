package com.dbclasshw.document;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentService {
    private final DocumentMapper mapper;
    private final RichTextContent richText;
    private final PermissionMapper permissions;
    private final DocumentVersionMapper versions;
    private final org.springframework.context.ApplicationEventPublisher events;

    public DocumentService(DocumentMapper mapper, RichTextContent richText, PermissionMapper permissions,
                           org.springframework.context.ApplicationEventPublisher events, DocumentVersionMapper versions) {
        this.mapper = mapper;
        this.richText = richText;
        this.permissions = permissions;
        this.events = events;
        this.versions = versions;
    }

    @Transactional
    public Document create(CreateDocumentRequest request, long ownerId) {
        Document document = new Document();
        document.setTitle(request.title());
        document.setContent(richText.serialize(request.content()));
        document.setOwnerId(ownerId);
        mapper.insert(document);
        Document saved=findById(document.getId(), ownerId);
        versions.insert(saved,ownerId,"CREATED",null);
        return saved;
    }

    public List<Document> findAll(long ownerId, String keyword) {
        if (keyword.length()>255) throw new IllegalArgumentException("Keyword too long");
        String escaped=keyword.replace("!","!!").replace("%","!%").replace("_","!_");
        return mapper.findAll(ownerId, escaped);
    }

    public Document findById(long id, long ownerId) {
        Document document = mapper.findById(id, ownerId);
        if (document == null) throw new DocumentNotFoundException();
        return document;
    }

    @Transactional
    public Document update(long id, long ownerId, UpdateDocumentRequest request) {
        Document locked=lockAccessible(id,ownerId);
        if (!"OWNER".equals(locked.getCurrentRole()) && !"EDITOR".equals(locked.getCurrentRole())) forbidden();
        Document document=new Document();
        document.setId(id);
        document.setOwnerId(ownerId);
        document.setTitle(request.title());
        document.setContent(richText.serialize(request.content()));
        document.setContentFormat("TIPTAP_JSON");
        document.setVersion(request.version());
        if (mapper.update(document,ownerId)==0) {
            findById(id,ownerId);
            throw new DocumentVersionConflictException();
        }
        Document saved=findById(id,ownerId);
        versions.insert(saved,ownerId,"UPDATED",null);
        events.publishEvent(new com.dbclasshw.sync.DocumentEvent("DOCUMENT_UPDATED",id,saved.getVersion(),saved.getUpdatedAt(),ownerId,null));
        return saved;
    }

    @Transactional
    public List<DocumentVersion> versions(long id,long userId) {
        lockAccessible(id,userId);
        return versions.list(id);
    }

    @Transactional
    public DocumentVersion version(long id,long userId,int number) {
        lockAccessible(id,userId);
        return requiredVersion(id,number);
    }

    private DocumentVersion requiredVersion(long id,int number) {
        DocumentVersion result=versions.find(id,number);
        if(result==null) throw new DocumentNotFoundException();
        return result;
    }

    @Transactional
    public Document restore(long id,long userId,int number,int expectedVersion) {
        Document current=lockAccessible(id,userId);
        if(!"OWNER".equals(current.getCurrentRole()) && !"EDITOR".equals(current.getCurrentRole())) forbidden();
        if(current.getVersion()!=expectedVersion) throw new DocumentVersionConflictException();
        DocumentVersion source=requiredVersion(id,number);
        if("TIPTAP_JSON".equals(source.contentFormat)) {
            try { richText.serialize(new com.fasterxml.jackson.databind.ObjectMapper().readTree(source.content)); }
            catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Invalid stored content"); }
        } else if(!"PLAIN_TEXT".equals(source.contentFormat)) throw new IllegalArgumentException("Invalid stored format");
        current.setTitle(source.title);
        current.setContent(source.content);
        current.setContentFormat(source.contentFormat);
        if(mapper.update(current,userId)==0) throw new DocumentVersionConflictException();
        Document saved=findById(id,userId);
        versions.insert(saved,userId,"RESTORED",number);
        events.publishEvent(new com.dbclasshw.sync.DocumentEvent("DOCUMENT_UPDATED",id,saved.getVersion(),saved.getUpdatedAt(),userId,null));
        return saved;
    }

    @Transactional
    public void delete(long id,long ownerId) {
        lockOwner(id,ownerId);
        if (mapper.delete(id,ownerId)==0) throw new DocumentNotFoundException();
        events.publishEvent(new com.dbclasshw.sync.DocumentEvent("DOCUMENT_DELETED",id,null,null,ownerId,null));
    }

    /** All document writes and permission changes lock this row first, in one transaction. */
    Document lockAccessible(long id,long userId) {
        Document document=mapper.lockById(id);
        if(document==null) throw new DocumentNotFoundException();
        String role=document.getOwnerId()==userId?"OWNER":permissions.lockRole(id,userId);
        if(role==null) throw new DocumentNotFoundException();
        document.setCurrentRole(role);
        return document;
    }

    /** Caller must hold a transaction. Also used by notification delivery in REQUIRES_NEW. */
    public Document lockReadable(long id,long userId) { return lockAccessible(id,userId); }

    public Document lockOwner(long id,long userId) {
        Document document=lockAccessible(id,userId);
        if(!"OWNER".equals(document.getCurrentRole())) forbidden();
        return document;
    }

    private void forbidden() { throw new org.springframework.security.access.AccessDeniedException("Insufficient document permission"); }
}
