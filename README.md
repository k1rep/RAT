# RAT-replication-package

This repository contains the source code that we used to perform the experiment in the paper titled "RAT: A Refactoring-Aware Traceability Model for Bug Localization.". The author has made the source code publicly available and it can be found at <https://github.com/feifeiniu-se/RAT_Demo>.

Using the authors' original approach, we applyed the eight reconstruction types mentioned by the authors in their paper and ran them on the following project dataset. Please follow the steps below to reproduce the result.

## Dataset

The following projects are all open source and can be obtained from github as code repositories.

| Project                 | Abb. | Time Span             |
| ----------------------- | ---- | --------------------- |
| JBossTransactionManager | JT   | 2012-08-20~2023-01-09 |
| WildFlyCore             | WC   | 2012-03-24~2023-07-11 |
| Debezium                | DE   | 2016-03-02~2022-05-06 |
| Weld                    | WE   | 2011-11-09~2015-04-13 |
| Undertow                | UN   | 2013-04-09~2023-06-08 |
| Teiid                   | TE   | 2012-10-10~2018-03-20 |
| Wildfly                 | WI   | 2011-10-11~2023-05-31 |
| ModeShape               | MO   | 2009-08-04~2017-04-14 |
| Infinispan              | IN   | 2011-05-09~2020-10-03 |
| WildFlyElytron          | WL   | 2014-08-13~2023-06-06 |
| HAL                     | HA   | 2016-07-07~2022-02-22 |
| JGroups                 | JG   | 2011-03-20~2023-06-29 |
| RESTEasy                | RE   | 2012-05-11~2023-06-08 |

## Environment Requirements

Java Development Kit >= 17

Maven == 3.9.6

## Refactoring Type

| Refactory Type        | Code Change Type |
| --------------------- | ---------------- |
| Rename Package        | rename           |
| Move Package          | rename           |
| Split Package         | derive           |
| Merge Package         | derive           |
| Rename Class          | rename           |
| Move Class            | rename           |
| Move and Rename Class | rename           |
| Move Source Folder    | rename           |

## Experiment Result Replication Guide

According to the author's description of use：RAT is command line tool so far, it supports three types of commands:

```
> -a <git-repo-folder> -s <sqlite-file-path> # detects all code history

> -bc <git-repo-folder> <start-commit-sha1> <end-commit-sha1> -s <sqlite-file-path> # detects code history between start commit and end commit

> -c <git-repo-folder> <commit-sha1> -s <sqlite-file-path> #detects code history between last commit and this commit

```

`<git-repo-folder>`defines the path of the local repository, `<sqlite-file-path>` indicates the path for saving the SQLite database.

When packaged with maven(`mvn clean`and `mvn install`)，you got a Java Jar package.

Before you run the RAT on a git-repo project, you should create a `<project-name>.sqlite3`file and execute the sql file in the `src/main/resources` folder named `createSqliteTable.sql`.

Then you can run the example like:

```sh
java -jar TraceabilityModel-1.0-SNAPSHOT-jar-with-dependencies.jar -a /wildfly -s /wildfly.sqlite3

```


