package com.primestar.session.listener;

import com.primestar.session.constant.InvalidateBy;

public interface SyncSessionInvalidateListener {
    /**
     * @param id session id
     * @param username session username
     * @param type   {@link InvalidateBy}
     */
    void onInvalidate(String id, String username, int type);
}
