package org.github.llbrt.cryptofssrv.fuse;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.cryptomator.cryptofs.FileSystemNeedsMigrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.llbrt.cryptofs.MountedFs;
import com.github.llbrt.cryptofs.fuse.FuseCryptoFs;
import com.github.llbrt.cryptofs.fuse.FuseCryptoFs.MountOptions;
import com.google.common.io.BaseEncoding;

public class TestFuseCryptoFs {

	private static final char[] PASSPHRASE = "T€st-Un1t".toCharArray();

	private static final String VAULT_OLDER_FORMAT = "vault-1.4.11";
	private static final String VAULT_CURRENT_FORMAT = "vault-1.5.6";

	private static final String SUM_MD5_FILE = "sum.md5";
	private static final String CREATED_DIR = "newDirectory";
	private static final String CREATED_FILE = "newFile";
	private static final int WRITTEN = 12;

	@TempDir
	public Path tempDirRoot;

	private Path mountPoint;

	@BeforeEach
	public void createMountPoint() throws IOException {
		mountPoint = tempDirRoot.resolve("mountPoint");
		Files.createDirectory(mountPoint);
	}

	@Test
	public void testMountOlderVersion_fails() throws IOException {
		assertThrows(FileSystemNeedsMigrationException.class,
				() -> prepareTestVault(VAULT_OLDER_FORMAT)
						.mount());
	}

	@Test
	public void testMountOlderVersion_migrates() throws IOException {
		Path oldFormatForMigration = copyVault(VAULT_OLDER_FORMAT, "toMigrate");
		MountedFs mounted = prepareTestVault(oldFormatForMigration)
				.migrateFs()
				.mount();
		testFilledMountedFs(mounted, false);
	}

	@Test
	public void testMountOlderVersion_readOnly_fails() throws IOException {
		assertThrows(FileSystemNeedsMigrationException.class,
				() -> prepareTestVault(VAULT_OLDER_FORMAT)
						.readOnly()
						.mount());
	}

	@Test
	public void testMountCurrentVersion() throws IOException {
		Path writableVault = copyVault(VAULT_CURRENT_FORMAT, "writable");
		MountedFs mounted = prepareTestVault(writableVault)
				.mount();
		testFilledMountedFs(mounted, false);
	}

	@Test
	public void testMountCurrentVersion_readOnly_cannotWrite() throws IOException {
		Path notWritableVault = copyVault(VAULT_CURRENT_FORMAT, "not-writable");
		MountedFs mounted = prepareTestVault(notWritableVault)
				.readOnly()
				.mount();
		testFilledMountedFs(mounted, true);
	}

	@Test
	public void testMountCurrentVersion_mountPointUnset() throws IOException {
		Path preMountPoint = mountPoint;
		mountPoint = null;
		Path vault = copyVault(VAULT_CURRENT_FORMAT, "dyn-mount-point");
		MountedFs mounted = prepareTestVault(vault)
				.mount();
		mountPoint = mounted.getMountPoint();
		assertNotEquals(preMountPoint, mountPoint);
		testFilledMountedFs(mounted, false);

		// After umount, the mount point must be empty and can be deleted
		Files.delete(mountPoint);
	}

	@Test
	public void testMountNewVault() throws IOException {
		Path vault = tempDirRoot.resolve("empty-vault");
		Files.createDirectory(vault);
		MountedFs mounted = prepareTestVault(vault)
				.initializeVault()
				.mount();
		testFillEmptyMountedFs(mounted);
		testFilledEmptyMountedFs(FuseCryptoFs.mount(mounted.getFs(), mounted.getMountPoint()));
	}

	@Test
	public void testMountNewVault_notInitialized_fails() throws IOException {
		Path vault = tempDirRoot.resolve("empty-vault");
		Files.createDirectory(vault);
		assertThrows(NoSuchFileException.class, () -> prepareTestVault(vault)
				.mount());
	}

	@Test
	public void testMountCurrentVersion_initialize_fails() throws IOException {
		Path initializedVault = copyVault(VAULT_CURRENT_FORMAT, "initialized");
		assertThrows(FileAlreadyExistsException.class, () -> prepareTestVault(initializedVault)
				.initializeVault()
				.mount());
	}

	private void testFilledMountedFs(MountedFs mounted, boolean expectWriteFailure) throws IOException {
		try {
			checkReferenceSums();

			Path mountPoint = mounted.getMountPoint();
			Path newFile = mountPoint.resolve(CREATED_FILE);
			if (expectWriteFailure) {
				assertThrows(FileSystemException.class, () -> Files.copy(sumFile(), newFile));
			} else {
				Files.copy(sumFile(), newFile);
			}
		} finally {
			mounted.umount();
		}
	}

	private void testFillEmptyMountedFs(MountedFs mounted) throws IOException {
		try {
			Path mountPoint = mounted.getMountPoint();
			// Creates a file
			Path newFile = mountPoint.resolve(CREATED_FILE);
			try (OutputStream os = Files.newOutputStream(newFile, CREATE_NEW)) {
				os.write(WRITTEN);
			}
			// Creates a directory
			Path newDir = mountPoint.resolve(CREATED_DIR);
			Files.createDirectory(newDir);
		} finally {
			mounted.umount();
		}
	}

	private void testFilledEmptyMountedFs(MountedFs mounted) throws IOException {
		try {
			Path mountPoint = mounted.getMountPoint();
			// Created file
			Path createdFile = mountPoint.resolve(CREATED_FILE);
			assertTrue(Files.isRegularFile(createdFile));
			try (InputStream is = Files.newInputStream(createdFile, CREATE_NEW)) {
				assertEquals(WRITTEN, is.read());
			}
			// Created directory
			Path createdDir = mountPoint.resolve(CREATED_DIR);
			assertTrue(Files.isDirectory(createdDir));
		} finally {
			mounted.umount();
		}
	}

	private MountOptions prepareTestVault(String name) {
		return prepareTestVault(getVaultPath(name));
	}

	private MountOptions prepareTestVault(Path vaultDir) {
		return FuseCryptoFs.mountOptions(vaultDir, PASSPHRASE)
				.mountPoint(mountPoint);
	}

	private Path getVaultPath(String name) {
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource(name).getFile());
		assertTrue(file.isDirectory());
		return file.toPath();
	}

	private Map<Path, String> loadSums() throws IOException {
		Map<Path, String> result = new HashMap<>();
		try (Stream<String> stream = Files.lines(sumFile())) {
			stream.forEach(s -> {
				// Line format: <MD5 sum><space><space>./dir1/.../file
				String[] split = s.split("  ");
				result.put(mountPoint.resolve(split[1].substring(2)), split[0]);
			});
		}
		return result;
	}

	private Path sumFile() {
		return mountPoint.resolve(SUM_MD5_FILE);
	}

	private void checkReferenceSums() throws IOException {
		Map<Path, String> sums = loadSums();
		List<Path> remainingFiles = Files.walk(mountPoint)
				.filter(p -> p.toFile().isFile())
				.filter(p -> md5SumMatches(p, sums))
				.collect(Collectors.toList());

		// Only the sum file should remain
		assertEquals(1, remainingFiles.size());
		assertEquals(sumFile(), remainingFiles.get(0));
	}

	private boolean md5SumMatches(Path path, Map<Path, String> sums) {
		try {
			String sum = sums.remove(path);
			if (sum == null) {
				return true;
			}
			MessageDigest md = MessageDigest.getInstance("MD5");
			try (InputStream is = Files.newInputStream(path);
					DigestInputStream dis = new DigestInputStream(is, md)) {
				dis.readAllBytes();
			}
			assertEquals(sum, BaseEncoding.base16().encode(md.digest()).toLowerCase(), path.toString());
			return false;
		} catch (Exception e) {
			fail(e);
			return true;
		}
	}

	private Path copyVault(String source, String dirName) throws IOException {
		Path oldFormatForMigration = tempDirRoot.resolve(dirName);

		Path root = getVaultPath(source);
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectory(toCreate(dir));
				return CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, toCreate(file));
				return CONTINUE;
			}

			private Path toCreate(Path path) {
				return oldFormatForMigration.resolve(root.relativize(path));
			}
		});

		return oldFormatForMigration;
	}
}
