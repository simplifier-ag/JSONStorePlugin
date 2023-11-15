
CREATE TABLE `${prefix}Json_Store_Collection` (
    `id`   VARCHAR(128) NOT NULL,
    `name` LONGTEXT     NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

CREATE TABLE `${prefix}Json_Store_Value` (
    `id`            VARCHAR(128) NOT NULL,
    `key_text`      LONGTEXT     NOT NULL,
    `data`          LONGBLOB     NOT NULL,
    `collection_id` VARCHAR(128) NOT NULL,

    PRIMARY KEY (`id`),

    CONSTRAINT `Json_Value_To_Collection` FOREIGN KEY (`collection_id`)
        REFERENCES `${prefix}Json_Store_Collection` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;