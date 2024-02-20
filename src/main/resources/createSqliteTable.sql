DROP TABLE IF EXISTS CodeBlock;
CREATE TABLE CodeBlock (
    id INTEGER,
    type TEXT
);

DROP TABLE IF EXISTS CodeBlockTime;
CREATE TABLE CodeBlockTime (
    name TEXT,
    commitId TEXT,
    refactorType TEXT,
    parentCodeBlock INTEGER,
    owner INTEGER,
    parameters TEXT,
    oldStartLineNum INTEGER,
    oldEndLineNum INTEGER,
    newStartLineNum INTEGER,
    newEndLineNum INTEGER
);

DROP TABLE IF EXISTS CodeBlockTime_link;
CREATE TABLE CodeBlockTime_link (
    source INTEGER,
    target INTEGER,
    link_type INTEGER
);

DROP TABLE IF EXISTS CodeBlockTimeChild;
CREATE TABLE CodeBlockTimeChild (
    codeBlockTimeId INTEGER,
    codeBlockChildId INTEGER,
    codeBlockChildType TEXT
);

DROP TABLE IF EXISTS CommitCodeChange;
CREATE TABLE CommitCodeChange (
    commitID TEXT,
    preCommitID TEXT
, postCommitID TEXT);

DROP TABLE IF EXISTS Mapping;
CREATE TABLE Mapping (
    codeBlockName TEXT,
    codeBlockID INTEGER
);

DROP TABLE IF EXISTS CommitMessage;
CREATE TABLE CommitMessage (
    message TEXT,
    author TEXT,
    time TEXT
);