CREATE DATABASE IF NOT EXISTS `solver-gto`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE `solver-gto`;

CREATE TABLE IF NOT EXISTS solver_job (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，求解任务唯一标识',
    job_name VARCHAR(128) NOT NULL COMMENT '任务名称，便于识别当前求解局面',
    game_variant VARCHAR(32) NOT NULL DEFAULT 'HUNL_RIVER' COMMENT '游戏变体，当前默认是单挑无限注德州河牌局面',
    street VARCHAR(16) NOT NULL DEFAULT 'RIVER' COMMENT '求解街次，当前程序仅支持RIVER',
    board_cards VARCHAR(16) NOT NULL COMMENT '公共牌，按两位一张牌连续存储，例如AsKd7h2cTc',
    pot_size DECIMAL(18,2) NOT NULL COMMENT '进入该节点时的初始底池大小',
    effective_stack DECIMAL(18,2) NOT NULL COMMENT '有效筹码量，用于描述双方可投入的最大筹码',
    hero_seat TINYINT NOT NULL DEFAULT 0 COMMENT 'Hero座位号，通常0表示OOP一方',
    first_actor_seat TINYINT NOT NULL DEFAULT 0 COMMENT '当前街第一个行动的座位号',
    iterations INT NOT NULL DEFAULT 1000 COMMENT 'CFR迭代次数，次数越高通常越接近平衡策略',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态，例如PENDING、RUNNING、DONE、FAILED',
    notes VARCHAR(255) NULL COMMENT '任务备注，用于补充说明局面来源或配置',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '任务最后更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='求解任务主表，定义一次河牌GTO求解所需的全局参数';

CREATE TABLE IF NOT EXISTS solver_player_range (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，范围记录唯一标识',
    job_id BIGINT NOT NULL COMMENT '所属任务ID，关联solver_job.id',
    seat TINYINT NOT NULL COMMENT '座位号，0和1分别表示两位玩家',
    combo_cards VARCHAR(4) NOT NULL COMMENT '两张手牌组合，例如AhKh',
    weight DECIMAL(18,6) NOT NULL DEFAULT 1.000000 COMMENT '该combo在范围中的权重或频率',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '范围记录创建时间',
    CONSTRAINT fk_player_range_job FOREIGN KEY (job_id) REFERENCES solver_job(id) ON DELETE CASCADE,
    UNIQUE KEY uk_job_seat_combo (job_id, seat, combo_cards),
    KEY idx_job_seat (job_id, seat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='玩家范围表，存储某个任务下双方的具体combo及其权重';

CREATE TABLE IF NOT EXISTS solver_tree_node (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，动作树节点记录唯一标识',
    job_id BIGINT NOT NULL COMMENT '所属任务ID，关联solver_job.id',
    node_key VARCHAR(128) NOT NULL COMMENT '当前节点唯一编码，用于串联整棵动作树',
    parent_node_key VARCHAR(128) NULL COMMENT '父节点编码，根节点为空',
    actor_seat TINYINT NULL COMMENT '当前节点轮到哪个座位行动，终局节点可为空',
    action_type VARCHAR(16) NOT NULL COMMENT '从父节点走到当前节点所采取的动作类型，例如BET、CALL、FOLD',
    amount_to_add DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '执行该动作需要额外投入的筹码量',
    terminal_type VARCHAR(16) NULL COMMENT '终局类型，例如SHOWDOWN或FOLD，非终局节点为空',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '同层兄弟节点排序，用于固定动作遍历顺序',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '节点创建时间',
    CONSTRAINT fk_tree_job FOREIGN KEY (job_id) REFERENCES solver_job(id) ON DELETE CASCADE,
    UNIQUE KEY uk_job_node_key (job_id, node_key),
    KEY idx_job_parent (job_id, parent_node_key),
    KEY idx_job_actor (job_id, actor_seat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动作树节点表，定义某个任务允许采取的博弈树结构';

CREATE TABLE IF NOT EXISTS solver_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，一次实际求解执行的唯一标识',
    job_id BIGINT NOT NULL COMMENT '所属任务ID，关联solver_job.id',
    status VARCHAR(16) NOT NULL COMMENT '执行状态，例如RUNNING、DONE、FAILED',
    iterations_completed INT NOT NULL DEFAULT 0 COMMENT '本次执行实际完成的迭代次数',
    oop_ev DECIMAL(18,6) NULL COMMENT 'OOP玩家在均衡策略下的期望收益',
    ip_ev DECIMAL(18,6) NULL COMMENT 'IP玩家在均衡策略下的期望收益',
    message VARCHAR(255) NULL COMMENT '执行结果说明或错误信息',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '本次求解开始时间',
    finished_at TIMESTAMP NULL COMMENT '本次求解结束时间',
    CONSTRAINT fk_run_job FOREIGN KEY (job_id) REFERENCES solver_job(id) ON DELETE CASCADE,
    KEY idx_run_job (job_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='求解执行记录表，记录某个任务每次运行的状态与结果摘要';

CREATE TABLE IF NOT EXISTS solver_strategy (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，策略结果记录唯一标识',
    run_id BIGINT NOT NULL COMMENT '所属执行ID，关联solver_run.id',
    job_id BIGINT NOT NULL COMMENT '所属任务ID，关联solver_job.id',
    node_key VARCHAR(128) NOT NULL COMMENT '策略所在节点编码，对应动作树中的某个决策点',
    seat TINYINT NOT NULL COMMENT '该策略所属的玩家座位号',
    combo_cards VARCHAR(4) NOT NULL COMMENT '具体手牌组合，例如AhKh',
    action_type VARCHAR(16) NOT NULL COMMENT '在该节点可采取的动作类型',
    probability DECIMAL(18,8) NOT NULL COMMENT '该combo在该节点执行该动作的概率',
    node_ev DECIMAL(18,6) NULL COMMENT '该combo在该节点按平均策略继续进行时的节点EV',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '策略结果写入时间',
    CONSTRAINT fk_strategy_run FOREIGN KEY (run_id) REFERENCES solver_run(id) ON DELETE CASCADE,
    CONSTRAINT fk_strategy_job FOREIGN KEY (job_id) REFERENCES solver_job(id) ON DELETE CASCADE,
    UNIQUE KEY uk_strategy (run_id, node_key, seat, combo_cards, action_type),
    KEY idx_strategy_job_node (job_id, node_key),
    KEY idx_strategy_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='策略输出表，保存每次求解后各节点各手牌的动作概率';

CREATE TABLE IF NOT EXISTS player (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，玩家唯一标识',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名，系统内唯一',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希，使用服务端单向哈希保存',
    display_name VARCHAR(64) NOT NULL COMMENT '玩家展示昵称，默认等于用户名',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '玩家状态，例如ACTIVE或DISABLED',
    last_login_at TIMESTAMP NULL COMMENT '最近一次登录时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '玩家注册时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '玩家资料更新时间',
    UNIQUE KEY uk_player_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='玩家账号表，保存训练系统登录玩家';

CREATE TABLE IF NOT EXISTS trainer_session (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，训练session数据库标识',
    session_id VARCHAR(64) NOT NULL COMMENT '训练session业务ID，对应前端和后端内存session',
    player_id BIGINT NOT NULL COMMENT '所属玩家ID，关联player.id',
    mode VARCHAR(16) NOT NULL DEFAULT 'CASH' COMMENT '训练模式，CASH现金桌或MTT锦标赛',
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'session状态，RUNNING、HAND_DONE或COMPLETED',
    total_hands INT NOT NULL COMMENT '本次session计划训练手数',
    hands_played INT NOT NULL DEFAULT 0 COMMENT '已完成手牌数',
    actions_taken INT NOT NULL DEFAULT 0 COMMENT 'Hero已执行行动次数',
    raw_gto_score DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '当前累计GTO得分，按每条街4分累计',
    max_gto_score INT NOT NULL DEFAULT 0 COMMENT '当前累计可得满分，等于已完成决策街数乘以4',
    gto_score_percent INT NOT NULL DEFAULT 0 COMMENT '当前GTO百分比分数，raw_gto_score除以max_gto_score后取整',
    config_json JSON NULL COMMENT '本次训练配置JSON快照',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'session开始时间',
    finished_at TIMESTAMP NULL COMMENT 'session完成时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'session最后更新时间',
    CONSTRAINT fk_trainer_session_player FOREIGN KEY (player_id) REFERENCES player(id) ON DELETE CASCADE,
    UNIQUE KEY uk_trainer_session_id (session_id),
    KEY idx_trainer_session_player_mode (player_id, mode, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练session表，以玩家、模式和session维度管理训练';

CREATE TABLE IF NOT EXISTS trainer_hand_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，单手训练历史记录标识',
    session_id VARCHAR(64) NOT NULL COMMENT '所属训练session业务ID，关联trainer_session.session_id',
    player_id BIGINT NOT NULL COMMENT '所属玩家ID，关联player.id',
    mode VARCHAR(16) NOT NULL DEFAULT 'CASH' COMMENT '训练模式，CASH或MTT',
    hand_number INT NOT NULL COMMENT '该手牌在session中的序号，从1开始',
    completed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '该手牌是否已经完成四条街训练',
    hero_position VARCHAR(8) NOT NULL COMMENT 'Hero位置，例如BTN、BB',
    gto_opponent_position VARCHAR(8) NOT NULL COMMENT 'GTO对手位置，例如BB、BTN',
    hero_hand VARCHAR(4) NOT NULL COMMENT 'Hero两张手牌，例如AsKd',
    opponent_hand VARCHAR(4) NULL COMMENT 'GTO对手两张手牌，例如KhQh，未摊牌时也会按训练样本保存',
    board_cards VARCHAR(16) NULL COMMENT '完整公共牌，按两位一张牌连续存储',
    scenario_type VARCHAR(32) NOT NULL COMMENT '训练场景类型，例如面对3bet或对抗挤压',
    hand_score DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '该手当前GTO百分比分数，按已完成决策街数折算',
    outcome_label VARCHAR(64) NULL COMMENT '该手牌最终结果说明，例如Hero赢下摊牌、Hero弃牌告负或双方平分底池',
    summary VARCHAR(255) NULL COMMENT '该手牌摘要，例如已完成和本手得分',
    behavior_analysis VARCHAR(512) NULL COMMENT '该手牌行为分析，说明错误点或策略特点',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    CONSTRAINT fk_hand_history_session FOREIGN KEY (session_id) REFERENCES trainer_session(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_hand_history_player FOREIGN KEY (player_id) REFERENCES player(id) ON DELETE CASCADE,
    UNIQUE KEY uk_hand_history_session_hand (session_id, hand_number),
    KEY idx_hand_history_player_mode (player_id, mode, hand_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练单手历史表，按session保存每手牌摘要和行为分析';

CREATE TABLE IF NOT EXISTS trainer_operation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，训练操作记录标识',
    session_id VARCHAR(64) NOT NULL COMMENT '所属训练session业务ID，关联trainer_session.session_id',
    player_id BIGINT NOT NULL COMMENT '所属玩家ID，关联player.id',
    mode VARCHAR(16) NOT NULL DEFAULT 'CASH' COMMENT '训练模式，CASH或MTT',
    hand_number INT NOT NULL COMMENT '所属手牌序号',
    sequence_no INT NOT NULL COMMENT '该手牌内操作顺序，从1递增',
    street VARCHAR(16) NOT NULL COMMENT '操作所在街次，翻前、翻牌、转牌或河牌',
    actor VARCHAR(16) NOT NULL COMMENT '操作主体，HERO、GTO、BOARD或SYSTEM',
    player_position VARCHAR(8) NULL COMMENT '操作主体位置，例如BTN、BB；发牌类操作可为空',
    action_text VARCHAR(128) NOT NULL COMMENT '操作动作文本，例如Fold、bets 2.48、发翻牌',
    detail VARCHAR(255) NULL COMMENT '操作详情，展示给前端的完整描述',
    automatic TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否系统自动操作，1表示GTO或发牌自动动作',
    score_category VARCHAR(32) NULL COMMENT 'Hero动作评分档位，例如最佳行动、错误行动',
    delta_score INT NOT NULL DEFAULT 0 COMMENT '该操作分值，最佳4、正确3、存疑2、错误1、巨大错误0',
    best_action VARCHAR(64) NULL COMMENT '该节点推荐主动作',
    analysis VARCHAR(512) NULL COMMENT '该操作行为分析和复盘说明',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作记录创建时间',
    CONSTRAINT fk_trainer_operation_session FOREIGN KEY (session_id) REFERENCES trainer_session(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_trainer_operation_player FOREIGN KEY (player_id) REFERENCES player(id) ON DELETE CASCADE,
    UNIQUE KEY uk_trainer_operation_seq (session_id, hand_number, sequence_no),
    KEY idx_trainer_operation_player_mode (player_id, mode, hand_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练操作流水表，记录每手牌内Hero、GTO和发牌动作';

ALTER TABLE trainer_session
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'session状态，RUNNING、HAND_DONE或COMPLETED';

ALTER TABLE trainer_hand_history
    ADD COLUMN IF NOT EXISTS opponent_hand VARCHAR(4) NULL COMMENT 'GTO对手两张手牌，例如KhQh，未摊牌时也会按训练样本保存' AFTER hero_hand,
    ADD COLUMN IF NOT EXISTS outcome_label VARCHAR(64) NULL COMMENT '该手牌最终结果说明，例如Hero赢下摊牌、Hero弃牌告负或双方平分底池' AFTER hand_score;
