CREATE
DATABASE IF NOT EXISTS bella_assistant CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE
bella_assistant;

-- Assistant 表
CREATE TABLE IF NOT EXISTS assistant
(
    id
    VARCHAR
(
    64
) NOT NULL PRIMARY KEY DEFAULT '',
    model VARCHAR
(
    128
) NOT NULL DEFAULT '' COMMENT 'assistant使用模型',
    object VARCHAR
(
    1024
) NOT NULL DEFAULT '',
    name VARCHAR
(
    128
) NOT NULL DEFAULT '' COMMENT 'assistant名称',
    description VARCHAR
(
    256
) NOT NULL DEFAULT '' COMMENT 'assistant描述',
    instructions TEXT NULL,
    temperature FLOAT NOT NULL DEFAULT 0.01 COMMENT 'temperature of the run',
    top_p FLOAT NOT NULL DEFAULT 1 COMMENT 'top_p of the run',
    response_format VARCHAR
(
    50
) DEFAULT 'auto' NULL COMMENT '输出格式',
    user VARCHAR
(
    50
) DEFAULT '' NULL COMMENT 'user',
    reasoning_effort VARCHAR
(
    100
) NOT NULL DEFAULT '' COMMENT '推理参数配置',
    profile TINYINT
(
    1
) NOT NULL DEFAULT 0,
    owner VARCHAR
(
    64
) NOT NULL DEFAULT '' COMMENT '创建人',
    metadata VARCHAR
(
    4096
) NOT NULL DEFAULT '' COMMENT '元信息',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='assistant基础信息表';

CREATE INDEX index_assistant_owner ON assistant (owner);

-- Assistant File Relation 表
CREATE TABLE IF NOT EXISTS assistant_file_relation
(
    id
    INT
    AUTO_INCREMENT
    PRIMARY
    KEY,
    file_id
    VARCHAR
(
    64
) NOT NULL DEFAULT '',
    assistant_id VARCHAR
(
    64
) NOT NULL DEFAULT '',
    object VARCHAR
(
    64
) NOT NULL DEFAULT '',
    tool_name VARCHAR
(
    100
) NOT NULL DEFAULT '' COMMENT '工具名称',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT unique_index_name UNIQUE
(
    file_id,
    assistant_id,
    tool_name
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX index_assistant_id ON assistant_file_relation (assistant_id);
CREATE INDEX index_file_id ON assistant_file_relation (file_id);
CREATE INDEX index_tool_name ON assistant_file_relation (tool_name);

-- Thread 表
CREATE TABLE IF NOT EXISTS thread
(
    id
    VARCHAR
(
    64
) NOT NULL PRIMARY KEY DEFAULT '',
    object VARCHAR
(
    128
) NOT NULL DEFAULT '',
    owner VARCHAR
(
    64
) NOT NULL DEFAULT '',
    user VARCHAR
(
    100
) NULL,
    environment VARCHAR
(
    1000
) NOT NULL DEFAULT '{}' COMMENT '环境信息',
    metadata VARCHAR
(
    2048
) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX index_thread_owner ON thread (owner);

-- Thread File Relation 表
CREATE TABLE IF NOT EXISTS thread_file_relation
(
    id
    INT
    AUTO_INCREMENT
    PRIMARY
    KEY,
    file_id
    VARCHAR
(
    256
) NOT NULL DEFAULT '',
    thread_id VARCHAR
(
    32
) NOT NULL DEFAULT '',
    object VARCHAR
(
    64
) NOT NULL DEFAULT '',
    tool_name VARCHAR
(
    100
) NOT NULL DEFAULT '' COMMENT '工具名称',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT idx_unique_file_tool_thread UNIQUE
(
    file_id,
    thread_id,
    tool_name
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX index_file_id ON thread_file_relation (file_id);
CREATE INDEX index_thread_id ON thread_file_relation (thread_id);
CREATE INDEX index_tool_name ON thread_file_relation (tool_name);

-- Message 表
CREATE TABLE IF NOT EXISTS message
(
    id
    VARCHAR
(
    64
) NOT NULL PRIMARY KEY DEFAULT '',
    role VARCHAR
(
    16
) NOT NULL DEFAULT '',
    thread_id VARCHAR
(
    64
) NOT NULL DEFAULT '',
    object VARCHAR
(
    64
) NOT NULL DEFAULT '',
    status VARCHAR
(
    64
) NOT NULL DEFAULT 'completed',
    content MEDIUMTEXT NULL,
    reasoning_content TEXT NULL,
    attachments VARCHAR
(
    4096
) DEFAULT '[]' NULL COMMENT '本轮消息可用的额外的文件和工具',
    file_ids VARCHAR
(
    2048
) NOT NULL DEFAULT '',
    metadata VARCHAR
(
    2048
) NOT NULL DEFAULT '',
    assistant_id VARCHAR
(
    64
) NOT NULL DEFAULT '',
    run_id VARCHAR
(
    64
) NOT NULL DEFAULT '',
    name VARCHAR
(
    100
) NOT NULL DEFAULT '' COMMENT '名称',
    message_type VARCHAR
(
    64
) NOT NULL DEFAULT 'mixed' COMMENT '消息的类型',
    summarized_by VARCHAR
(
    64
) NOT NULL DEFAULT '' COMMENT '如果消息被压缩，此处标记压缩消息的message_id',
    message_status VARCHAR
(
    64
) NOT NULL DEFAULT 'original' COMMENT '消息状态',
    created_at DATETIME
(
    3
) NOT NULL DEFAULT CURRENT_TIMESTAMP
(
    3
),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_created_at ON message (created_at);
CREATE INDEX idx_msg_assistant_id ON message (assistant_id);
CREATE INDEX idx_msg_run_id ON message (run_id);
CREATE INDEX idx_msg_thread_id ON message (thread_id);

-- Run 表
CREATE TABLE IF NOT EXISTS run
(
    id
    VARCHAR
(
    64
) NOT NULL PRIMARY KEY DEFAULT '',
    object VARCHAR
(
    64
) NOT NULL DEFAULT '',
    assistant_id VARCHAR
(
    64
) NOT NULL DEFAULT '',
    thread_id VARCHAR
(
    64
) NOT NULL DEFAULT '',
    status VARCHAR
(
    64
) NOT NULL DEFAULT '',
    model VARCHAR
(
    64
) NOT NULL DEFAULT '',
    instructions TEXT NULL,
    temperature FLOAT NOT NULL DEFAULT 0.01 COMMENT 'temperature of the run',
    top_p FLOAT NOT NULL DEFAULT 1 COMMENT 'top_p of the run',
    max_prompt_tokens INT NOT NULL DEFAULT 0 COMMENT '最大输入Token长度',
    max_completion_tokens INT NOT NULL DEFAULT 0 COMMENT '最大生成Token长度',
    truncation_strategy VARCHAR
(
    100
) NOT NULL DEFAULT '' COMMENT '上下文策略',
    tool_choice VARCHAR
(
    3096
) NOT NULL DEFAULT 'auto',
    parallel_tool_calls TINYINT
(
    1
) NOT NULL DEFAULT 1 COMMENT '并行工具调用',
    response_format VARCHAR
(
    50
) NOT NULL DEFAULT 'auto' COMMENT '输出格式',
    user VARCHAR
(
    100
) NULL,
    file_ids VARCHAR
(
    2048
) NOT NULL DEFAULT '',
    metadata VARCHAR
(
    2048
) NOT NULL DEFAULT '',
    last_error VARCHAR
(
    4096
) NOT NULL DEFAULT '',
    required_action TEXT NULL,
    incomplete_details VARCHAR
(
    2048
) NOT NULL DEFAULT '' COMMENT '未完成的原因',
    `usage` VARCHAR
(
    256
) NOT NULL DEFAULT '',
    started_at DATETIME NOT NULL DEFAULT '1970-01-01 08:00:00',
    completed_at DATETIME NOT NULL DEFAULT '1970-01-01 08:00:00',
    cancelled_at DATETIME NOT NULL DEFAULT '1970-01-01 08:00:00',
    expires_at DATETIME NOT NULL DEFAULT '1970-01-01 09:00:00',
    failed_at DATETIME NOT NULL DEFAULT '1970-01-01 08:00:00',
    authorization_header VARCHAR
(
    128
) NOT NULL DEFAULT '' COMMENT '模型调用身份认证信息',
    task_id VARCHAR
(
    64
) NOT NULL DEFAULT '' COMMENT 'celery中对应的任务id',
    save_message TINYINT
(
    1
) NOT NULL DEFAULT 1 COMMENT '是否保存本轮生成 message',
    additional_message_ids VARCHAR
(
    200
) NOT NULL DEFAULT '' COMMENT '本轮 run 的 additional message ids',
    stream VARCHAR
(
    100
) NOT NULL DEFAULT '1',
    reasoning_time INT DEFAULT 0 NULL COMMENT '模型推理耗时，以秒为单位',
    reasoning_effort VARCHAR
(
    100
) NOT NULL DEFAULT '""',
    created_at DATETIME
(
    3
) NOT NULL DEFAULT CURRENT_TIMESTAMP
(
    3
),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_created_at ON run (created_at);
CREATE INDEX idx_run_assistant_id ON run (assistant_id);
CREATE INDEX idx_run_thread_id ON run (thread_id);
CREATE INDEX idx_status ON run (status);

-- Run Step 表
CREATE TABLE IF NOT EXISTS run_step
(
    id
    VARCHAR
(
    64
) NOT NULL PRIMARY KEY DEFAULT '',
    status VARCHAR
(
    16
) NOT NULL DEFAULT '',
    type VARCHAR
(
    16
) NOT NULL DEFAULT '',
    assistant_Id VARCHAR
(
    64
) NOT NULL DEFAULT '',
    thread_id VARCHAR
(
    64
) NOT NULL DEFAULT '',
    run_id VARCHAR
(
    64
) NOT NULL DEFAULT '',
    object VARCHAR
(
    16
) NOT NULL DEFAULT '',
    metadata VARCHAR
(
    2048
) NOT NULL DEFAULT '',
    last_error VARCHAR
(
    4096
) NOT NULL DEFAULT '',
    step_details LONGTEXT NULL,
    completed_at DATETIME NOT NULL DEFAULT '1970-01-01 08:00:00',
    cancelled_at DATETIME NOT NULL DEFAULT '1970-01-01 08:00:00',
    expires_at DATETIME NOT NULL DEFAULT '1970-01-01 08:00:00',
    failed_at DATETIME NOT NULL DEFAULT '1970-01-01 08:00:00',
    message_id VARCHAR
(
    64
) NOT NULL DEFAULT '',
    `usage` VARCHAR
(
    1024
) NOT NULL DEFAULT '',
    llm_input LONGTEXT NULL,
    reasoning_time INT DEFAULT 0 NULL COMMENT '模型推理耗时，以秒为单位',
    reasoning_content LONGTEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_step_assistant_id ON run_step (assistant_Id);
CREATE INDEX idx_step_run_id ON run_step (run_id);
CREATE INDEX idx_step_thread_id ON run_step (thread_id);

-- Assistant Tool 表
CREATE TABLE IF NOT EXISTS assistant_tool
(
    id
    INT
    AUTO_INCREMENT
    PRIMARY
    KEY,
    assistant_id
    VARCHAR
(
    64
) NOT NULL DEFAULT '' COMMENT 'tool所关联的对象（assistant_id or run_id)',
    tool TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX index_assistant_id ON assistant_tool (assistant_id);

-- Run Tool 表
CREATE TABLE IF NOT EXISTS run_tool
(
    id
    INT
    AUTO_INCREMENT
    PRIMARY
    KEY,
    run_id
    VARCHAR
(
    64
) NOT NULL DEFAULT '',
    tool TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX index_run_id ON run_tool (run_id);

-- Response ID Mapping 表
CREATE TABLE IF NOT EXISTS response_id_mapping
(
    response_id
    VARCHAR
(
    64
) NOT NULL PRIMARY KEY DEFAULT '' COMMENT 'Response唯一标识符',
    thread_id VARCHAR
(
    64
) NOT NULL DEFAULT '' COMMENT '会话id',
    run_id VARCHAR
(
    64
) NOT NULL DEFAULT '' COMMENT 'run_id',
    previous_response_id VARCHAR
(
    64
) NOT NULL DEFAULT '' COMMENT '上次请求的responseid',
    user VARCHAR
(
    64
) NOT NULL DEFAULT '0' COMMENT '用户id',
    status VARCHAR
(
    16
) NOT NULL DEFAULT 'active' COMMENT '状态：active 正常的 deleted 已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ID Sequence 表
CREATE TABLE IF NOT EXISTS id_sequence
(
    id INT AUTO_INCREMENT PRIMARY KEY,
    prefix VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'ID前缀',
    current_value BIGINT NOT NULL DEFAULT 0 COMMENT '当前值',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_prefix (prefix)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ID序列生成表';

