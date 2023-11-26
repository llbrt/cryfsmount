package com.github.llbrt.cryptofs.fuse;

import static java.lang.Math.min;

import static org.cryptomator.cryptofs.CryptoFileSystemProperties.FileSystemFlags.READONLY;
import static org.cryptomator.cryptofs.migration.api.MigrationContinuationListener.ContinuationResult.PROCEED;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.cryptomator.cryptofs.CryptoFileSystem;
import org.cryptomator.cryptofs.CryptoFileSystemProperties;
import org.cryptomator.cryptofs.CryptoFileSystemProperties.FileSystemFlags;
import org.cryptomator.cryptofs.CryptoFileSystemProvider;
import org.cryptomator.cryptofs.migration.Migrators;
import org.cryptomator.cryptofs.migration.api.MigrationContinuationListener.ContinuationEvent;
import org.cryptomator.cryptofs.migration.api.MigrationContinuationListener.ContinuationResult;
import org.cryptomator.cryptofs.migration.api.MigrationProgressListener.ProgressState;
import org.cryptomator.cryptolib.api.CryptoException;
import org.cryptomator.cryptolib.api.CryptorProvider;
import org.cryptomator.cryptolib.api.Masterkey;
import org.cryptomator.cryptolib.api.MasterkeyLoader;
import org.cryptomator.cryptolib.common.MasterkeyFileAccess;
import org.cryptomator.integrations.mount.Mount;
import org.cryptomator.integrations.mount.MountFailedException;
import org.cryptomator.integrations.mount.MountService;
import org.cryptomator.integrations.mount.UnmountFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.llbrt.cryptofs.MountedFs;
import com.google.common.base.Preconditions;

public final class FuseCryptoFs implements MountedFs {
	private static final Logger log = LoggerFactory.getLogger(FuseCryptoFs.class);

	private static final FileSystemFlags[] EMPTY_FLAGS = new FileSystemFlags[0];

	private static final byte[] EMPTY_ARRAY = new byte[0];
	private static final String SCHEME = "masterkeyfile";

	private static final String VAULTCONFIG_FILENAME = "vault.cryptomator";

	private static final String MASTERKEY_FILENAME = "masterkey.cryptomator";
	private static final URI KEY_ID = URI.create(SCHEME + ":" + MASTERKEY_FILENAME);

	// Wait at most 30 seconds
	private static final int UMOUNT_COUNT_MAX = 60;
	private static final long UMOUNT_RETRY_WAIT = 500;

	// Master key file management
	private static final SecureRandom secureRandom;
	private static final MasterkeyFileAccess masterkeyFileAccess;

	static {
		SecureRandom secureRandomTmp;
		try {
			secureRandomTmp = SecureRandom.getInstanceStrong();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("A strong algorithm should be installed", e);
		}
		secureRandom = secureRandomTmp;
		masterkeyFileAccess = new MasterkeyFileAccess(EMPTY_ARRAY, secureRandom);
	}

	private final CryptoFileSystem fs;
	private final Mount mount;
	private final Path mountPoint;

	private FuseCryptoFs(CryptoFileSystem fs, Mount mount, Path mountPoint) {
		this.fs = fs;
		this.mount = mount;
		this.mountPoint = mountPoint;
	}

	@Override
	public CryptoFileSystem getFs() {
		return fs;
	}

	@Override
	public Path getMountPoint() {
		return mountPoint;
	}

	@Override
	public void umount() {
		try {
			attemptUmount();
		} catch (Exception e) {
			try {
				log.warn("umount failed, try to force umount", e);
				mount.unmountForced();
			} catch (Exception e1) {
				log.error("Force umount failed", e);
			}
		} finally {
			try {
				mount.close();
			} catch (Exception e) {
				log.warn("close failed", e);
			}
		}
		log.info("umount done");
	}

	/**
	 * Tries to unmount the file system.
	 */
	private void attemptUmount() throws InterruptedException, UnmountFailedException {
		for (int c = 0; c <= UMOUNT_COUNT_MAX; c++) {
			try {
				log.info("umount attempt");
				mount.unmount();
				return;
			} catch (UnmountFailedException e) {
				if (c >= UMOUNT_COUNT_MAX) {
					throw e;
				}
				log.warn("umount failed (retry)", e);
				Thread.sleep(UMOUNT_RETRY_WAIT);
			}
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(fs.getPathToVault());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof FuseCryptoFs)) {
			return false;
		}
		FuseCryptoFs other = (FuseCryptoFs) obj;
		return Objects.equals(fs, other.fs);
	}

	@Override
	public String toString() {
		return fs.toString();
	}

	public static MountedFs mount(CryptoFileSystem fs, Path mountPoint) {
		try {
			var mountService = MountService.get().findAny().orElseThrow(() -> new MountFailedException("No mount provider found: fuse3 is required"));
			var rootPath = fs.getRootDirectories().iterator().next();
			var builder = mountService.forFileSystem(rootPath)
					.setMountpoint(mountPoint)
					.setMountFlags(mountService.getDefaultMountFlags());
			return new FuseCryptoFs(fs, builder.mount(), mountPoint);
		} catch (MountFailedException e) {
			throw new RuntimeException(e);
		}
	}

	public static MountOptions mountOptions(Path vaultDir, char[] vaultPassphrase) {
		return new MountOptions(vaultDir, new String(vaultPassphrase));
	}

	public static final class MountOptions {
		private final Path vaultDir;
		private final String passphrase;
		private Path mountPoint;
		private boolean initializeVault;
		private boolean migrateFs;
		private boolean readOnly;

		MountOptions(Path vaultDir, String passphrase) {
			this.vaultDir = vaultDir;
			this.passphrase = passphrase;
		}

		public final MountOptions mountPoint(Path mountPoint) {
			this.mountPoint = mountPoint;
			return this;
		}

		public final MountOptions initializeVault() {
			this.initializeVault = true;
			return this;
		}

		public final MountOptions migrateFs() {
			this.migrateFs = true;
			return this;
		}

		public final MountOptions readOnly() {
			this.readOnly = true;
			return this;
		}

		public final MountedFs mount() throws IOException {
			if (migrateFs) {
				do {
					log.info("Migration requested");
				} while (migrate());
				log.info("Migration done");
			}
			if (mountPoint == null) {
				mountPoint = Files.createTempDirectory("cryfsmount-");
				log.info("Mount point created: " + mountPoint);
			} else {
				log.info("Mount point: " + mountPoint);
			}
			if (initializeVault) {
				initializeNewVault();
			}

			List<FileSystemFlags> flags = new ArrayList<>();
			if (readOnly) {
				flags.add(READONLY);
			}
			CryptoFileSystemProperties cryptoFileSystemProperties = CryptoFileSystemProperties
					.cryptoFileSystemProperties()
					.withKeyLoader(keyId -> {
						Preconditions.checkArgument(SCHEME.equalsIgnoreCase(keyId.getScheme()), "Only supports keys with scheme " + SCHEME);
						Path keyFilePath = vaultDir.resolve(keyId.getSchemeSpecificPart());
						return masterkeyFileAccess.load(keyFilePath, passphrase);
					})
					.withFlags(flags.toArray(EMPTY_FLAGS))
					.build();

			CryptoFileSystem fs = CryptoFileSystemProvider.newFileSystem(vaultDir, cryptoFileSystemProperties);
			return FuseCryptoFs.mount(fs, mountPoint);
		}

		private void initializeNewVault() throws IOException {
			Files.createDirectories(vaultDir);

			// Write masterkey
			Path masterkeyFilePath = vaultDir.resolve(MASTERKEY_FILENAME);
			try (Masterkey masterkey = Masterkey.generate(secureRandom)) {
				masterkeyFileAccess.persist(masterkey, masterkeyFilePath, passphrase);

				// Initialize vault
				try {
					MasterkeyLoader loader = ignored -> masterkey.copy();
					CryptoFileSystemProperties fsProps = CryptoFileSystemProperties.cryptoFileSystemProperties()
							.withCipherCombo(CryptorProvider.Scheme.SIV_CTRMAC)
							.withKeyLoader(loader)
							.build();
					CryptoFileSystemProvider.initialize(vaultDir, fsProps, KEY_ID);
				} catch (CryptoException e) {
					throw new IOException("Failed initialize vault.", e);
				}
			}
		}

		private boolean migrate() throws IOException {
			Migrators migrators = Migrators.get();
			if (!migrators.needsMigration(vaultDir, VAULTCONFIG_FILENAME, MASTERKEY_FILENAME)) {
				return false;
			}
			migrators.migrate(vaultDir, VAULTCONFIG_FILENAME, MASTERKEY_FILENAME, passphrase, this::migrationProgressChanged, this::migrationRequiresInput);
			return migrators.needsMigration(vaultDir, VAULTCONFIG_FILENAME, MASTERKEY_FILENAME);
		}

		private void migrationProgressChanged(ProgressState state, double progress) {
			log.info("Migration {}: {}%", state, min(100, (int) (progress * 100)));
		}

		private ContinuationResult migrationRequiresInput(ContinuationEvent continuationEvent) {
			log.info("Migration {} necessary", continuationEvent);
			return PROCEED;
		}
	}
}
