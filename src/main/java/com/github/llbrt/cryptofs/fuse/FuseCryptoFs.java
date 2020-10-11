package com.github.llbrt.cryptofs.fuse;

import static org.cryptomator.cryptofs.CryptoFileSystemProperties.FileSystemFlags.MIGRATE_IMPLICITLY;
import static org.cryptomator.cryptofs.CryptoFileSystemProperties.FileSystemFlags.READONLY;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.cryptomator.cryptofs.CryptoFileSystem;
import org.cryptomator.cryptofs.CryptoFileSystemProperties;
import org.cryptomator.cryptofs.CryptoFileSystemProperties.FileSystemFlags;
import org.cryptomator.cryptofs.CryptoFileSystemProvider;
import org.cryptomator.frontend.fuse.mount.CommandFailedException;
import org.cryptomator.frontend.fuse.mount.EnvironmentVariables;
import org.cryptomator.frontend.fuse.mount.FuseMountFactory;
import org.cryptomator.frontend.fuse.mount.Mount;
import org.cryptomator.frontend.fuse.mount.Mounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.llbrt.cryptofs.MountedFs;

public final class FuseCryptoFs implements MountedFs {
	private static final Logger log = LoggerFactory.getLogger(FuseCryptoFs.class);

	private static final FileSystemFlags[] EMPTY_FLAGS = new FileSystemFlags[0];

	private static final String DEFAULT_MASTERKEY_FILENAME = "masterkey.cryptomator";

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
			mount.unmount();
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
		Mounter mounter = FuseMountFactory.getMounter();
		EnvironmentVariables envVars = EnvironmentVariables.create()
				.withFlags(mounter.defaultMountFlags())
				.withMountPoint(mountPoint)
				.build();
		try {
			return new FuseCryptoFs(fs, mounter.mount(fs.getPath("/"), envVars), mountPoint);
		} catch (CommandFailedException e) {
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
			List<FileSystemFlags> flags = new ArrayList<>();
			if (migrateFs) {
				flags.add(MIGRATE_IMPLICITLY);
			}
			if (readOnly) {
				flags.add(READONLY);
			}
			CryptoFileSystemProperties cryptoFileSystemProperties = CryptoFileSystemProperties
					.withPassphrase(passphrase)
					.withFlags(flags.toArray(EMPTY_FLAGS))
					.build();

			if (mountPoint == null) {
				mountPoint = Files.createTempDirectory("cryfsmount-");
				log.info("Mount point created: " + mountPoint);
			} else {
				log.info("Mount point: " + mountPoint);
			}
			if (initializeVault) {
				Files.createDirectories(vaultDir);
				CryptoFileSystemProvider.initialize(vaultDir, DEFAULT_MASTERKEY_FILENAME, passphrase);
			}
			CryptoFileSystem fs = CryptoFileSystemProvider.newFileSystem(vaultDir, cryptoFileSystemProperties);
			return FuseCryptoFs.mount(fs, mountPoint);
		}
	}
}
