-- 开始数据库初始化
SELECT 'Starting database initialization...' AS 'DEBUG';

-- 为主服务创建数据库
CREATE DATABASE IF NOT EXISTS PROPERTY_LEASE_MAIN CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SELECT 'Database PROPERTY_LEASE_MAIN created or already exists' AS 'DEBUG';

-- 为计费服务创建数据库
CREATE DATABASE IF NOT EXISTS PROPERTY_LEASE_BILLING CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SELECT 'Database PROPERTY_LEASE_BILLING created or already exists' AS 'DEBUG';

-- 为设备服务创建数据库
CREATE DATABASE IF NOT EXISTS PROPERTY_LEASE_DEVICE CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SELECT 'Database PROPERTY_LEASE_DEVICE created or already exists' AS 'DEBUG';

-- 列出所有数据库验证
SHOW DATABASES;

SELECT 'Database initialization completed successfully!' AS 'DEBUG';