#!/bin/bash

set -e
set -u

function create_user_and_database() {
	local DB="$1"
	local SCHEMA="$2"
	local USER="$3"
	local PASS="$4"

	if psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -tAc "SELECT 1 FROM pg_roles WHERE rolname='$USER'" | grep -q "1"; then
		echo "User $USER already exists"
	else
		if ! psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -tAc "CREATE USER $USER WITH PASSWORD '$PASS';" | grep -q "CREATE ROLE"; then
			echo "Failed to create user $USER"
			exit 1
		fi
	fi

	if psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -tAc "SELECT 1 FROM pg_database WHERE datname='$DB'" | grep -q "1"; then
		echo "Database '$DB' already exists"
	else
		if ! psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -c "CREATE DATABASE $DB;" | grep -q "CREATE DATABASE"; then
			echo "Failed creating '$DB'"
			exit 1
		fi
	fi

	if ! psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$DB" -c "CREATE SCHEMA IF NOT EXISTS $SCHEMA AUTHORIZATION $USER" | grep -q "CREATE SCHEMA"; then
		echo "Failed to create schema '$SCHEMA'"
		exit 1
	fi

	if ! psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -tAc "GRANT ALL PRIVILEGES ON DATABASE $DB TO $USER;" | grep -q "GRANT"; then
		echo "Failed to grant privileges to user $USER on database $DB"
		exit 1
	fi
}

POSTGRES_MULTIPLE_DATABASES=$(compgen -e | grep '^POSTGRES_MULTIPLE_DATABASE_' | sort)
for VARIABLE in $POSTGRES_MULTIPLE_DATABASES; do
	VALUE="${!VARIABLE}"

	if [ -z "$VALUE" ]; then
		break
	fi

	echo "Multiple DB creation requested: $VALUE"
	IFS='/' read -r -a ARRAY <<< "$VALUE"
	create_user_and_database "${ARRAY[@]}"
done
