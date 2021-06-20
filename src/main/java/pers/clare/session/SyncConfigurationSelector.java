package pers.clare.session;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

public class SyncConfigurationSelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
//        return new String[]{"pers.clare.session.SyncSessionConfiguration"};
        return new String[]{};
    }
}
