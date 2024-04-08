
CREATE TABLE ${prefix}JSON_STORE_COLLECTION (
    ID   VARCHAR2(128) NOT NULL PRIMARY KEY,
    NAME CLOB          NOT NULL
);

CREATE TABLE ${prefix}JSON_STORE_VALUE (
    ID            VARCHAR2(128) NOT NULL PRIMARY KEY,
    KEY_TEXT      CLOB          NOT NULL,
    DATA          BLOB          NOT NULL,
    COLLECTION_ID VARCHAR2(128) NOT NULL,

    CONSTRAINT JSON_VALUE_TO_COLLECTION FOREIGN KEY (COLLECTION_ID)
        REFERENCES ${prefix}JSON_STORE_COLLECTION (ID) ON DELETE CASCADE
);