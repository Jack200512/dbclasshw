package com.dbclasshw.document;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentService {
    private final DocumentMapper mapper;

    public DocumentService(DocumentMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public Document create(CreateDocumentRequest request, long ownerId) {
        Document document = new Document();
        document.setTitle(request.title());
        document.setContent(request.content());
        document.setOwnerId(ownerId);
        mapper.insert(document);
        return findById(document.getId(), ownerId);
    }

    public List<Document> findAll(long ownerId) {
        return mapper.findAll(ownerId);
    }

    public Document findById(long id, long ownerId) {
        Document document = mapper.findById(id, ownerId);
        if (document == null) throw new DocumentNotFoundException();
        return document;
    }
}
