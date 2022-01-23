Command line interface to access to a vault encrypted with [Cryptomator](https://github.com/cryptomator/cryptomator)

If you find this project useful, consider donating to [CRYPTOMATOR](https://cryptomator.org/donate/)

## Features

- Access Cryptomator encrypted vaults opened with a Java command line on a platform compatible with [FUSE](https://en.wikipedia.org/wiki/Filesystem_in_Userspace)
- Create a Debian package containing the application and a minimum JRE

## Build

This project requires [Maven](https://maven.apache.org/) and a JDK version 17.

For a smaller package, the build could be done with the community version of Azul, [Zulu](https://www.azul.com/downloads/zulu-community/?version=java-17-lts&os=linux&package=jdk) - around deb 29MB / installed 51MB with Zulu17.32+13-CA (build 17.0.2+8-LTS).

The command `dpkg-deb` is necessary to create the Debian package.

## Usage

The command `/opt/cryfsmount/bin/cryfsmount` can create a new vault or migrate an old vault to the latest format.

Stop the process to close the vault.

If the mount point is not set, a new temporary directory is created.

The passphrase of the vault can be typed, set in a file or in an environment variable.

*WARNING*: when the passphrase is set in a file, make sure there is no trailing end-of-line. For the string `My pass`, create the file with
```
echo -n "My pass" > pass.txt
```

*WARNING*: the command `cryfsumount` stops ALL the running `cryfsmount` processes.

## Troubleshooting

You may experience slowness due to the generation of random numbers. In this case, [haveged](https://github.com/jirka-h/haveged) should be installed (Linux kernel before 5.6).
