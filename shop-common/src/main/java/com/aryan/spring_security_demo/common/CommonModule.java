package com.aryan.spring_security_demo.common;

/**
 * Shared infrastructure every module relies on: cross-cutting config
 * (caching, scheduling, AOP, clock, OpenAPI), the logging/audit aspects, the global
 * error handler, response wrappers, validation, and startup diagnostics.
 * Scans only {@code com.aryan.spring_security_demo.common}; see {@link ModuleConfiguration}.
 */
@ModuleConfiguration
public class CommonModule {
}
