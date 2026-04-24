CREATE TABLE kg_fault_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE kg_repair_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    steps TEXT,
    reference_url VARCHAR(512),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE kg_fault_repair_edge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fault_id BIGINT NOT NULL,
    repair_id BIGINT NOT NULL,
    confidence DOUBLE NOT NULL DEFAULT 0.5,
    scenario VARCHAR(255),
    CONSTRAINT fk_kg_edge_fault FOREIGN KEY (fault_id) REFERENCES kg_fault_node(id),
    CONSTRAINT fk_kg_edge_repair FOREIGN KEY (repair_id) REFERENCES kg_repair_node(id)
);

CREATE TABLE fault_case_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id BIGINT NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    feature_vector_json TEXT,
    predicted_fault_code VARCHAR(64),
    user_feedback_code VARCHAR(64),
    notes TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
