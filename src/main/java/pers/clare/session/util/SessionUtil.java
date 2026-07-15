package pers.clare.session.util;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SessionUtil {

    public static String generateId() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }

}
