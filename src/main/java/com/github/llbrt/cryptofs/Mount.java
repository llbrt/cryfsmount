package com.github.llbrt.cryptofs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.github.llbrt.cryptofs.fuse.FuseCryptoFs;
import com.github.llbrt.cryptofs.fuse.FuseCryptoFs.MountOptions;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "cryfsmount")
public final class Mount implements Callable<MountedFs> {

	@ArgGroup(exclusive = true, multiplicity = "1")
	private Passphrase passphrase;

	static class Passphrase {
		@Option(names = { "--passphrase:file" }, description = "Path of the file containing the passphrase")
		private Path passphrasePath;

		@Option(names = { "--passphrase:env" }, description = "Name of the environment variable containing the passphrase")
		private String passphraseEnvironmentVariable;

		@Option(names = { "-p", "--passphrase" }, interactive = true)
		private char[] passphrase;
	}

	@Option(names = { "-c", "--create", "--initialize" }, description = "Creates a new vault")
	private boolean initializeVault;
	@Option(names = { "-m" }, description = "Migrates the vault if necessary")
	private boolean migrateFs;
	@Option(names = { "-r", "--read-only" }, description = "Mounts the vault read-only")
	private boolean readOnly;

	@Parameters(index = "0", description = "Path to the vault")
	private Path vaultDir;

	@Parameters(index = "1", arity = "0..1", description = "Mount point to access to the vault")
	private Path mountPoint;

	@Spec
	private CommandSpec spec;

	@Override
	public MountedFs call() throws Exception {
		char[] vaultPassphrase;
		if (passphrase.passphrase != null) {
			vaultPassphrase = passphrase.passphrase;
		} else if (passphrase.passphraseEnvironmentVariable != null) {
			String vaultPass = System.getenv(passphrase.passphraseEnvironmentVariable);
			vaultPassphrase = vaultPass.toCharArray();
		} else if (passphrase.passphrasePath != null) {
			String vaultPass = new String(Files.readAllBytes(passphrase.passphrasePath));
			vaultPassphrase = vaultPass.toCharArray();
		} else {
			throw new ParameterException(spec.commandLine(), "Password required");
		}

		// Vault must exist and not be empty except on initialization
		checkIsDirectory(vaultDir, "Vauld directory", initializeVault, initializeVault);

		// If set, the mount point must exist and be empty
		if (mountPoint != null) {
			checkIsDirectory(mountPoint, "Mount point", false, true);
		}

		MountOptions mo = FuseCryptoFs.mountOptions(vaultDir, vaultPassphrase)
				.mountPoint(mountPoint);
		if (initializeVault)
			mo.initializeVault();
		if (migrateFs)
			mo.migrateFs();
		if (readOnly)
			mo.readOnly();

		return mo.mount();
	}

	private final void checkIsDirectory(Path path, String name, boolean mayNotExist, boolean mustBeEmpty) {
		var pathFile = path.toFile();
		if (!pathFile.exists()) {
			if (mayNotExist) {
				// Done
				return;
			}
			throw new ParameterException(spec.commandLine(), name + " not found");
		}
		if (!pathFile.isDirectory()) {
			throw new ParameterException(spec.commandLine(), name + " is not a directory");
		}
		if (mustBeEmpty && pathFile.list().length > 0) {
			throw new ParameterException(spec.commandLine(), name + " is not empty");
		}
	}
}
