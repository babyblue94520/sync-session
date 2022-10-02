package pers.clare.session.listener;

import pers.clare.session.constant.InvalidateBy;

public interface SyncSessionInvalidateListener {
    /**
     * @param id       session id
     * @param username session username
     * @param type     {@link InvalidateBy}
     */
    void onInvalidate(String id, String username, int type);
}
