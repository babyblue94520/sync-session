package pers.clare.session;

import java.time.Duration;
import java.util.function.BooleanSupplier;

final class TestWait {

    private TestWait() {
    }

    static boolean until(Duration timeout, Duration interval, BooleanSupplier supplier) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (supplier.getAsBoolean()) {
                return true;
            }
            Thread.sleep(interval.toMillis());
        }
        return supplier.getAsBoolean();
    }
}
