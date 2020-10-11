package com.github.llbrt.cryptofs;

import java.nio.file.Path;

import org.cryptomator.cryptofs.CryptoFileSystem;

public interface MountedFs {

	CryptoFileSystem getFs();

	Path getMountPoint();

	void umount();
}
