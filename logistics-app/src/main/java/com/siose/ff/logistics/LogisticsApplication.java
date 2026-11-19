package com.siose.ff.logistics;

import com.siose.ff.base.app.FfApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FF Logistic as one process. The business areas are shared's domain cores, found by component
 * scanning under {@code com.siose.ff.logistics}; each is a Spring Modulith module. Where one area
 * needs another, the adapter lives here, in the needing area's package, and Spring Modulith checks
 * that it goes through the other area's API only.
 *
 * <p>The same services repository runs each area as a process of its own; the business code is the
 * same jar on both sides.
 */
@SpringBootApplication
public class LogisticsApplication {

    public static void main(String[] args) {
        FfApplication.run(LogisticsApplication.class, args);
    }
}
