# Sync Session

## Overview

SyncSession is an architecture designed for distributed systems. Session data is stored in RDBS. It sends events to
synchronize session status in all services.

Regularly update lastAccessTime to reduce the load of RDBS and improve concurrent processing capabilities

## Requirement

* Spring Boot 2.3+
* Java 8+
* Database (support MySQL and H2)

## QuickStart

1. **Config**

    ```java
    @EnableSyncSession
    public class SessionConfig {

    }
    ```

   **Distributed System**

   If your infrastructure is a distributed system. You must implement SyncSessionEventService

    ```java
    /**
    * Cases of using redis.
    */
    @Service
    public class SyncSessionEventServiceImpl extends SyncSessionEventService implements InitializingBean {
        @Autowired
        private StringRedisTemplate stringRedisTemplate;

        @Autowired
        private RedisMessageListenerContainer listenerContainer;

        @Autowired
        private DefaultClientResources defaultClientResources;

        private final String topic = "session";
   
        @Override
        public void afterPropertiesSet() {
            defaultClientResources.eventBus().get().subscribe((event) -> {
                if (event instanceof ConnectedEvent) {
                    publishConnectedEvent();
                }
            });
        }

        @Override
        public void send(String body) {
            stringRedisTemplate.convertAndSend(body);
        }

        @Override
        public void addListener(Consumer<String> listener) {
            listenerContainer.addMessageListener((message, pattern) -> {
                listener.accept(new String(message.getBody()));
            }, new PatternTopic(topic));
        }
    }
    ```

2. **Use**

    * **Session operation**

      RequestCacheHolder can be used anywhere.

        ```java
        public class CustomFilter implements Filter {
        
            @Override
            public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
                RequestCache<SyncSession> requestCache = RequestCacheHolder.get();
                // get session
                SyncSession syncSession = requestCache.getSession();
                // auto create session
                SyncSession syncSession = requestCache.getSession(true);
                // invalidate session
                syncSession.invalidate();
            }
        }
        ```

    * **Filter**

        ```java
        public class CustomFilter implements Filter {
        
            @Override
            public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
                RequestCache<CustomSession> requestCache = RequestCacheHolder.get(CustomSession.class);
                // get session
                SyncSession syncSession = requestCache.getSession();
                syncSession.setCsrfToken("{token}");
            }
        }
        ```

## Advanced

### **Session properties**

```yaml
sync-session:
  cookie-name: SSESSIONID
  timeout: 30m
```

### **Session invalidate listener**

Under the distributed service architecture, the session will be invalidated by any service, so through **InvalidateBy**,
it is recognized whether the current service is invalidated or the notification from another service.

* InvalidateBy

    * **SELF**: Indicates that the session is invalidated by the current service.

    * **NOTICE**: Indicates that the session is invalidated by another service.

```java

@EnableSyncSession
public class SessionConfig implements InitializingBean {
    @Autowired
    private SyncSessionService<?> syncSessionServie;

    @Override
    public void afterPropertiesSet() throws Exception {
        syncSessionServie.addInvalidateListeners((id, name, type) -> {
            log.info("{} {} {}", id, name, type);
        });
    }
}
```

### **Set custom attribute**

1. **Create Custom Session**

    ```java
    public class UserSession extends SyncSession {
        private String csrfToken;
    
        public String getCsrfToken() {
            return csrfToken;
        }
    
        public void setCsrfToken(String csrfToken) {
            this.csrfToken = csrfToken;
            // The mask data has changed
            this.save();
        }
    }
    ```

2. **Extends SyncSessionServiceImpl**

    ```java
    public class CustomSessionService extends SyncSessionServiceImpl<CustomSession> {
        public UserSessionService(
            SyncSessionProperties properties
            , DataSource dataSource
            , @Nullable SyncSessionEventService sessionEventService
        ) {
            super(properties, dataSource, sessionEventService);
        }
    }
    ```

### **Invalidate by username**

Sometime need to invalidate all sessions by username.

```java
public class UserSession {
    @Autowired
    private SyncSessionService syncSessionService;

    public void kick(String username) {
        syncSessionService.invalidateByUsername(username);
    }

    public void kickOther(String username) {
        String[] excludeSessionIds = {"...."};
        syncSessionService.invalidateByUsername(username, excludeSessionIds);
    }
}
```

### **Manage other system sessions**

Manage other system sessions by central management system.

1. **Create SyncSessionOperatorService**

   ```java
   @Configuration
   public class OtherSessionConfig {
   
       @Bean
       @Autowired
       public SyncSessionOperatorService<SyncSession> otherSyncSessionOperatorService(
              DataSource dataSource // other session storage
               , SyncSessionEventService sessionEventService
       ) {
           return new SyncSessionOperatorService<>(new SyncSessionProperties(), dataSource, sessionEventService);
       }
   }
   ```

2. **Use SyncSessionOperatorService**

   ```java
   public class OtherUserService{
        @Autowired
        private SyncSessionOperatorService<SyncSession> otherSyncSessionOperatorService;
   
        public void kick(String username){
            otherSyncSessionOperatorService.invalidateByUsername(username);
        }
   }
   ```
