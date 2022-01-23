package com.github.llbrt.utils.logs;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;

/**
 * Declares a new converter to add the process id in the logs.
 */
public final class ExtendedPatternLayoutEncoder extends PatternLayoutEncoder {
	@Override
	public void start() {
		// Declare new converter
		PatternLayout.DEFAULT_CONVERTER_MAP.put(
				"processId", ProcessIdConverter.class.getName());
		super.start();
	}
}
