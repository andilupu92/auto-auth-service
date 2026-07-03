-- Flyway Migration: V4__add_apple_user_id_to_users_table.sql

ALTER TABLE users
ADD COLUMN apple_user_id CHARACTER VARYING(255);

ALTER TABLE users
ADD CONSTRAINT uq_users_apple_user_id UNIQUE (apple_user_id);