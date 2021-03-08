# depcheck-maven-plugin

[![Build Status](https://img.shields.io/github/workflow/status/vegardit/depcheck-maven-plugin/Build)](https://github.com/vegardit/depcheck-maven-plugin/actions?query=workflow%3A%22Build%22)
[![License](https://img.shields.io/github/license/vegardit/depcheck-maven-plugin.svg?color=blue)](LICENSE.txt)
[![Maintainability](https://api.codeclimate.com/v1/badges/e11923f8b5b6db961f75/maintainability)](https://codeclimate.com/github/vegardit/depcheck-maven-plugin/maintainability)
[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-v2.0%20adopted-ff69b4.svg)](CODE_OF_CONDUCT.md)
[![Maven Central](https://img.shields.io/maven-central/v/com.vegardit.maven/depcheck-maven-plugin)](https://search.maven.org/artifact/com.vegardit.maven/depcheck-maven-plugin)

1. [What is it?](#what-is-it)
1. [Usage](#usage)
1. [License](#license)


## <a name="what-is-it"></a>What is it?

Pragmatic [Maven](https://maven.apache.org) plugin to check for unused direct and used indirect (transitive) dependencies.


## <a name="usage"></a>Usage

### Binaries

**Release** binaries of this project are available at https://search.maven.org/artifact/com.vegardit.maven/depcheck-maven-plugin

**Snapshot** binaries are available via the [mvn-snapshots-repo](https://github.com/vegardit/depcheck-maven-plugin/tree/mvn-snapshots-repo) git branch. You need to add this repository configuration to your Maven `settings.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<settings>
  <profiles>
    <profile>
      <repositories>
        <repository>
          <id>central</id>
          <name>depcheck-maven-plugin-snapshots</name>
          <url>https://raw.githubusercontent.com/vegardit/depcheck-maven-plugin/mvn-snapshots-repo</url>
          <releases><enabled>false</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>depcheck-maven-plugin-snapshots</activeProfile>
  </activeProfiles>
</settings>
```

### Execution via the command line

To execute the latest release of this plugin via the command line, first change into your maven project directory and then run:

```sh
mvn com.vegardit.maven:depcheck-maven-plugin:check-deps
```


## <a name="license"></a>License

All files are released under the [Apache License 2.0](LICENSE.txt).

Individual files contain the following tag instead of the full license text:
```
SPDX-License-Identifier: Apache-2.0
```

This enables machine processing of license information based on the SPDX License Identifiers that are available here: https://spdx.org/licenses/.
