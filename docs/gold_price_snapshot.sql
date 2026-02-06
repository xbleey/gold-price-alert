CREATE TABLE `gold_price_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `fetched_at` datetime(6) NOT NULL,
  `name` varchar(64) DEFAULT NULL,
  `price` decimal(19,6) DEFAULT NULL,
  `symbol` varchar(16) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_at_readable` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_gold_price_snapshot_fetched_at` (`fetched_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10666 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci