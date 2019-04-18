# Models ETL

Uses [flyway](https://github.com/flyway/flyway) for db migrations.

## Migrations

Migrations are run automatically when the service starts.

## Make commands

Start the ETL

```bash
make start
```

Run a sbt console

```bash
make sbt
```

Start/Stop/Destroy containers

```bash
make start-containers
make test/start-containers
make stop-containers
make destroy-containers
```

## Data base structure

Each type of messages is put in a separate table.

The fields are the same for all tables:

- id
- created_at
- updated_at
- deleted_at
- data (jsonb)

The primary key is (id, updated_at).

For more info on how to work with jsonb, see :

- http://www.postgresqltutorial.com/postgresql-json/
- https://www.postgresql.org/docs/current/functions-json.html

Basic examples:

```sql
-- select the most recent version of each model (`id desc` is to use the primary key)
select distinct on (id) * from models order by id desc, updated_at desc;
-- average duration on all models (including previous versions of the same models)
select avg((data ->> 'duration')::int) from models;
-- average duration on most recent version of all the models
select avg((data ->> 'duration')::int) from (select distinct on (id) * from models order by id desc, updated_at desc) _;
-- or
select avg((data ->> 'duration')::int) from models_latest;
```

### Views

Each table has a view named `TABLENAME_latest` that is `select distinct on (id) * from TABLENAME order by id desc, updated_at desc;`
