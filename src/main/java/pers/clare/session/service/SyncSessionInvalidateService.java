package pers.clare.session.service;

public interface SyncSessionInvalidateService {

    void invalidate(String id);

    void invalidateByUsername(String username, String... excludeSessionIds);

}
