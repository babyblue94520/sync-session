package pers.clare.session;

import org.springframework.test.context.ActiveProfilesResolver;

public class DatabaseProfileActiveProfilesResolver implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        return new String[]{DatabaseProfileSupport.selectedProfile()};
    }
}
