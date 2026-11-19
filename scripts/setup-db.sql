-- One-time local database setup for SaathChalo. Safe to run again.
-- Run from the repo root:   sudo -u postgres psql < scripts/setup-db.sql
SELECT 'CREATE ROLE saathchalo LOGIN PASSWORD ''saathchalo'''
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'saathchalo')\gexec

SELECT 'CREATE DATABASE saathchalo OWNER saathchalo'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'saathchalo')\gexec

SELECT 'CREATE DATABASE saathchalo_test OWNER saathchalo'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'saathchalo_test')\gexec

\c saathchalo
CREATE EXTENSION IF NOT EXISTS postgis;
\c saathchalo_test
CREATE EXTENSION IF NOT EXISTS postgis;
