Command line interface to access to a vault encrypted with [Cryptomator](https://github.com/cryptomator/cryptomator)

If you find this project useful, consider donating to [CRYPTOMATOR](https://cryptomator.org/donate/)

## Features

- Access Cryptomator encrypted vaults opened with a Java command line on a platform compatible with [FUSE](https://en.wikipedia.org/wiki/Filesystem_in_Userspace)
- Create a Debian package containing the application and a minimum JRE

## Build

This project requires [Maven](https://maven.apache.org/) 3.9.5 (or more) and a JDK version 21.

The created package may be smaller if the build is made with the community version of Azul, [Zulu](https://www.azul.com/downloads/zulu-community/?version=java-21-lts&os=linux&package=jdk).

The command `dpkg-deb` is necessary to create the Debian package.

## Usage

The command `/opt/cryfsmount/bin/cryfsmount` can create a new vault or migrate an old vault to the latest format.

Stop the process to close the vault.

*Note*: fuse v3 is required.

If the mount point is not set, a new temporary directory is created.

The passphrase of the vault can be typed, set in a file or in an environment variable.

*WARNING*: when the passphrase is set in a file, make sure there is no trailing end-of-line. For the string `My pass`, create the file with
```
echo -n "My pass" > pass.txt
```

*WARNING*: the command `cryfsumount` stops ALL the running `cryfsmount` processes.

## Troubleshooting

You may experience slowness due to the generation of random numbers. In this case, [haveged](https://github.com/jirka-h/haveged) should be installed (Linux kernel before 5.6).
