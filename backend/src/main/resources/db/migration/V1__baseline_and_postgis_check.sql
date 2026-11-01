-- The database administrator provisions PostGIS. Application migrations must not require elevated extension privileges.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_extension
        WHERE extname = 'postgis'
    ) THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Required PostgreSQL extension postgis is not installed',
            HINT = 'Provision PostGIS for this database before starting the application.';
    END IF;
END
$$;
