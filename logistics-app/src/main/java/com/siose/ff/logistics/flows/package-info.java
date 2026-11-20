/**
 * The flows between the areas, wired in-process: the modulith's half of what the services do over
 * the network. Only here, in the modulith: each area's core knows no other area (decision 2), so the
 * wiring that connects one area's API to another's lives in the application that holds both.
 *
 * <p>Spring Modulith checks that it uses the areas through their APIs only.
 */
@ApplicationModule(displayName = "Flows", allowedDependencies = {"transportation", "warehousing"})
package com.siose.ff.logistics.flows;

import org.springframework.modulith.ApplicationModule;
