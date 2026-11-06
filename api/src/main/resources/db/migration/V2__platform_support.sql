-- Tenant / soft-delete restriction helpers read by the entity @SQLRestriction clauses.
CREATE OR REPLACE FUNCTION ep_company() RETURNS uuid LANGUAGE sql STABLE AS
$$ SELECT nullif(current_setting('ep.company', true), '')::uuid $$;

CREATE OR REPLACE FUNCTION ep_bypass() RETURNS boolean LANGUAGE sql STABLE AS
$$ SELECT coalesce(current_setting('ep.bypass', true), '') = 'on' $$;

-- ShedLock: one node runs each scheduled job.
CREATE TABLE shedlock (
    name       varchar(64)  NOT NULL PRIMARY KEY,
    lock_until timestamp    NOT NULL,
    locked_at  timestamp    NOT NULL,
    locked_by  varchar(255) NOT NULL
);
