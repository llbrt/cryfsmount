package com.github.llbrt.utils.logs;

import java.lang.management.ManagementFactory;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Converts the process id pattern declares by {@link ExtendedPatternLayoutEncoder}.
 */
public final class ProcessIdConverter extends ClassicConverter {
	private static final String PROCESS_ID = Long.toString(ManagementFactory.getRuntimeMXBean().getPid());

	@Override
	public String convert(final ILoggingEvent event) {
		// for every logging event return processId from mx bean
		// (or better alternative)
		return PROCESS_ID;
	}
}
