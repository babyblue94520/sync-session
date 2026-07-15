package pers.clare.session;

final class DatabaseProfileSupport {

    private static final String DEFAULT_PROFILE = "h2";

    private DatabaseProfileSupport() {
    }

    static String selectedProfile() {
        String activeProfiles = System.getProperty("spring.profiles.active");
        if (hasText(activeProfiles)) {
            return firstProfile(activeProfiles);
        }

        String profile = System.getProperty("test.profile");
        if (hasText(profile)) {
            return profile;
        }

        String database = System.getProperty("test.database");
        if (!hasText(database)) {
            return DEFAULT_PROFILE;
        }

        return switch (database.toLowerCase()) {
            case "h2" -> "h2";
            case "mysql" -> "mysql";
            case "postgresql", "postgres" -> "postgresql";
            default -> throw new IllegalArgumentException("Unsupported test.database: " + database);
        };
    }

    private static String firstProfile(String activeProfiles) {
        String[] values = activeProfiles.split(",");
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return DEFAULT_PROFILE;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank() && !value.startsWith("${");
    }
}
