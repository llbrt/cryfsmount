Command line interface to access to a vault encrypted with [Cryptomator](https://github.com/cryptomator/cryptomator)

If you find this project useful, consider donating to [CRYPTOMATOR](https://cryptomator.org/donate/)

## Features

- Access Cryptomator encrypted vaults opened with a Java command line on a platform compatible with [FUSE](https://en.wikipedia.org/wiki/Filesystem_in_Userspace)
- Create a Debian package containing the application and a minimum JRE

## Build

This project requires [Maven](https://maven.apache.org/) and a JDK version 11.

For a smaller package, the build should be done with the community version of Azul, [Zulu](https://www.azul.com/downloads/zulu-community/?version=java-11-lts&os=linux&package=jdk) (around deb 27MB / installed 47MB with Zulu11.39+15-CA (build 11.0.7+10-LTS), but deb 87MB/ installed 322MB with 11.0.8+10-post-Ubuntu-0ubuntu118.04.1).

The command `dpkg-deb` is necessary to create the Debian package.

## Usage

The command `/opt/cryfsmount/bin/cryfsmount` can create a new vault or migrate an old vault to the latest format.

Close the vault be stopping the process.

If the mount point is not set, a new temporary directory is created.

The passphrase of the vault can be typed, set in a file or in an environment variable.

*WARNING*: when the passphrase is set in a file, make sure there is no trailing end-of-line. For the string `My pass`, create the file with
```
echo -n "My pass" > pass.txt
```

*WARNING*: the command `cryfsumount` stops ALL the running `cryfsmount` processes.
