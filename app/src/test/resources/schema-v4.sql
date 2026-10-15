CREATE TABLE account (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    currency TEXT NOT NULL,
    kind TEXT NOT NULL,
    archived INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE category (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    color TEXT NOT NULL,
    archived INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE member (
    id TEXT NOT NULL PRIMARY KEY,
    display_name TEXT NOT NULL,
    device_id TEXT NOT NULL
);

CREATE TABLE event (
    id TEXT NOT NULL PRIMARY KEY,
    device_id TEXT NOT NULL,
    seq INTEGER NOT NULL,
    occurred_at INTEGER NOT NULL,
    type TEXT NOT NULL,
    payload TEXT NOT NULL,
    UNIQUE(device_id, seq)
);

CREATE INDEX event_order ON event(occurred_at, device_id, seq);

CREATE TABLE tx (
    id TEXT NOT NULL PRIMARY KEY,
    type TEXT NOT NULL,
    amount TEXT NOT NULL,
    currency TEXT NOT NULL,
    account_id TEXT NOT NULL,
    to_account_id TEXT,
    category_id TEXT,
    date TEXT NOT NULL,
    note TEXT NOT NULL,
    merchant TEXT NOT NULL,
    paid_by TEXT NOT NULL,
    source TEXT NOT NULL,
    edited_at INTEGER NOT NULL
);

CREATE INDEX tx_date ON tx(date DESC);

CREATE TABLE budget (
    category_id TEXT NOT NULL,
    month TEXT NOT NULL,
    amount TEXT NOT NULL,
    currency TEXT NOT NULL,
    PRIMARY KEY (category_id, month)
);

CREATE TABLE recurring_rule (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    amount TEXT NOT NULL,
    currency TEXT NOT NULL,
    account_id TEXT NOT NULL,
    category_id TEXT NOT NULL,
    cadence TEXT NOT NULL,
    first_due TEXT NOT NULL,
    archived INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE bill_skip (
    rule_id TEXT NOT NULL,
    due_period TEXT NOT NULL,
    PRIMARY KEY (rule_id, due_period)
);

CREATE TABLE goal (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    target TEXT NOT NULL,
    monthly TEXT NOT NULL,
    currency TEXT NOT NULL,
    archived INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE goal_allocation (
    id TEXT NOT NULL PRIMARY KEY,
    goal_id TEXT NOT NULL,
    amount TEXT NOT NULL,
    currency TEXT NOT NULL,
    date TEXT NOT NULL
);

