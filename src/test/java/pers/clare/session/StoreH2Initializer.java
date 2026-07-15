package pers.clare.session;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

final class StoreH2Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        if (!isH2(applicationContext)) {
            return;
        }

        int h2Port = TestPorts.freePort();
        String dbName = "store_h2_contract";
        ConfigurableApplicationContext h2Context = TestH2Support.start(h2Port, "REGULAR", dbName);
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "h2.port=" + h2Port,
                "h2.mode=REGULAR",
                "h2.dbName=" + dbName
        );
        applicationContext.getBeanFactory().registerSingleton(
                "storeContractH2Server",
                (DisposableBean) h2Context::close
        );
    }

    private boolean isH2(ConfigurableApplicationContext applicationContext) {
        for (String profile : applicationContext.getEnvironment().getActiveProfiles()) {
            if ("h2".equals(profile)) {
                return true;
            }
        }
        return false;
    }
}
