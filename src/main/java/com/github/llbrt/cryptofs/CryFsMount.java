package com.github.llbrt.cryptofs;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;

public final class CryFsMount {

	private static final Logger logger = LoggerFactory.getLogger(CryFsMount.class);

	public static void main(String[] args) {
		CommandLine cmd = new CommandLine(new Mount());
		int exitCode = cmd.execute(args);
		if (exitCode != 0) {
			System.exit(exitCode);
		}
		MountedFs mountedFs = cmd.getExecutionResult();
		String message = mountedFs + " mounted on " + mountedFs.getMountPoint();
		logger.info(message);
		System.out.println(message);

		// Prepare umount
		Runtime.getRuntime().addShutdownHook(new Thread(() -> mountedFs.umount(), "Umounter"));

		// Wait for kill in a separate thread
		new Thread(() -> {
			while (true) {
				try {
					TimeUnit.MINUTES.sleep(4);
				} catch (InterruptedException e) {
					// ignored
				}
			}
		}, "Keep alive thread").start();

		try {
			System.in.close();
		} catch (Exception e) {
			logger.warn("Failed to close input stream", e);
		}
		System.out.close();
		System.err.close();
	}
}
