create table match_rule
(
    id                bigserial primary key,
    kind              text not null,
    is_transaction_id text null,
    is_description    text null,
    is_institution    text null,
    is_category       text null
);

create table initial_balance
(
    id     bigserial primary key,
    "date" date    not null,
    amount decimal not null
);

create table plaid_institution
(
    id             bigserial primary key,
    institution_id text not null,
    name           text not null
);

create table plaid_item
(
    id           bigserial primary key,
    item_id      text   not null,
    access_token text   not null,
    institution  bigint not null references plaid_institution (id)
);
