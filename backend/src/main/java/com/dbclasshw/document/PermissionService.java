package com.dbclasshw.document;

import com.dbclasshw.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PermissionService {
    private final DocumentService documents;
    private final PermissionMapper permissions;
    private final UserMapper users;
    private final org.springframework.context.ApplicationEventPublisher events;

    public PermissionService(DocumentService documents,PermissionMapper permissions,UserMapper users,
                             org.springframework.context.ApplicationEventPublisher events) {
        this.documents=documents; this.permissions=permissions; this.users=users;
        this.events=events;
    }

    @Transactional
    public List<Permission> list(long id,long actor) {
        documents.lockOwner(id,actor);
        return permissions.findAll(id);
    }

    @Transactional
    public List<Permission> add(long id,long actor,SharingRequests.Add request) {
        documents.lockOwner(id,actor);
        var target=users.findByLogin(request.login().trim());
        if(target==null || !target.enabled) throw new IllegalArgumentException("Target user unavailable");
        if(target.id==actor) throw new IllegalArgumentException("Cannot share to owner");
        if(permissions.lockRole(id,target.id)!=null) throw new SharingConflictException();
        permissions.insert(id,target.id,request.role());
        return permissions.findAll(id);
    }

    @Transactional
    public List<Permission> change(long id,long actor,long target,SharingRequests.Change request) {
        documents.lockOwner(id,actor);
        if(target==actor) throw new IllegalArgumentException("Cannot change owner");
        if(permissions.lockRole(id,target)==null) throw new DocumentNotFoundException();
        permissions.update(id,target,request.role());
        events.publishEvent(new com.dbclasshw.sync.DocumentEvent("ACCESS_CHANGED",id,null,null,actor,target));
        return permissions.findAll(id);
    }

    @Transactional
    public void revoke(long id,long actor,long target) {
        documents.lockOwner(id,actor);
        if(target==actor) throw new IllegalArgumentException("Cannot revoke owner");
        if(permissions.delete(id,target)==0) throw new DocumentNotFoundException();
        events.publishEvent(new com.dbclasshw.sync.DocumentEvent("ACCESS_CHANGED",id,null,null,actor,target));
    }
}
