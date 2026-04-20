-- 初始化库存数据（INSERT IGNORE 保证重启不重复插入）
INSERT IGNORE INTO `inventory` (`product_id`, `total_stock`, `locked_stock`, `available_stock`, `version`, `updated_at`) VALUES
(1001, 100, 0, 100, 0, NOW(3)),
(1002, 50,  0, 50,  0, NOW(3)),
(1003, 200, 0, 200, 0, NOW(3));
