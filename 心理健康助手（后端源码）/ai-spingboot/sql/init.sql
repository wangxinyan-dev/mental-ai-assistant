CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20),
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    avatar VARCHAR(255),
    gender INT,
    birthday DATE,
    user_type INT DEFAULT 1 COMMENT '1:普通用户 2:管理员',
    status INT DEFAULT 1 COMMENT '0:禁用 1:正常',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consultation_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_title VARCHAR(200),
    started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_emotion_analysis TEXT,
    last_emotion_updated_at DATETIME,
    INDEX idx_user_started (user_id, started_at),
    INDEX idx_started_at (started_at),
    FOREIGN KEY (user_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS consultation_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    sender_type INT NOT NULL COMMENT '1:用户 2:AI助手',
    message_type INT DEFAULT 1 COMMENT '1:文本',
    content TEXT NOT NULL,
    emotion_tag VARCHAR(50),
    ai_model VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_created (session_id, created_at),
    FOREIGN KEY (session_id) REFERENCES consultation_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_article (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT,
    summary VARCHAR(1000),
    category_id BIGINT,
    cover_image VARCHAR(500),
    tags VARCHAR(500),
    author_name VARCHAR(50),
    author_id BIGINT,
    read_count INT DEFAULT 0,
    status INT DEFAULT 0 COMMENT '0:草稿 1:已发布 2:已下线',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_category_id (category_id),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS emotion_diary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    diary_date DATE,
    mood_score INT COMMENT '1-10',
    dominant_emotion VARCHAR(50),
    emotion_triggers VARCHAR(1000),
    diary_content TEXT,
    sleep_quality INT COMMENT '1-5',
    stress_level INT COMMENT '1-5',
    ai_emotion_analysis TEXT COMMENT 'JSON',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_date (user_id, diary_date),
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 默认分类
INSERT IGNORE INTO knowledge_category (category_name, sort_order) VALUES
('情绪管理', 1), ('压力应对', 2), ('冥想正念', 3), ('人际关系', 4),
('睡眠改善', 5), ('自我成长', 6), ('心理健康知识', 7), ('危机干预', 8);
